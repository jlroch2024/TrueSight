# TS-28 Upload a Portfolio CSV

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-2 Portfolios](TS-2-Portfolios.md) |
| Sprint | Sprint 1 |
| Points | 3 |
| Labels | sprint-1 |
| Assignee | GOH ZYIK XYUN |

## User Story
As a portfolio manager, I want to upload my holdings as a CSV file, so that I do not have to type each company in.

## What We Agreed

- A CSV is uploaded into the portfolio that is currently open.
- If that portfolio already has holdings, the upload replaces them after a confirmation, for example "This replaces the 3 holdings in Tech."
- The CSV must have a ticker column, named `ticker` or `symbol` in any capitalisation. A weight column is optional.
- Files can be up to 5 MB.

## Acceptance Criteria

- Uploading `docs/examples/holdings.csv` into an empty portfolio shows its 10 holdings.
- Uploading into a portfolio that already has holdings asks for confirmation first, then shows only the new holdings.
- Cancelling the confirmation leaves the old holdings unchanged.
- A CSV with no ticker column is refused with "The file needs a ticker column."
- A file that is not a CSV is refused.
- The holdings are still there after the page is refreshed.
- One user cannot see another user's holdings.
- Switching to another portfolio shows that portfolio's holdings.

## Technical Notes

- Endpoints: `POST /api/portfolios/{id}/holdings/upload` (a file upload) replaces the portfolio's holdings. `GET /api/portfolios/{id}/holdings` lists them.
- Every request checks that the portfolio belongs to the logged-in user. If it does not, the answer is 404 Not Found.
- A holding stores its ticker and weight (empty if not given), plus fields the analysis fills in later: company name, status, report type, report date and report link. New holdings start as Waiting.
- Replacing happens in one database transaction, so if the new file is invalid, the old holdings stay.
- Ticker column: the first column whose name, ignoring spaces and capitals, is `ticker` or `symbol`. Weight column: `weight`, accepting both `12.5` and `12.5%`.
- Frontend: an Upload CSV button on the portfolio page. The confirmation appears only when the portfolio already has holdings.

**Needs First:** Manage My Portfolios
