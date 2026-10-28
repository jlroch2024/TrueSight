# TS-218 Holdings in Order of Weight

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 29 October 2026) |
| Epic | [TS-2 Portfolios](TS-2-Portfolios.md) |
| Sprint | Sprint 4 |
| Points | 1 |
| Labels | s4-new, sprint-4 |

## User Story
As a portfolio manager, I want my holdings listed from the largest to the smallest, so that the ones that matter most are at the top.

## What We Agreed
- The Holdings tab lists holdings by **weight, largest first**. Holdings with the same weight are in ticker order.
- Holdings without a weight come last, in ticker order.
- A holding whose weight is changed moves to its new place straight away.

## Acceptance Criteria
1. Holdings weighted 2, 18.5 and 8 are listed 18.5, 8, 2.
1. Two holdings with the same weight are listed in ticker order.
1. Holdings without a weight are listed after every weighted holding, in ticker order.
1. After a weight is changed, the holding moves to its new place without reloading the page.

## Technical Notes
- Sort in `components/workspace/view.ts`, where the workspace works out what the drawer shows, so the drawer and anything else reading `view.holdings` agree. The backend's order is left as it is.
- **Tests:** `view.test.ts` for criteria 1 to 3; `HoldingsTab.test.tsx` for criterion 4.
