# TS-99 Shared Supplier Highlight

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 2 |
| Points | 3 |
| Assignee | isaiah |

## User Story
As a portfolio manager, I want to see which suppliers and customers several of my holdings depend on, so that I can spot where one company's trouble would hit several positions at once.

## What We Agreed

- A company that isn't itself a holding, and supplies two or more holdings or buys from two or more, is drawn amber and bigger, labelled e.g. "Supplies 4 holdings".
- Under the graph, a list of these companies, most shared first.

## Acceptance Criteria

- With the sample data, TSMC shows "Supplies 4 holdings" and Apple "Buys from 2 holdings".
- A company serving only one holding isn't listed, and holdings are never listed.
- Holdings are counted, not arrows.

## Technical Notes

- `sharedCompanies` and `sharedSummary` in `supplyChain.ts`, tested with the real sample relationships.

---

h4.
