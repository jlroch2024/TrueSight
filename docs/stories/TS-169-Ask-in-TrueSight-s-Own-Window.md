# TS-169 Ask in TrueSight's Own Window

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-2 Portfolios](TS-2-Portfolios.md) |
| Sprint | Sprint 3 |
| Points | 3 |
| Labels | s2-fix, sprint-3 |
| Assignee | GOH ZYIK XYUN |

## User Story

As a portfolio manager, I want TrueSight to ask before deleting a portfolio or replacing its holdings, in a window that works in every browser, so that I can delete what I mean to and nothing else.

## What We Agreed

- Delete (in the Manage Portfolios window) and Upload CSV over existing holdings (on the Portfolio page) ask in TrueSight's own confirmation window, never the browser's `window.confirm`. Some browsers never show the browser's box and answer Cancel, so on the live site Delete does nothing there today.
- The window has a title, the question, **Cancel** and a red action button (**Delete**, or **Replace Holdings**). Cancel has the focus first, so Enter never deletes by accident. Escape, or a click outside, cancels. Tab moves between the two buttons only. Screen readers hear an alert dialog with its title and question.
- **Rename** in Manage Portfolios puts the cursor in the New Name box with the name selected. After Save or Cancel the cursor returns to that portfolio's Rename button.
- `AGENTS.md` gains the rule: ask with `useConfirm()`, never `window.confirm`, `alert` or `prompt`.

## Acceptance Criteria

1. Delete opens TrueSight's window: "Delete Portfolio", "Delete "Growth" and all its holdings? This cannot be undone.", with Cancel and a red Delete. Delete removes the portfolio; Cancel, Escape and a click outside leave it.
1. Uploading a CSV into a portfolio that already has holdings opens the same window, titled "Replace Holdings", with a red Replace Holdings button; Cancel sends nothing.
1. When the window opens, Cancel has the focus, and Tab never leaves the window.
1. No source file in `frontend/src` calls `window.confirm`, `alert` or `prompt`.
1. Clicking Rename puts the cursor in the New Name box with the whole name selected; after Cancel, the focus is on that portfolio's Rename button.

## Technical Notes

- **New** `frontend/src/components/ConfirmDialog.tsx`, exporting `useConfirm()`: `const { confirm, dialog } = useConfirm();` then `if (await confirm({ title, message, confirmLabel, danger: true }))`, and the page renders `{dialog}`. `ConfirmOptions = { title: string; message: string; confirmLabel: string; danger?: boolean }`. The window: `role="alertdialog"`, `aria-modal="true"`, `aria-labelledby` (title) and `aria-describedby` (message). Style it like the Manage Portfolios window (`.window-backdrop`, `.window` in `styles.css`): square corners, hairline border, the danger button's existing `danger` class.
- **Manage Portfolios is itself a window.** The confirmation opens above it (a higher `z-index`), and while it is open, Escape closes only the confirmation, not Manage Portfolios: `ManagePortfolios.tsx` lines 66–72 must ignore Escape while `dialog` is shown.
- **Delete:** `ManagePortfolios.tsx` line 130 replaces `window.confirm(...)`. **Replace:** `PortfolioPage.tsx` line 102.
- **Rename focus (item 36):** `ManagePortfolios.tsx` lines 171–186. The New Name input gets `autoFocus` and selects its text on focus; keep a ref per Rename button (or find it by its `aria-label`, `Rename {name}`) to focus after Save or Cancel.
- **The guard test:** extend `frontend/src/noNativeControls.test.ts` with a test that no source matches `/\b(window\.)?(confirm|alert|prompt)(/`, ignoring `await confirm(`.
- `AGENTS.md`, Website Rules, "No browser-default controls": add "Ask for confirmation with `useConfirm()` from `components/ConfirmDialog.tsx`, never `window.confirm`, `alert` or `prompt`, which some browsers never show."
- **Earlier build.** Commit `064bab3` on the local branch `TS-22-BugFix-Confirmation-Window`, on the product owner's computer, built this window for the Portfolios page that One Portfolio Page later removed. If you work on that computer you may cherry-pick it onto your branch and move its Delete onto Manage Portfolios; otherwise build it from these notes.
- **Tests:** `ConfirmDialog.test.tsx` (focus starts on Cancel, Escape and outside click cancel, Tab stays inside, confirm answers true); `ManagePortfolios.test.tsx` (Delete sends `DELETE` only after confirming; Escape in the confirmation leaves Manage Portfolios open; Rename focus); `PortfolioPage.test.tsx` (Replace asks first).
