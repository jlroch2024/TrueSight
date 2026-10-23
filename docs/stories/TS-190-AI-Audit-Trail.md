# TS-190 AI Audit Trail

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 23 October 2026) |
| Epic | [TS-5 Trust and Review](TS-5-Trust-and-Review.md) |
| Sprint | Sprint 3 |
| Points | 5 |
| Labels | s3-new, sprint-3 |
| Assignee | Ben Aw Yong |

## User Story

As an analyst, I want a record of every AI call, so that I can see why the AI got something wrong.

## What We Agreed

- The Analyst Desk's **AI Calls** tab lists every Gemini call, newest first: when, what for (Finding Relationships, Judging News, Second Check, Recommending, Criticality, Common Names), the model, the outcome, how long it took, and how many answers were kept and dropped.
- **Filters:** purpose, outcome, and a date range. 50 per page.
- **Opening a call** shows its prompt (a report's text appears as its marker, with Show Report) and Gemini's answer exactly as it arrived.
- **Export CSV** downloads the filtered list (without prompts and answers, which can be very long).
- The recording itself is the setup's; this story shows it.

## Acceptance Criteria

1. The tab lists calls newest first with the columns above, 50 per page.
1. Each filter narrows the list, and the CSV holds exactly the filtered calls.
1. Opening a call shows its prompt and answer; a report's text is never shown inline.
1. Portfolio managers get 403.

## Technical Notes

- **Endpoints (analysts only):** `GET /api/ai-calls?purpose=&outcome=&from=&to=&page=`, `GET /api/ai-calls/{id}`, `GET /api/ai-calls/export?…` (`text/csv`, `Content-Disposition: attachment`). `@Tag(name = "AI Audit Trail")`.
- **Downloading:** `api()` returns JSON, so add `apiDownload(path, filename)` to `src/api/client.ts` beside it (a shared piece; this story may change it), with the same login token and errors.
- **Tests:** `AiCallsIT` (filters, paging, CSV, 403); website tests for the tab, a call's detail and the export.
