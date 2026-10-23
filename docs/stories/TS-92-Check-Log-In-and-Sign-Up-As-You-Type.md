# TS-92 Check Log In and Sign Up As You Type

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 2 |
| Points | 2 |
| Assignee | Allan Kok Song Yeong |

## User Story
As a new user, I want to see a mistake in the email or password as soon as I leave the box, so that I can fix it before pressing the button.

## What We Agreed

- The labels EMAIL and PASSWORD are in small capitals.
- Like GitHub: each box is checked when the user leaves it, and again as they type once it has been checked. Errors appear under the box in small red capitals, with a warning sign.
- Sign Up shows "Password should be at least 8 characters" under the password box.
- No "No account yet?" or "Already have an account?" text. A centred Sign Up button on Log In, and a Log In button on Sign Up, instead of links.
- Log In never says what a password should look like.

## Acceptance Criteria

- Leaving the email box with "gtfsdege" shows "Enter a valid email address." before anything is sent.
- Leaving Sign Up's password box with fewer than 8 characters shows the rule as an error; fixing it clears the error.
- Pressing Sign Up or Log In with a mistake sends nothing, and shows every error.
- "This email is already registered" shows under the email box.
- Log In has a Sign Up button and Sign Up has a Log In button; neither page has a link.

## Technical Notes

- `authChecks.ts` holds the checks, matching the backend's rules: `@Email`, and a password of 8 to 72 characters.
- `FormField.tsx` is the labelled box with its hint or error, using `aria-invalid` and `aria-describedby`.

---

h4.
