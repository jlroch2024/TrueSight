# TS-88 ASML's Report Finds No Suppliers

| | |
|---|---|
| Type | Bug |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-3 Reading Annual Reports](TS-3-Reading-Annual-Reports.md) |
| Sprint | Sprint 1 |
| Assignee | Jeremy Roch |

## What Happens
Analysing ASML finds no relationships, not even Carl Zeiss SMT, its sole supplier of optics, which the sample data and demo rely on.

## Why
ASML's 20-F puts every printed line in its own `<div>`, and the text extractor turned each into a separate paragraph. So sentences stayed broken ("...one of our key suppliers, Carl Zeiss SMT, our"), and Gemini saw only fragments. 86% of the report's lines ended mid-sentence. The annual report story's Technical Notes required joining such lines, naming ASML as the example.

## Acceptance Criteria

- A sentence printed across several `<div>`s comes out whole, on one line.
- In the part of ASML's real 20-F sent to Gemini, no line ends mid-sentence.
- A real run with ASML finds Carl Zeiss SMT as a supplier, with ASML's own sentence as the evidence.
- NVIDIA's report is unaffected: its TSMC sentence is still whole.

## Technical Notes

- `ReportTextExtractor`: a `<div>` adds a line break only if there isn't one, and a line break in the HTML source is a space.
- The test uses eleven real printed lines of ASML's 20-F.
- Reports saved before the fix keep their broken text, so the live database needs clearing once.
