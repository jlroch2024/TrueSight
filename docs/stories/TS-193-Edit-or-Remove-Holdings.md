# TS-193 Edit or Remove Holdings

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 23 October 2026) |
| Epic | [TS-2 Portfolios](TS-2-Portfolios.md) |
| Sprint | Sprint 3 |
| Points | 3 |
| Labels | s3-new, sprint-3 |
| Assignee | isaiah |

## User Story

As a portfolio manager, I want to change one holding's weight, remove it, or add one, so that I do not have to upload a whole new CSV.

## What We Agreed

- In the Portfolio page's holdings table: **Edit** a weight in place (0 to 100, blank for none), **Remove** a holding (asked first with TrueSight's own window), and **Add Holding** (a ticker and an optional weight).
- An added holding is saved as Waiting and its analysis **starts at once**; if an analysis is already running, it waits for the next Analyse.
- A ticker already in the portfolio is refused: "{TICKER} is already in this portfolio."
- Removing a holding also removes it from the portfolio's alerts; an alert left with no holdings is removed.
- One Workspace later moves this into the Holdings tab.

## Acceptance Criteria

1. Editing a weight saves it and the table shows it; a weight outside 0 to 100 is refused with 400.
1. Removing a holding asks first, then removes it and its place in alerts.
1. Adding a ticker shows it as Waiting, then Analysing, then its result.
1. Adding a ticker already held is refused with 409 and the message above.
1. Another user's portfolio answers 404.

## Technical Notes

- **Endpoints:** `POST /api/portfolios/{id}/holdings` with `{ "ticker", "weight" }` (201, the holding), `PUT /api/portfolios/{id}/holdings/{holdingId}` with `{ "weight" }` (200), `DELETE …/{holdingId}` (204), in `HoldingController`, with `@Operation` summaries. Every lookup includes the owner.
- **Starting the analysis:** reuse `AnalysisService.start` and `AnalysisJob`; a 409 from a running analysis is not an error here.
- **Alerts:** `alert_holdings` already cascades; delete alerts left empty in the same transaction.
- **Website:** `PortfolioPage.tsx`'s table and an Add Holding form above it; Remove uses `useConfirm`.
- **Tests:** `HoldingEditIT` for each criterion; `PortfolioPage.test.tsx` for the table actions.
```

```
