# TS-109 Analyst Role

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-1 Accounts and Access](TS-1-Accounts-and-Access.md) |
| Sprint | Sprint 2 |
| Points | 3 |
| Labels | sprint-2 |
| Assignee | isaiah |

**Owner:** Isaiah · **Needs First:** Set Up Sprint 2

## User Story

As an analyst, I want my own kind of account, so that I can check what the AI finds while portfolio managers use the results.

## What We Agreed

- Two roles: **Portfolio Manager** (everyone, by default) and **Analyst**.
- **Nobody can make themselves an Analyst.** The Analysts are the email addresses in the setting `ANALYST_EMAILS` (comma-separated, any capitalisation), applied when an account is created and again every time the app starts.
- An Analyst can do everything a Portfolio Manager can, plus the Analyst-only actions later stories add.
- The sidebar shows who is logged in, and "Analyst" under the email for an Analyst.

## Acceptance Criteria

1. Signing up with an email listed in `ANALYST_EMAILS` creates an Analyst; any other email creates a Portfolio Manager.
1. Adding an existing user's email to `ANALYST_EMAILS` makes them an Analyst once the app restarts; removing it makes them a Portfolio Manager again.
1. `GET /api/auth/me` returns the user's email and role.
1. The sidebar shows the logged-in email, with "Analyst" under it for an Analyst and nothing extra for a Portfolio Manager.
1. A Portfolio Manager who calls an Analyst-only address gets **403** with "Only analysts can do this."
1. Emails in the setting match whatever their capitalisation (`Analyst@Firm.com` matches `analyst@firm.com`).

## Technical Notes

- **Setting:** `application.yml`: `truesight.analyst-emails: ${ANALYST_EMAILS:}`. Add `ANALYST_EMAILS=` to `.env.example` with a comment. On the live site it is a plain Cloud Run variable, not a secret.
- **Backend, package** `user`**:**
   - `AnalystEmails` (`@Component`): reads the setting once into a lower-cased, trimmed `Set<String>`; `boolean isAnalyst(String email)`.
   - `AuthService.signUp` sets the new user's role from `AnalystEmails`.
   - `AnalystRoleSync` (`ApplicationRunner`, all profiles): for every user, sets `ANALYST` if their email is listed, otherwise `PORTFOLIO_MANAGER`. Skip the demo analyst `analyst@truesight.local`, which keeps its role.
   - `CurrentUser.requireAnalyst()`: throws `ApiException.forbidden("Only analysts can do this.")` unless `role()` is `ANALYST`. Every later Analyst-only endpoint calls this one method first; never copy the check.
- **API:** `AuthResponse` becomes `(String token, String email, String role)`; `MeResponse` becomes `(String email, String role)`. Role values: `PORTFOLIO_MANAGER`, `ANALYST`.
- **Website:**
   - `src/api/auth.ts`: `type Me = { email: string; role: 'PORTFOLIO_MANAGER' | 'ANALYST' }`; `authApi.me()`.
   - `Layout.tsx`: fetch `me` once, show the email above Log Out, "Analyst" under it for Analysts, and the `ANALYST_NAV_ITEMS` only for Analysts.
- **Tests:**
   - `AuthIT` (with `@TestPropertySource(properties = "truesight.analyst-emails=Listed@Example.com")`): criteria 1, 3, 6.
   - `AnalystRoleSyncIT`: create users, run the runner with a changed list, check roles (criterion 2).
   - `CurrentUserTest` or `CurrentUserIT`: `requireAnalyst()` throws 403 for a Portfolio Manager (criterion 5).
   - `Layout.test.tsx`: the "Analyst" label, and no Analyst items, for each role (criterion 4).

**Out of scope:** changing roles inside the app; an admin role.
