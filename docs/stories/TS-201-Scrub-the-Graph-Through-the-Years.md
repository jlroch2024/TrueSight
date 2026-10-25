# TS-201 Scrub the Graph Through the Years

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 23 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 4 |
| Points | 5 |
| Labels | s4-new, sprint-4 |
| Assignee | Jeremy Roch |

## User Story

As a portfolio manager, I want to scrub the supply chain graph back through the years, so that I can watch suppliers and customers appear and disappear and see where new dependencies came from.

## What We Agreed

- The product owner's request (20 October 2026), as in the Antigravity prototype's timeline: scrolling back, suppliers not mentioned then disappear; scrolling forward, they appear with a colour or glow.
- **The top bar's timeline becomes a scrubber:** a stop for each year of the history, then **Today**. Drag it, click a stop, or step with the arrow keys. It opens at Today.
- **The graph shows the chosen year** as SEC Filing Timeline gives it: each holding's relationships from its latest report filed by the end of that year.
- **Going back,** companies and arrows not named that year fade out and disappear; ones named then but dropped since fade back in.
- **Going forward,** what is new that year **glows lime**, with a "New in 2025" tag on the company, for as long as that year is shown. What is dropped fades out with a brief amber fade. The legend gains "New This Year" while a past year is shown.
- **Nothing jumps.** The layout is worked out once over every company and arrow of every year, so scrubbing only shows and hides: each company keeps its place.
- **The holdings always stay,** as they are today's portfolio.
- **An older arrow,** when chosen, shows that year's evidence in the Inspector, headed "From the 2024 Annual Report", with Show in Report opening that year's filing.
- **Looking, not acting:** alerts, review and What If It Stops? use Today only. While a past year is shown, What If It Stops? says "Return to Today to run a scenario."
- With reduced motion turned on in the system, years change without fades or glow animation (the lime outline stays).
- Before Load History has run, the scrubber shows only Today, with Load History beside it.

## Acceptance Criteria

1. The scrubber shows a stop for each year of the history and Today, and opens at Today.
1. Choosing a year shows exactly that year's relationships, and the companies they name, plus every holding.
1. Stepping back fades out what that year did not name; stepping forward fades in what is new, which glows lime with its "New in" tag while the year is shown.
1. No company moves when the year changes.
1. Choosing an older arrow shows that year's evidence and opens that year's filing.
1. What If It Stops? only runs at Today.
1. With reduced motion, years change without animation.
1. The arrow keys step through the years when the scrubber has the focus.

## Technical Notes

- **Data:** `GET /api/portfolios/{id}/history` from SEC Filing Timeline. Today is today's relationships, as now.
- **Layout:** the flow layout (A Flowing Supply Chain Graph) runs once over the union of every year's companies and relationships (by `key`); scrubbing only changes which elements are visible. Keep the remembered positions per portfolio as they are.
- **Showing and hiding:** in `SupplyChainGraph.tsx`, classes per element for the chosen year (`.absent`, `.new`, `.gone`), with opacity transitions about 300 ms. The glow is Cytoscape's underlay (`underlay-color` lime, `underlay-opacity`, `underlay-padding`), on nodes and edges.
- **Scrubber:** a component in the top bar with `role="slider"`, `aria-valuetext` "2025", and arrow-key handling; no browser-default range input.
- **Tests:** `SupplyChainGraph.test.tsx` (the visible set per year, the `.new` class, positions unchanged across years); the scrubber's keyboard and labels; the Inspector's past evidence; What If It Stops? at a past year.
