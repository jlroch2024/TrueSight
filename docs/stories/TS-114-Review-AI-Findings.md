# TS-114 Review AI Findings

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-5 Trust and Review](TS-5-Trust-and-Review.md) |
| Sprint | Sprint 2 |
| Points | 5 |
| Labels | sprint-2 |
| Assignee | isaiah |

**Owner:** Isaiah · **Needs First:** Analyst Role

## User Story

As an analyst, I want to approve or reject each supplier and customer the AI found, starting with the ones most portfolios rely on, so that portfolio managers can trust what they see and wrong findings never drive their alerts.

## What We Agreed

- Every relationship starts **Unreviewed**. An Analyst can **Approve** or **Reject** it.
- **Rejecting needs a note** saying why (e.g. "Planned partner, not a supplier"). Approving may have one.
- A decision can be changed later. The latest decision counts, and records who made it and when.
- **Rejected relationships disappear from every graph** and are never used for alerts. Nothing is deleted, so a rejection can be undone.
- **Review what matters first:** Unreviewed relationships come first, ordered by how many portfolios rely on each one. Each shows "Used by 3 portfolios", **never which portfolios or whose**.
- Relationships are shared, so one decision applies to every user. The page says so.
- The page is Analyst-only and appears in the sidebar as **Review**.

## Acceptance Criteria

1. An Analyst opening Review sees every Unreviewed relationship with: the two companies and which supplies which, what is provided, the report's sentence, the report (form and filing date), "Used by N portfolios", and a **Show Report** button that opens the filing.
1. A relationship used by 3 portfolios is listed above one used by 1.
1. Approving marks it Approved, with the Analyst's email and the time, and it leaves the Unreviewed list.
1. Rejecting without a note is refused with "Please say why this is rejected."
1. Rejecting with a note marks it Rejected, and it no longer appears on any portfolio's graph.
1. Changing a rejected relationship to Approved brings it back on the graph.
1. The filter shows Unreviewed, Approved, Rejected or All.
1. A Portfolio Manager does not see Review in the sidebar, and the API answers them 403.
1. With nothing left to review, the page says "Nothing to review. Analyse a portfolio to find more relationships."
1. Decisions are still there after the backend restarts.

## Technical Notes

- **Endpoints** (controller `ReviewController`, `@Tag(name = "Review")`, both call `currentUser.requireAnalyst()`):
   - `GET /api/reviews?status=UNREVIEWED|APPROVED|REJECTED` (no parameter: all). Order: Unreviewed first; then `usedByPortfolios`, highest first; then newest report first. Answer: a list of `ReviewItemResponse`.
   - `PUT /api/relationships/{id}/review` with `ReviewRequest`, answers 200 with the updated `ReviewItemResponse`. Unknown id: 404 "Relationship not found." A note over 500 characters: 400.

```json
// ReviewRequest
{ "decision": "REJECTED", "note": "Planned partner, not a current supplier." }

// ReviewItemResponse
{
  "relationship": { "...": "the existing RelationshipResponse: id, company, counterparty, type, provides, evidence, report" },
  "reviewStatus": "UNREVIEWED",
  "reviewNote": null,
  "reviewedBy": null,
  "reviewedAt": null,
  "usedByPortfolios": 3
}
```

- `usedByPortfolios` = the number of distinct portfolios with a holding whose `company_id` is either the relationship's `company_id` or its `counterparty_id`. One query for the whole list, not one per item.
- `ReviewRequest`: `decision` is `@NotNull` (`APPROVED` or `REJECTED`); `note` is `@Size(max = 500)`. The "Please say why" rule is checked in `ReviewService` (it depends on the decision).
- **Graph data:** `RelationshipService.forPortfolio` leaves out `REJECTED`, and `RelationshipResponse` gains `reviewStatus`, `reviewedAt` and `reviewNote` (needed by Show Review Status on the Graph). Update `docs/examples/relationships.json` and its README to match: every sample relationship `UNREVIEWED`.
- **Website:** `src/pages/ReviewPage.tsx` at `paths.review`; `src/api/reviews.ts`; add `{ label: 'Review', path: paths.review }` to `ANALYST_NAV_ITEMS`. Each item is a card: "TSMC → NVIDIA" with "supplies" or "buys from", what is provided, the sentence in a quote block, the report line, "Used by N portfolios", **Show Report** (uses `openInNewTab`), **Approve**, **Reject** (opens a required note box, then Confirm Reject). Above the list: the filter and "Your decisions apply to every portfolio manager." All four page states apply.
- **Tests:** `ReviewIT` for criteria 1–8 and 10 (use `logins.analystBearer`, and build portfolios with holdings so `usedByPortfolios` differs); `FindSuppliersAndCustomersIT` or a new `RelationshipIT`: rejected relationships are left out of the graph data; `ReviewPage.test.tsx` for the cards, the note rule, the filter and the empty state.

**Out of scope:** editing a relationship (Sprint 3: Fix or Add a Relationship); reviewing news (Sprint 3).
