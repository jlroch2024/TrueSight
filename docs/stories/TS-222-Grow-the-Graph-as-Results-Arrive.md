# TS-222 Grow the Graph as Results Arrive

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 30 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 4 |
| Points | 3 |
| Labels | s4-new, sprint-4 |

## User Story
As a portfolio manager, I want each holding's suppliers and each new alert to appear the moment TrueSight finds them,
with the graph moving smoothly as it grows, so that Analyse and Check News feel quick, and I can start reading while
the rest is still coming.

## What We Agreed

- **Asked for by the product owner on 30 October 2026.** The graph gains its suppliers and customers only when the
  **last** holding is Done (`WorkspacePage.tsx`, the effect after "Analysis and news, followed until they finish"), so
  one slow report keeps it empty; alerts are loaded only when the whole news check ends.
- **Results show as they arrive; there is no progress bar.** The graph grows holding by holding, and new alerts appear
  while news is still being checked. That is the progress.
- **The graph must stay smooth while it grows.** Reproduced on 30 October 2026 on the demo portfolio: while Analyse
  runs, the page asks for the holdings every 1.5 s, and every answer rebuilds the graph's scene, even when nothing has
  changed (4 rebuilds in 6 s). Each rebuild sets every company's speed and the physics to zero, so whatever was moving
  (a newcomer settling, the web following a drag) stops dead. Without the checks, the same motion eased out over about
  3 s. Showing results as they arrive would make this happen to every newcomer, so this story fixes it first:
  - the graph is rebuilt only when something on it changed (a company, a link, a status ring, a colour, a
    disruption);
  - a graph carried on to new data keeps its motion, and only newcomers add energy.
- **This story is the website only.** Shorter Waits for Analyse and Check News (its own story) makes the backend
  finish holdings sooner and build alerts round by round; this story shows whatever has arrived, so either can merge
  first.

## Acceptance Criteria

1. While other holdings are still Analysing, a holding's suppliers and customers join the graph within one check
   (1.5 s) of it turning Done.
1. A check that brings back the same holdings, relationships and alerts does not rebuild the graph.
1. When companies join or leave the graph, the companies already moving keep their speed: the graph never stops dead
   or jumps while Analyse or Check News runs, and a company being dragged keeps following the pointer.
1. While news is being checked, new alerts appear in the Alerts tab and the Open Alerts count without waiting for the
   check to finish.

## Technical Notes

- **The graph rebuilds only on change:** in `frontend/src/graph/LivingGraph.tsx`, `graph` (`useMemo` over `nodes`
  and `edges`, about line 280) is built only when their content changes, not their identity: compare a signature of
  what is drawn (each node's id, size, ring, colour and logo; each edge's ends, type, review status and criticality)
  with the last one, and keep the last `graph` when it is equal. `view.ts` still recomputes freely.
- **A carried graph keeps its motion:** in the scene effect (about line 337), a carried graph no longer calls
  `restoreSettled`, which zeroes every speed and the alpha (`physics.ts`, line 224); and `Scene.carryFrom`
  (`scene.ts`, about line 151) carries each company's `vx` and `vy` with its position instead of setting them to 0.
  The alpha carries on from the old physics, raised to 0.3 only when companies arrived (0.12 when they left), never
  lowered. A dragged company stays dragged across a rebuild.
- **The workspace reloads what changed:** in `pages/WorkspacePage.tsx`, while `analysing`, call `loadRelationships`
  each time the number of Done holdings goes up, as well as at the end. While `checking`, each poll also reloads the
  alerts (`alertApi.list`), as the end of a check does today; the graph is untouched when nothing new came (criterion
  2).
- **No backend change.**
- **Tests:**
  - `LivingGraph.test.tsx`: rendering again with equal but new `nodes` and `edges` keeps the same scene (criterion 2).
  - `physics.test.ts`: a carried graph keeps each company's speed and the alpha, and adds energy only for newcomers
    (criterion 3).
  - `WorkspacePage.test.tsx`, with the fake `fetch`: relationships are loaded when the Done count rises, and alerts
    while news is checked (criteria 1 and 4).
  - By hand: drag a company while Analyse runs; it keeps following the pointer, and the web never freezes.
