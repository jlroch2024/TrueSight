# TS-96 Show Report and Last Analysed

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 2 |
| Points | 2 |
| Assignee | Allan Kok Song Yeong |

## User Story
As a portfolio manager, I want to see when each holding was last analysed, and open its report with a clear button, so that I know how fresh the results are.

## What We Agreed

- The holding's "Show in Report" link becomes a **Show Report** button: it opens the report but highlights nothing.
- A **Last Analysed** column shows when TrueSight read the report and asked the AI, once the holding is Done.
- No links anywhere in the website: every action is a button.

## Acceptance Criteria

- A Done holding shows its Last Analysed date and time.
- Show Report opens the holding's report in a new tab.
- A holding that isn't Done yet shows neither.
- No page has a link.

## Technical Notes

- `HoldingResponse.lastAnalysed` (ISO time) is the report's `created_at`, given only when the holding is Done. No migration.
- `format.ts` holds `formatDateTime` and `openInNewTab`.
- `AGENTS.md` gains the "No links" rule, and "Show Report" in Terms.

---

h4.
