# TS-22 Manage My Portfolios

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
As a portfolio manager, I want to create, rename, delete and switch between portfolios, so that I can analyse different client books separately.

## What We Agreed

- Each portfolio has a name, which must be unique within the user's account.
- A new user starts with no portfolios, and is asked to create one.
- Deleting a portfolio asks for confirmation and removes its holdings. Relationships found in annual reports are kept, because they belong to the reports.
- Every page shows which portfolio is open, and shows that portfolio's data only.

## Acceptance Criteria

- A user can create a portfolio by giving it a name.
- A user can rename a portfolio.
- Creating or renaming a portfolio to a name the user already uses is refused with "You already have a portfolio with this name".
- Deleting a portfolio asks for confirmation, then removes it and its holdings.
- A user can switch between portfolios, and the page shows the chosen portfolio's name.
- One user cannot see, rename or delete another user's portfolios.

## Technical Notes

- Endpoints: `GET /api/portfolios`, `POST /api/portfolios`, `PUT /api/portfolios/{id}` and `DELETE /api/portfolios/{id}`.
- Another user's portfolio answers 404 Not Found, never 403, so nobody can discover other users' portfolios by trying numbers.
- A portfolio stores its name, its owner and when it was created.
- Frontend: a Portfolios page listing the user's portfolios, with Create, Rename and Delete. The open portfolio's name shows in the header, with a switcher.
- The Portfolios page is at /portfolios, and is added to NAV_ITEMS in navigation.ts. Choosing a portfolio opens /portfolios/{id}. The switcher in the header moves between portfolios by changing the address.

**Needs First:** Sign Up and Log In
