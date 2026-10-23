# TS-90 Failed Holding Blocks Analyse Permanently

| | |
|---|---|
| Type | Bug |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-3 Reading Annual Reports](TS-3-Reading-Annual-Reports.md) |
| Sprint | Sprint 1 |
| Assignee | Jeremy Roch |

## What Happens
Once any holding fails (for example, the SEC is briefly unreachable), Analyse is disabled for that portfolio for good. The only way out is uploading the CSV again.

## Why
The backend refused Analyse (409) while any holding was Failed, and nothing ever reset a Failed holding.

## What We Agreed

- Pressing Analyse tries the Failed holdings again.
- Done and No Report Found holdings are left alone.
- Analyse is still refused while an analysis is running.

## Acceptance Criteria

- A Failed holding still shows the reason, as before.
- Pressing Analyse again retries it, and it can end Done, with the old reason cleared.
- Analyse is refused while an analysis is already running.
- After a failure, the page says "Some holdings failed. Press Analyse to try them again."

## Technical Notes

- `AnalysisService.start` queues Waiting and Failed holdings.
- The two tests of the old blocking behaviour now test the new one.
