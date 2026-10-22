# TS-94 Open on the Portfolio Page

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 2 |
| Points | 1 |
| Assignee | Jeremy Roch |

## User Story
As a portfolio manager, I want to land on my portfolio when I log in, so that I start where the work is.

## What We Agreed

- No Home page: `/` opens the first portfolio, like `/portfolios`.

## Acceptance Criteria

- Logging in opens the first portfolio.
- Signing up asks the new user to create a first portfolio.
- There is no Home link in the sidebar.
- An unknown address offers an Open My Portfolios button.

## Technical Notes

- `HomePage.tsx` is removed, and `NAV_ITEMS` is empty.
- `AGENTS.md`'s example page for the four page states becomes `PortfolioPage.tsx`.
- The README's setup step 4 no longer mentions the home page.
- `HealthController`'s comment is updated.

---

h4.
