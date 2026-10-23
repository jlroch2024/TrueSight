"""Rebuilds docs/stories/ from a Jira export, so every agent can read the stories without Jira.

Export from Jira: Filters > All issues, project TrueSight, Export > Export CSV (all fields). Then, from the repository
root:

    python docs/stories/from_jira.py path/to/Jira.csv

Every TS-*.md file and README.md here is rewritten from the export. Issues from other projects are left out.
"""

import csv
import re
import sys
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROJECT = 'TS'


def slug(text):
    """A file name from a title, as the story branches are named: "Ask in TrueSight's Own Window" Ask-in-TrueSight-s-Own-Window."""
    return re.sub(r'[^A-Za-z0-9]+', '-', text).strip('-')


def table_row(line):
    """A Jira table row ("||a||b||" or "|a|b|") as a Markdown one, and whether it was a header."""
    header = line.startswith('||')
    cells = [cell.strip() for cell in re.split(r'\|\|?', line.strip())[1:-1]]
    return '| ' + ' | '.join(cells) + ' |', header, len(cells)


def inline(text):
    """Jira's inline markup as Markdown: bold, italics, links and escapes. Code spans are already set aside."""
    text = re.sub(r'\[([^\]|]+)\|([^\]]+)\]', r'[\1](\2)', text)
    text = re.sub(r'\[(https?://[^\]]+)\]', r'<\1>', text)
    text = re.sub(r'(?<![\w*\\])\*(?=\S)([^*\n]*?\S)\*(?![\w*])', r'**\1**', text)
    text = re.sub(r'(?<![\w\\])_(?=\S)([^_\n]*?\S)_(?!\w)', r'*\1*', text)
    return re.sub(r'\\([\[\]{}*_|-])', r'\1', text)


def markdown(wiki):
    """Converts a Jira description (wiki markup) to Markdown."""
    wiki = wiki.replace('\r\n', '\n').replace('\r', '\n')
    kept = []

    def keep(text):
        kept.append(text)
        return f'\x00{len(kept) - 1}\x00'

    # Blocks first, so nothing inside them is read as markup.
    def block(match):
        kind, language, body = match.group(1), match.group(2) or '', match.group(3).strip('\n')
        if kind == 'quote':
            return '\n'.join('> ' + line for line in body.split('\n'))
        return keep(f'```{language}\n{body}\n```')

    wiki = re.sub(r'\{(code|noformat|quote)(?::([a-z]+))?[^}]*\}(.*?)\{\1\}', block, wiki, flags=re.S)
    # Code spans: {{...}}, whose text may itself hold braces ({{{ "a": 1 }}}).
    wiki = re.sub(r'\{\{(.+?)\}\}(?!\})', lambda m: keep('`' + m.group(1) + '`'), wiki)

    out = []
    table = None
    for line in wiki.split('\n'):
        if line.startswith('|'):
            row, header, width = table_row(line)
            if table is None:
                if out and out[-1] != '':
                    out.append('')
                out.append(inline(row))
                if not header:
                    # Markdown needs a header row: an empty one keeps a plain table's rows as they are.
                    out.insert(-1, '| ' + ' | '.join([' '] * width) + ' |')
                out.append('|' + '---|' * width)
                table = width
            else:
                out.append(inline(row))
            continue
        table = None
        heading = re.match(r'^h([1-6])\.\s+(.*)$', line)
        alone = re.fullmatch(r'\*([^*]+)\*\s*', line)
        listed = re.match(r'^([*#]+)\s+(.*)$', line)
        if heading:
            out.append('#' * max(2, int(heading.group(1)) - 1) + ' ' + inline(heading.group(2)))
        elif alone:
            out.append('## ' + inline(alone.group(1)))
        elif listed:
            marks = listed.group(1)
            indent = '   ' * (len(marks) - 1)
            out.append(indent + ('1. ' if marks[-1] == '#' else '- ') + inline(listed.group(2)))
        elif re.fullmatch(r'-{4,}\s*', line):
            out.append('---')
        else:
            out.append(inline(line))
    text = '\n'.join(out)
    text = re.sub(r'\x00(\d+)\x00', lambda m: kept[int(m.group(1))], text)
    # A heading needs a blank line before it to be read as one.
    text = re.sub(r'([^\n])\n(#{2,} )', r'\1\n\n\2', text)
    return re.sub(r'\n{3,}', '\n\n', text).strip() + '\n'


