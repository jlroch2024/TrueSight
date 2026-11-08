"""Builds report-section-filings.json from the plain text of 29 real annual reports (TS-229).

Usage: python make_report_section_filings.py LIST_JSON TEXT_DIR > report-section-filings.json

LIST_JSON lists each filing's ticker, form, filing date and SEC address. TEXT_DIR holds each filing's text, made from
that address by ReportTextExtractor (TICKER.txt). The texts are about 17 MB, so they are not kept in the repository:
this file keeps, for each filing, its length, every line holding "item" and a number with its position (and the start
of the line after it, since a heading's title can be on the next line), and the section this design chooses. A test
rebuilds a text of the true length with those lines at their true places.

The rules are ReportSection's, written again here so the test also checks the two agree.
"""
import json
import re
import sys

FLAGS = re.IGNORECASE
AFTER_NUMBER = r"(?:\s*\.\s*[a-d]\b)?\s*[.:\-–—]?\s*"
HEADINGS = {
    "10-K": (r"1", r"business", r"2", r"properties"),
    "20-F": (r"3", r"key\s+information", r"5", r"operating\s+and\s+financial\s+reviews?"),
}
MENTION_BEFORE = re.compile(r"(?:(?<![A-Za-z])[a-z]+|[,;:(\"“‘'])[ \t ’\"“]*$")
MENTION_AFTER = re.compile(r"^(?:[ \t ]*[,;)\"”’']|[ \t ]+[a-z])")
ITEM_LINE = re.compile(r"\bitem\s*\d", FLAGS)
MIN_SHARE = 0.05
NEXT_LINE_KEPT = 200


def heading(number, title):
    return re.compile(r"\bitem\s*" + number + AFTER_NUMBER + title + r"\b", FLAGS)


def is_heading(match, text):
    line_start = text.rfind("\n", 0, match.start()) + 1
    before = text[line_start:match.start()]
    after = text[match.end():match.end() + 40]
    return not MENTION_BEFORE.search(before) and not MENTION_AFTER.search(after)


def stretch(form, text):
    start_number, start_title, end_number, end_title = HEADINGS[form]
    start, end = heading(start_number, start_title), heading(end_number, end_title)
    longest = None
    for s in start.finditer(text):
        if not is_heading(s, text):
            continue
        for e in end.finditer(text, s.end()):
            if is_heading(e, text):
                if longest is None or e.start() - s.start() > longest[1] - longest[0]:
                    longest = (s.start(), e.start())
                break
    if longest is None or longest[1] - longest[0] < MIN_SHARE * len(text):
        return None
    return longest


def item_lines(text):
    lines, position, keep_next = [], 0, False
    for line in text.split("\n"):
        if ITEM_LINE.search(line):
            lines.append({"position": position, "text": line})
            keep_next = True
        elif keep_next and line.strip():
            lines.append({"position": position, "text": line[:NEXT_LINE_KEPT]})
            keep_next = False
        position += len(line) + 1
    return lines


def main(list_json, text_dir):
    filings = []
    for filing in json.load(open(list_json, encoding="utf-8")):
        text = open(f"{text_dir}/{filing['ticker']}.txt", encoding="utf-8").read()
        # Java counts UTF-16 units; these texts have no character outside the Basic Multilingual Plane.
        assert all(ord(c) < 0x10000 for c in text), filing["ticker"]
        chosen = stretch(filing["form"], text)
        filings.append({
            "ticker": filing["ticker"],
            "form": filing["form"],
            "filingDate": filing["filingDate"],
            "address": filing["address"],
            "length": len(text),
            "start": chosen[0] if chosen else "whole",
            "end": chosen[1] if chosen else "whole",
            "itemLines": item_lines(text),
        })
    json.dump(filings, sys.stdout, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
