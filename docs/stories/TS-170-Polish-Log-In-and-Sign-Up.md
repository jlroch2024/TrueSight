# TS-170 Polish Log In and Sign Up

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-1 Accounts and Access](TS-1-Accounts-and-Access.md) |
| Sprint | Sprint 3 |
| Points | 1 |
| Labels | s2-fix, sprint-3 |
| Assignee | Jeremy Roch |

## User Story

As a new visitor, I want Sign Up to look like Log In but be clearly different from it, so that I always know which one I am on.

## What We Agreed

- Sign Up is **one card, the same width and layout as Log In**: logo, motto, heading, fields, button, switch line. Its left panel goes.
- They are told apart by their accent and heading: Log In keeps its teal top edge and teal button; Sign Up has a **lime** top edge, a lime motto and a lime Create Account button, headed **Create Your Account**.
- On both, the fields' labels and their warnings are **capitals of the same font**, small (11 px) with slight letter spacing: "EMAIL", "PASSWORD", "CONFIRM PASSWORD", "⚠ ENTER A VALID EMAIL ADDRESS." Labels stay muted grey; warnings stay red. This is an exception to the Title Case rule, written into `AGENTS.md`.

## Acceptance Criteria

1. Sign Up shows one card with no side panel, the same width as Log In's.
1. Sign Up's card has a lime top edge and a lime Create Account button; Log In's stays teal.
1. On both pages, every field label and field warning is drawn in capitals (`text-transform: uppercase`) at 11 px, in Space Grotesk.
1. Everything else on both pages works as today: the fields, Confirm Password, the warnings, the switch line.

## Technical Notes

- Files: `frontend/src/pages/SignUpPage.tsx`, `frontend/src/styles.css` (the Log In and Sign Up blocks), and `AGENTS.md`.
- Capitals come from `text-transform: uppercase` on the label and warning classes (with `font-size: 11px; letter-spacing: 0.06em`), **not** `font-variant: small-caps`, which `AGENTS.md` forbids.
- `AGENTS.md`, Website Rules, under "Look": "**Log In and Sign Up** are the same card, told apart by their accent: teal for Log In, lime for Sign Up. Their fields' labels and warnings are in capitals of the same font (`text-transform: uppercase`, 11 px): the one exception to Title Case."
- **Earlier build.** Commit `4da4672` on the local branch `TS-16-BugFix-Log-In-and-Sign-Up-Look`, on the product owner's computer, does this. If you work on that computer you may cherry-pick it, then change its `AGENTS.md` line from "small capitals" to "capitals" as written above.
- **Tests:** `SignUpPage.test.tsx` (no side panel; the heading; Confirm Password still checked) and `LogInPage.test.tsx` (unchanged behaviour). The look itself is checked by hand.
