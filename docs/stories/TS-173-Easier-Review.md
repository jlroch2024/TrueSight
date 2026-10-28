# TS-173 Easier Review

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-5 Trust and Review](TS-5-Trust-and-Review.md) |
| Sprint | Sprint 3 |
| Points | 2 |
| Labels | s2-fix, sprint-3 |
| Assignee | Lim Zhe An |

## User Story

As an analyst, I want to see how much is waiting and to give a reason while looking at the report, so that I can work through the review queue quickly and carefully.

## What We Agreed

- **Counts on the filter (item 53):** "Unreviewed (20)", "Approved (1)", "Rejected (0)", "All (21)".
- **Empty states say what to do:** Approved, empty: "None approved yet. Approve one from Unreviewed." Rejected, empty: "None rejected yet." Unreviewed, empty: "Nothing to review. Analyse a portfolio to find more relationships."
- **Rejecting keeps the report in reach (item 52).** When Reject opens the note box, the cursor goes into it, and Show in Report stays beside Confirm Reject and Cancel.
- **Only actions that change something (item 54).** An Approved card offers Reject; a Rejected card offers Approve; an Unreviewed card offers both.
- **Filing dates (item 51)** read like "Feb 24, 2026", through `formatDate`.

## Acceptance Criteria

1. The filter shows each status's count, and the counts change when a relationship is approved or rejected.
1. Each empty filter shows its message as written above.
1. Opening Reject puts the cursor in the note box, and Show in Report is still there.
1. An Approved card has no Approve button; a Rejected card has no Reject button.
1. The filing date reads like "Feb 24, 2026".

## Technical Notes

- File: `frontend/src/pages/ReviewPage.tsx` (and its test). No backend change.
- **Counts:** load `reviewApi.list('ALL')` once and filter in the page by `reviewStatus`, instead of one request per filter (lines 139–154). Counts come from the same list. After a decision, `changed` (lines 158–164) updates the item in the full list; the shown list is derived from it.
- **Filter labels:** `RadioGroup`'s `options` take the label with the count, e.g. `Unreviewed (20)`.
- **Reject form (lines 87–114):** `autoFocus` on the textarea; add the Show in Report button into this branch's `actions` too.
- **Buttons (lines 116–126):** hide Approve when `item.reviewStatus === 'APPROVED'`, Reject when `'REJECTED'`.
- **Filing date (line 73):** `formatDate(relationship.report.filingDate)`. Show Dates and Labels the Same Way makes `formatDate` read a date-only string as a calendar date, which is why it must merge first.
- **Tests:** `ReviewPage.test.tsx` for every criterion (one fake response for `GET /api/reviews` serving all filters).
