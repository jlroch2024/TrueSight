# TS-16 Sign Up and Log In

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-1 Accounts and Access](TS-1-Accounts-and-Access.md) |
| Sprint | Sprint 1 |
| Points | 3 |
| Labels | sprint-1 |
| Assignee | isaiah |

## User Story
As a portfolio manager, I want my own account, so that only I can see my portfolios.

## What We Agreed

- An account uses an email and a password of at least 8 characters.
- Passwords are stored scrambled, never as typed.
- A login lasts 2 hours. After that, the user logs in again.
- There is no email verification or password reset in this sprint.

## Acceptance Criteria

- A new user can sign up with an email and a password, and is then logged in.
- A user can log in and log out.
- A wrong email or password shows "Wrong email or password".
- Signing up with an email that already has an account shows "This email is already registered".
- A password shorter than 8 characters is refused with a message saying so.
- A user who is not logged in is sent to the log-in page, and the backend refuses their requests.

## Technical Notes

- Endpoints: `POST /api/auth/signup` and `POST /api/auth/login`, which both return a token, and `GET /api/auth/me`, which returns the logged-in user's email.
- Every other `/api/` address needs the token, except `/api/health` and the Swagger page.
- Use Spring Security with a signed token (JWT) that expires after 2 hours. The signing secret comes from `JWT_SECRET`.
- Hash passwords with BCrypt. No response ever contains the password or its hash.
- Frontend: Sign Up and Log In pages, and a Log Out button. Keep the token in the browser and send it with every request as `Authorization: Bearer <token>`.
- Replace the setup's placeholder current user with the real logged-in user, taken from the token. Every endpoint that uses the current user then works for real.
- Log In and Sign Up are at /login and /signup (see paths.ts), outside the sidebar layout. When the backend answers 401, clear the token and go to /login.
Add a test helper that makes requests as a logged-in user, and update the existing integration tests to use it wherever they now need a token.

**Needs First:** Set Up the Project
