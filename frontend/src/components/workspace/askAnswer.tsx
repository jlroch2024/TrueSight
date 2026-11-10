// How an answer from Ask TrueSight is laid out: one line per sentence, "supplies" drawn as the graph's arrow, and the
// companies and figures picked out. The words are the AI's own; only their setting changes.
import { Fragment, type ReactNode } from 'react';
import { FlowLink } from '../FlowLink';

// The colour of what is supplied (lime, as --lime is).
export const SUPPLIED = '#c4f36a';

// A full stop after one of these does not end a sentence.
const NOT_AN_END = /\b(?:Inc|Corp|Co|Ltd|Plc|No|vs|U\.S|e\.g|i\.e)\.$/i;

/** The answer's lines: a new line for each sentence, and after each semicolon. */
export function answerLines(answer: string): string[] {
  const lines: string[] = [];
  for (const piece of answer.split(/(?<=[.!?])\s+(?=[A-Z"'])|(?<=;)\s+/)) {
    const before = lines[lines.length - 1];
    if (before !== undefined && NOT_AN_END.test(before)) lines[lines.length - 1] = `${before} ${piece}`;
    else lines.push(piece);
  }
  return lines;
}

const escape = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** One line's words, with its company names and figures in bold and its "supplies" as an arrow. */
function setLine(line: string, names: string[]): ReactNode {
  const known = [...new Set(names.filter((name) => name.length > 1))].sort((a, b) => b.length - a.length).map(escape).join('|');
  const name = known ? `(?<![\\w])(?:${known})(?![\\w])` : '(?!)';
  const pattern = new RegExp(`(${name})|(\\d+(?:\\.\\d+)?%|\\b\\d+ holdings?\\b)|\\b(supplies(?=\\s+[A-Z0-9])|supply(?=\\s+(?:${name})))\\b`, 'g');
  const parts: ReactNode[] = [];
  let at = 0;
  for (const match of line.matchAll(pattern)) {
    parts.push(line.slice(at, match.index));
    at = match.index + match[0].length;
    parts.push(
      match[3] ? (
        <span key={match.index} className="ask-arrow">
          <span className="visually-hidden">{match[0]}</span>
          <FlowLink colour={SUPPLIED} />
        </span>
      ) : (
        <strong key={match.index} className={match[2] ? 'num' : undefined}>{match[0]}</strong>
      ),
    );
  }
  if (at === 0) return line;
  parts.push(line.slice(at));
  return parts.map((part, index) => <Fragment key={index}>{part}</Fragment>);
}

export function AskAnswerText({ answer, names }: { answer: string; names: string[] }) {
  return (
    <div className="ask-answer">
      {answerLines(answer).map((line, index) => (
        // A line that only says the data is unreviewed steps back behind the findings.
        <p key={index} className={/^[^.;]*\bunreviewed\b[^.;]*[.;]?$/i.test(line) && !/\bsuppl/i.test(line) ? 'ask-caveat' : undefined}>
          {setLine(line, names)}
        </p>
      ))}
    </div>
  );
}
