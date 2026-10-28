# TS-192 Edit My Profile

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-1 Accounts and Access](TS-1-Accounts-and-Access.md) |
| Sprint | Sprint 3 |
| Points | 3 |
| Labels | s3-new, sprint-3 |
| Assignee | isaiah |

## User Story

As a user, I want to change my display name and password, so that my account stays mine and up to date.

## What We Agreed

- The header's email becomes a **Profile** button (showing the display name once set) that opens a Profile window, like Manage Portfolios.
- **Display name:** 1 to 100 characters. It replaces the email in the header and wherever an analyst's decision is shown ("Approved by Jo Tan"). Without one, the email is shown as today.
- **Change password:** current password, new password and confirm, with the same rules as Sign Up. A wrong current password says "Your current password is not right." The user stays logged in.
- The email cannot be changed.

## Acceptance Criteria

1. Saving a display name shows it in the header and on decisions the user made.
1. A blank or over-long name is refused with 400.
1. Changing the password with the right current password works, and the new one logs in; the old one no longer does.
1. A wrong current password is refused with 400 and the message above; a mismatch between new and confirm is caught before sending.

## Technical Notes

- **Endpoints:** `GET /api/auth/me` gains `displayName`; `PUT /api/me` with `{ "displayName": "…" }` (200, the me response); `PUT /api/me/password` with `{ "currentPassword", "newPassword" }` (204). `@Tag(name = "Profile")`.
- The password rules come from `AuthController.SignUpRequest`; reuse them rather than copying.
- `ReviewItemResponse.reviewedBy` (and the other "by" fields of Sprint 3) send the display name when set.
- **Website:** `components/ProfileWindow.tsx`, opened from `Layout.tsx`'s header (a shared piece; this story may change it).
- **Tests:** `ProfileIT` for each criterion; website tests for the window.