def read(path):
    with open(path, encoding='utf-8-sig', newline='') as file:
        rows = list(csv.reader(file))
    header, rows = rows[0], rows[1:]

    def column(name):
        return [i for i, title in enumerate(header) if title == name]

    def first(row, name):
        return next((row[i].strip() for i in column(name) if row[i].strip()), '')

    def every(row, name):
        return [row[i].strip() for i in column(name) if row[i].strip()]

    issues = []
    for row in rows:
        if first(row, 'Project key') != PROJECT:
            continue
        points = first(row, 'Custom field (Story Points)')
        issues.append(
            {
                'key': first(row, 'Issue key'),
                'number': int(first(row, 'Issue key').split('-')[1]),
                'title': first(row, 'Summary'),
                'type': first(row, 'Issue Type'),
                'status': first(row, 'Status'),
                'epic': first(row, 'Parent key'),
                'epic_title': first(row, 'Parent summary'),
                'sprints': every(row, 'Sprint'),
                'labels': every(row, 'Labels'),
                'points': points[:-2] if points.endswith('.0') else points,
                'assignee': first(row, 'Assignee'),
                'blocks': every(row, 'Outward issue link (Blocks)'),
                'blocked_by': every(row, 'Inward issue link (Blocks)'),
                'description': first(row, 'Description'),
            }
        )
    return sorted(issues, key=lambda issue: issue['number'])


def sprint_of(issue):
    """The sprint an issue belongs to: its Jira sprint, else its sprint-N label, else the backlog."""
    if issue['sprints']:
        return issue['sprints'][-1].replace(f'{PROJECT} ', '')
    for label in issue['labels']:
        if re.fullmatch(r'sprint-\d+', label):
            return 'Sprint ' + label.split('-')[1]
    return 'Backlog'


def file_name(issue):
    return f"{issue['key']}-{slug(issue['title'])}.md"


def write_issue(issue, by_key, exported):
    def named(key):
        other = by_key.get(key)
        return f'[{key} {other["title"]}]({file_name(other)})' if other else key

    facts = [('Type', issue['type']), ('Status', f'{issue["status"]} (in Jira on {exported})')]
    if issue['epic']:
        facts.append(('Epic', named(issue['epic'])))
    if issue['type'] != 'Epic':
        facts.append(('Sprint', sprint_of(issue)))
    if issue['points']:
        facts.append(('Points', issue['points']))
    if issue['labels']:
        facts.append(('Labels', ', '.join(issue['labels'])))
    if issue['assignee']:
        facts.append(('Assignee', issue['assignee']))
    if issue['blocks']:
        facts.append(('Blocks', ', '.join(named(key) for key in issue['blocks'])))
    if issue['blocked_by']:
        facts.append(('Blocked By', ', '.join(named(key) for key in issue['blocked_by'])))
    if issue['type'] == 'Epic':
        children = [other for other in by_key.values() if other['epic'] == issue['key']]
        if children:
            facts.append(('Issues', ', '.join(named(other['key']) for other in children)))

    lines = [f"# {issue['key']} {issue['title']}", '', '| | |', '|---|---|']
    lines += [f'| {name} | {value} |' for name, value in facts]
    lines += ['', markdown(issue['description']) if issue['description'] else '*No description in Jira.*\n']
    (HERE / file_name(issue)).write_text('\n'.join(lines), encoding='utf-8', newline='\n')


def write_index(issues, exported):
    lines = [
        '# Stories',
        '',
        f'Every TrueSight issue in Jira, one file each, exported on {exported}. Jira is the source: these files are a',
        'copy for agents and people who cannot open Jira. Each file holds the issue as Jira has it: the User Story, What',
        'We Agreed, Acceptance Criteria and Technical Notes, with its epic, sprint, points and status on the export date.',
        '',
        'To refresh them after stories change in Jira, export every issue from Jira as a CSV with all fields, then run',
        '`python docs/stories/from_jira.py path/to/Jira.csv` from the repository root.',
    ]
    groups = {}
    for issue in issues:
        if issue['type'] != 'Epic':
            groups.setdefault(sprint_of(issue), []).append(issue)

    def order(name):
        number = re.search(r'\d+', name)
        return (name == 'Backlog', int(number.group()) if number else 0)

    for group in sorted(groups, key=order):
        lines += ['', f'## {group}', '', '| Issue | Type | Epic | Points | Status |', '|---|---|---|---|---|']
        for issue in groups[group]:
            lines.append(
                f"| [{issue['key']} {issue['title']}]({file_name(issue)}) | {issue['type']} | "
                f"{issue['epic_title']} | {issue['points']} | {issue['status']} |"
            )
    lines += ['', '## Epics', '']
    lines += [f"- [{issue['key']} {issue['title']}]({file_name(issue)})" for issue in issues if issue['type'] == 'Epic']
    (HERE / 'README.md').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')


def main():
    if len(sys.argv) != 2:
        sys.exit('Usage: python docs/stories/from_jira.py path/to/Jira.csv')
    issues = read(sys.argv[1])
    if not issues:
        sys.exit(f'The export has no {PROJECT} issues.')
    exported = datetime.now().strftime('%-d %B %Y') if sys.platform != 'win32' else datetime.now().strftime('%#d %B %Y')
    for old in HERE.glob(f'{PROJECT}-*.md'):
        old.unlink()
    by_key = {issue['key']: issue for issue in issues}
    for issue in issues:
        write_issue(issue, by_key, exported)
    write_index(issues, exported)
    print(f'{len(issues)} issues written to {HERE}')


if __name__ == '__main__':
    main()
