# TS-91 Sharp Theme

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 2 |
| Points | 3 |
| Assignee | Allan Kok Song Yeong |

## User Story
As a portfolio manager, I want TrueSight to look sharp and consistent, so that it feels like a professional tool.

## What We Agreed

- A dark "blueprint" look: hairline borders, square corners, teal and lime, Space Grotesk, with JetBrains Mono for tickers and numbers.
- A filled button is the main action on a page, with one 45° chamfer on its top-right corner. Other buttons are outlined and square.
- Labels are in small capitals.
- A header across the top: the logo tile and TrueSight on the left, and the logged-in email and Log Out on the right.

## Acceptance Criteria

- Every page uses the new colours, square corners and chamfered main buttons.
- The header shows who is logged in.
- Log Out works from the header.

## Technical Notes

- `styles.css` rewritten, keeping the class names, so pages don't change. The email comes from `GET /api/auth/me`.
- `AGENTS.md`'s "Look" rule is updated to describe the new style.

---

h4.
