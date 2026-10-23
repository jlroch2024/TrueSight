# TS-100 Graph Legend and Arrow Hover

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 2 |
| Points | 2 |
| Assignee | Ben Aw Yong |

## User Story
As a portfolio manager, I want a key to the graph, and to see what an arrow means by hovering over it, so that I don't have to click every arrow.

## What We Agreed

- A small key in the graph's corner.
- Hovering over an arrow highlights it, and shows one sentence next to the pointer.

## Acceptance Criteria

- The key explains holdings, suppliers and customers, shared companies, and the flow.
- Hovering over an arrow highlights it and shows e.g. "Taiwan Semiconductor Manufacturing supplies NVIDIA: semiconductor wafers".
- Clicking an arrow still shows its evidence.

## Technical Notes

- `GraphLegend.tsx` (tested), and `relationshipSummary` in `supplyChain.ts` (tested).

---
