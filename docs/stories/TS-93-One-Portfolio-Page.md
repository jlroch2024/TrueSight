# TS-93 One Portfolio Page

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 2 |
| Points | 3 |
| Assignee | GOH ZYIK XYUN |

## User Story
As a portfolio manager, I want one Portfolio page with a list to switch portfolios and a way to manage them, so that I don't have to go between two pages.

## What We Agreed

- No separate Portfolios page. The header has a Portfolio list, to switch, and a Manage Portfolios button.
- Manage Portfolios opens a window listing every portfolio: the open one marked Active; Open, Rename and Delete buttons; a box to create one; and Close.
- Switching portfolios keeps the same kind of page, so Supply Chain stays on Supply Chain.

## Acceptance Criteria

- Every Manage My Portfolios criterion still holds, now in the window: create, refuse a duplicate name, rename, refuse renaming to a used name, and delete only after confirming.
- Creating a portfolio opens it; deleting the open one opens another.
- `/portfolios` opens the first portfolio, or asks a user with none to create one.
- The sidebar shows Portfolio and Supply Chain for the open portfolio.
- The window has no links.

## Technical Notes

- `PortfoliosPage.tsx` and `PortfolioSwitcher.tsx` are replaced by `PortfolioPicker.tsx`, `ManagePortfolios.tsx` and `PortfolioStart.tsx`.
- The tests move to `ManagePortfolios.test.tsx`, keeping every existing criterion.
- `AGENTS.md`'s page table is updated.

---

h4.
