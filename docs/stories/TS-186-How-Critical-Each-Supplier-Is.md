# TS-186 How Critical Each Supplier Is

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-3 Reading Annual Reports](TS-3-Reading-Annual-Reports.md) |
| Sprint | Sprint 3 |
| Points | 5 |
| Labels | s3-new, sprint-3 |
| Assignee | Allan Kok Song Yeong |

## User Story

As a portfolio manager, I want to know when a supplier is a sole or single source, so that I can see where my portfolio is most fragile.

## What We Agreed

- When reading a report, the AI also says whether the report calls a supplier a **sole source** (the only possible supplier of something) or a **single source** (the one supplier used, though others exist), with the sentence that says so. Only when the report says it: never guessed.
- That sentence is quote-checked like every relationship's, and stored as the criticality's evidence.
- **On the graph,** a sole- or single-source arrow is drawn thicker, and the Inspector shows a "Sole Source" or "Single Source" tag with its sentence and Show in Report.
- **Reports already read.** A company already analysed is never sent to Gemini again, so an analyst can start a one-off pass, "Read Reports Again for Criticality", which asks Gemini about criticality only, over the stored reports (no SEC download), and fills it in on the existing relationships. It never adds or removes relationships. It costs one Gemini call per stored report.
- The Disruption Simulator in Sprint 4 relies on this.

## Acceptance Criteria

1. A report whose text calls a supplier its sole source saves that relationship as Sole Source, with the quote-checked sentence; a single source likewise.
1. A criticality sentence that is not in the report is dropped, and the relationship is saved without criticality.
1. Sole- and single-source arrows are thicker on the graph; the Inspector shows the tag and the sentence.
1. The analyst's pass fills criticality on existing relationships of stored reports, adds none, and running it twice changes nothing more.
1. Portfolio managers get 403 from the pass.

## Technical Notes

- **Prompt:** `RelationshipFinder.PROMPT` gains `criticality` (`SOLE_SOURCE`, `SINGLE_SOURCE` or empty) and `criticalityQuote`, with the two definitions above; `GeminiAnswer.Found` and the schema gain both.
- **The pass:** `POST /api/reports/criticality` (analysts only, 202, background), purpose `CRITICALITY`: for each stored report, a prompt listing the relationships already found and asking only for their criticality, matched back by counterparty name.
- **Responses:** `RelationshipResponse` gains `criticality` and `criticalityEvidence`.
- **Website:** `SupplyChainGraph.tsx` (a wider line for critical arrows; the legend gains "Sole or Single Source"); `Inspector.tsx` (the tag and sentence).
- **Test data:** run the new prompt once on `docs/examples/nvidia-10k.txt` and save the real answer beside `gemini-nvidia.json`, with the README entry.
- **Tests:** `GeminiAnswerTest` (the new fields), a `RelationshipFinder` integration test with the saved answer, a pass integration test with a fake `GeminiClient`, website tests for the arrow and the tag.
