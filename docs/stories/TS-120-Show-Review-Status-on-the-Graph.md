# TS-120 Show Review Status on the Graph

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 2 |
| Points | 2 |
| Labels | sprint-2 |
| Assignee | Allan Kok Song Yeong |

**Owner:** Allan · **Needs First:** Review AI Findings

## User Story

As a portfolio manager, I want to see which relationships an analyst has checked, so that I know how much to trust each one.

## What We Agreed

- **Approved** relationships are solid arrows; **Unreviewed** ones are dashed. Rejected ones are not drawn (the API already leaves them out).
- Selecting an arrow adds one line to its panel: "Approved by an analyst on 14 October 2026" (plus the note, if any), or "Not yet reviewed by an analyst".
- The graph's legend (from Graph Legend and Arrow Hover) gains two entries: solid = Approved, dashed = Not yet reviewed.

## Acceptance Criteria

1. In a portfolio with one Approved and one Unreviewed relationship, the first is drawn solid and the second dashed.
1. Selecting the Approved arrow shows "Approved by an analyst on …" with the note; selecting the Unreviewed one shows "Not yet reviewed by an analyst".
1. After an Analyst rejects a relationship, it is gone from the graph when the page is refreshed.
1. The legend shows both line styles whenever the graph has arrows.

## Technical Notes

- Uses `reviewStatus`, `reviewedAt` and `reviewNote` on each item of `GET /api/portfolios/{id}/relationships`. Add them to the `Relationship` type in `src/api/relationships.ts`.
- `src/pages/supplyChain.ts` (the code that turns relationships into graph data) copies `reviewStatus` onto each edge; `SupplyChainGraph.tsx` styles edges with `reviewStatus = UNREVIEWED` as `line-style: dashed`. Keep the layout, zoom, flow and Fit to Screen behaviour exactly as they are.
- Dates use `formatDate` ("14 October 2026"); add it to `format.ts` beside `formatDateTime` if it is not there.
- Until Review AI Findings merges, build against `docs/examples/relationships.json` with the fields added.
- **Tests:** `supplyChain.test.ts` (the edge gets the status), `SupplyChainGraph.test.tsx` (the dashed style is set; the panel line for each status), the legend entries.
