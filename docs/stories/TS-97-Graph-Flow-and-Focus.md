# TS-97 Graph Flow and Focus

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 2 |
| Points | 3 |
| Assignee | isaiah |

## User Story
As a portfolio manager, I want to see which way goods flow, and focus on one company's connections, so that a busy graph stays readable.

## What We Agreed

- Arrows are dashes moving from supplier to customer, and still lines when the computer is set to reduce motion.
- Hovering over a company (or tapping it, on a touch screen) keeps it, its direct suppliers and customers, and the arrows between them lit, and fades the rest.

## Acceptance Criteria

- The dashes move from supplier to customer.
- Hovering over NVIDIA leaves only NVIDIA, TSMC and Samsung, and their two arrows, lit.
- Moving off a company, or tapping the background, brings everything back.

## Technical Notes

- `focusOn(nodeId, edges)` in `supplyChain.ts`, tested.
- The dash direction was checked by drawing one arrow at two dash positions.

---

h4.
