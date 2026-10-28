# TS-171 A Flowing Supply Chain Graph

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 3 |
| Points | 8 |
| Labels | s2-fix, sprint-3 |
| Assignee | Jeremy Roch |

## User Story
As a portfolio manager, I want the supply chain graph to open as a clear, living network I can read straight away,
with suppliers mostly to the left of their customers, every name readable and the disruptions that touch my holdings
marked on their links, so that I can see at a glance where my holdings depend on others and where that is at risk.

## What We Agreed
*Rewritten on 23 October 2026: the product owner replaced the Cytoscape flow with a graph built from scratch, the*
*"living graph", on the branch* `experiment/living-graph`. The items below are what it does.

- **A living web, not columns (item 31).** Companies settle into a network (d3-force: springs along links, repulsion,
collision of tile-and-label boxes) with a gentle pull that keeps suppliers mostly to the left of their customers. The
strict left-to-right flow was tried and dropped: with many holdings it becomes a tall column, while the graph's space,
between the two drawers, is roughly square.
- **A first view needing nothing changed.** The graph settles out of sight for the zoom it will open at, so every label
has room at that zoom, and no link runs behind a company it does not join (item 55: Contemporary Amperex's link to
Tesla no longer runs through Panasonic).
- **Alive.** Companies bloom in from left to right as a line of light sweeps across, then float gently. Pointing at a
company lights it and its neighbours and fades the rest; a hover card above the pointer names it (item 39 for links).
- **Dragging (items 31, 33).** A dragged company's neighbours follow on springs, others drift aside as if in a
suspension, and the dropped company stays where it was put, held by a soft anchor and marked with a lime pin on its
corner. Reset Layout releases every placed company.
- **Links.** Straight, in each holding's chain colour (the seven largest holdings by weight; the rest grey), drawn as a
conveyor: a tapered rail with segments through which waves of light run towards the customer, ending in a chevron
that flashes as each wave arrives. A link not yet reviewed is fainter.
- **Disruptions on the graph.** A link an open alert runs through takes the disruption's colour (High red, Medium
orange, Low yellow), its conveyor stalls and pulses, and a diamond marks its middle; its hover card names the event.
This needs one backend addition: each alert's reached holding also gives the relationship's id.
- **Holdings stand out by colour, not size.** A holding's coloured outline and chamfer mark it, so its tile is only a
little bigger than a supplier's, and the other logos and the shared-holdings marker stay readable.
- **Zoom as drill-down.** Zooming out far enough that suppliers and customers recede, holdings shrink at half the
graph's rate and never touch, and the others' labels fade before the holdings'. Zooming in brings them back.
- **Logos, normalised (item 40).** Each logo's built-in margin is trimmed; a logo with its own background fills its
tile, a light one sits on a dark tile, any other on a white tile at one size. A company Logo.dev does not know shows
its initials on a gradient of its own. Companies without a ticker get a logo only when Logo.dev knows a brand of
exactly that name, so no wrong logo is ever shown.
- **Navigation.** Smooth zoom around the pointer, a pan that drifts on, double-click to fly to a company, search (`/`),
fit (`0`), full screen (`F`), and a minimap. A compact key names each holding's colour.
- **Remembered for the tab (item 62).** Placed companies, the zoom and the pan are kept per portfolio in
`sessionStorage` while the tab is open.
- **The look.** Square and chamfered shapes, glass controls, the Geist typeface, registration marks, hatching on faded
companies, ports where links meet tiles, and a dot grid with crosses. Separate from the rest of the app, which One
Workspace (TS-178) will redesign around it.
- **Libraries this story names:** `d3-force` and `d3-quadtree` (physics), `motion` (control animation), `lucide-react`
(icons), `@fontsource-variable/geist` (typeface). Cytoscape is removed.

## Acceptance Criteria

1. On the demo portfolio, the first view has every company's name readable and no link running behind a company it
does not join; most links run left to right.
1. Pointing at a company lights it and its direct neighbours and fades the rest; its hover card appears above the
pointer, beside it, without sliding in.
1. Dragging a company moves its neighbours smoothly; let go, it stays where it was put and shows the lime pin; Reset
Layout releases it.
1. Links move steadily from supplier to customer, at the same pace whatever the zoom or pointer.
1. An open alert that reaches a holding through a relationship marks that link in its severity's colour, with a stalled
conveyor and a diamond; a dismissed alert marks nothing.
1. Zooming out, suppliers' and customers' labels fade before holdings', and none is left at the furthest zoom; holdings
stay larger than other companies and never overlap.
1. A logo that fills its own square (AMD's) fills its tile; a company without a ticker gets a logo only when Logo.dev
knows a brand of exactly that name, and otherwise shows its initials.
1. After placing a company and zooming, opening another page and coming back shows the graph as it was left.
1. With reduced motion turned on, nothing animates by itself.

## Technical Notes

- **Where:** `frontend/src/graph/` (`LivingGraph.tsx`, `model.ts`, `physics.ts`, `scene.ts`, `camera.ts`,
`geometry.ts`, `logos.ts`, `render.ts`, `livingGraph.css`); `SupplyChainPage.tsx` uses `LivingGraph` with the same
props as before plus `risks`. The old `SupplyChainGraph.tsx` and `GraphLegend.tsx` are removed.
- **Backend:** `AlertResponse.ReachedHolding` gains `relationshipId` (null when the event is about the holding itself);
`AlertsIT` checks it. No table change.
- **Logos:** `LogoDomainClient.domainOf` accepts a brand only when its name matches the company's, with unit words and
legal endings removed, and refuses domains made up from the name run together.
- **Tests:** `src/graph/*.test.ts(x)` cover the model, the first view (no hidden link, no label clash at the opening
zoom), anchors, the drill-down, the camera, geometry, logo treatment, disruptions and the controls; `AlertsIT` the new
field; `LogoDomainClientTest` the logo rule with real Logo.dev answers.
