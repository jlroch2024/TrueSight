# TS-54 See the Supply Chain as a Graph

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 1 |
| Points | 5 |
| Labels | sprint-1 |
| Assignee | Ben Aw Yong |
| Blocked By | [TS-85 Fix Issue with Displaying Supply Chain as a Graph](TS-85-Fix-Issue-with-Displaying-Supply-Chain-as-a-Graph.md) |

## User Story
As a portfolio manager, I want to see my holdings and their suppliers and customers on a graph, so that I understand how they connect at a glance.

## What We Agreed

- Each company is a node and each relationship is an edge, with the arrow pointing from supplier to customer.
- Holdings look different from other companies, in shape as well as colour.
- The graph uses a physics layout, so connected companies pull together and shared suppliers stand out.
- Clicking an edge shows its evidence, where it came from, and a Show in Report link.

## Acceptance Criteria

- The Supply Chain page shows the open portfolio's holdings and relationships as a graph.
- Holdings can be told apart from other companies.
- A supplier shared by several holdings, such as TSMC, appears once, with an edge to each of them.
- Clicking an edge shows its evidence, the report it came from and that report's date, and a Show in Report link.
- Show in Report opens the annual report on the SEC website, scrolled to the evidence.
- The graph can be zoomed, moved and rearranged by dragging companies, and the layout settles by itself.
- With no relationships yet, the page says so and explains how to run an analysis.

## Technical Notes

- Data: `GET /api/portfolios/{id}/relationships`. If that address does not exist yet, read `docs/examples/relationships.json`, which has the same shape.
- Use Cytoscape.js with the cytoscape-cola layout, kept running so the physics stays live while dragging. When the computer is set to reduce motion, use a still layout instead.
- Show in Report link: the report's link, then `#:~:text=`, then the first 5 words of the evidence, a comma, and the last 5 words. Encode each part for a web address, and write each hyphen as `%2D`. If the evidence is 10 words or fewer, use it whole. Chrome and Edge scroll to and highlight it.
- The page is at /portfolios/:portfolioId/supply-chain, and is added to PORTFOLIO_NAV_ITEMS in navigation.ts.
Holdings come from GET /api/portfolios/{id}/holdings. Until that exists, treat each relationship's company as a holding.

**Needs First:** Manage My Portfolios
