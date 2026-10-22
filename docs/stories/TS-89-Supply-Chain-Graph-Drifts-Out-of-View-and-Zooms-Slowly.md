# TS-89 Supply Chain Graph Drifts Out of View and Zooms Slowly

| | |
|---|---|
| Type | Bug |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 1 |
| Assignee | Jeremy Roch |

## What Happens
With real data, companies drift off the edges of the graph and nothing brings them back. Zooming is five times slower than normal. The arrows are barely visible.

## Why
The view was fitted only once, before the physics layout moved the companies. Zoom was slowed with `wheelSensitivity: 0.2`, and arrows were drawn in the border colour. Breaks See the Supply Chain as a Graph, AC6.

## Acceptance Criteria

- After the layout settles, every company is on screen.
- Scrolling zooms at the normal speed, between 0.3× and 3×.
- Fit to Screen brings every company back into view.
- Dragging still pulls connected companies together.
- The arrows are clearly visible.

## Technical Notes

- Two cola phases: settle while fitting the view, then keep the physics running without moving it, with one refit 1.5 seconds later unless the user has moved.
- Only layout and zoom change; the graph's new look is a Sprint 2 story.
