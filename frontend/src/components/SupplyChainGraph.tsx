// Draws the supply chain as an interactive graph: Cytoscape.js, laid out with cytoscape-cola.
//
// Each company is a square tile with its logo from Logo.dev, or its first letter while the logo loads, when Logo.dev
// does not know the company, or when there is no Logo.dev token. Its label sits underneath (a holding's ticker and
// weight, otherwise its ticker or a short name), on a patch of the panel's colour, so it stays readable where an arrow
// passes behind it. Holdings have a teal border, and are drawn bigger the bigger their weight (holdingSize).
//
// Layout: first the companies settle while the view fits them all; then the physics keeps running, so connected
// companies keep pulling together while the user drags one, and shared suppliers stand out. The view is fitted once
// more shortly after, unless the user has already zoomed, panned or dragged, and Fit to Screen brings everything back
// into view at any time. Scrolling zooms at the normal speed, within limits.
//
// A supplier or customer that several holdings depend on (see sharedCompanies) is drawn bigger, with an amber border,
// and its label says what it supplies or buys, e.g. "Supplies 4 holdings".
//
// A visitor whose computer is set to reduce motion instead gets a layout computed once, still: cola never runs, and
// the moving flow markers are hidden (the arrowheads still show which way goods flow).
//
// A small marker travels from supplier to customer over each arrow. The arrow's solid or dashed base line shows its
// review status. Pointing at a company (hovering, or tapping it on a touch screen) keeps it, its direct suppliers and
// customers, and the arrows between them lit, and fades the rest; moving off the company, or tapping the background,
// brings everything back.
import cytoscape, { type Core, type ElementDefinition, type Layouts, type LayoutOptions } from 'cytoscape';
import cola from 'cytoscape-cola';
import { useEffect, useRef } from 'react';
import {
  flowProgressAt,
  focusOn,
  holdingSize,
  type GraphEdge,
  type GraphNode,
  logoUrl,
  nodeLabel,
  plainName,
  TILE_SIZE,
} from '../pages/supplyChain';

cytoscape.use(cola);

// Matches styles.css: Cytoscape draws to a <canvas>, so it cannot read the CSS custom properties there.
const ACCENT = '#2dd4bf';
const LIME = '#c4f36a';
const AMBER = '#f5b73b';
const MUTED = '#8ba9a5';
const TEXT = '#e4f3ef';
const BORDER = '#25434b';
const PANEL = '#0b2028';
const PANEL_RAISED = '#102a33';

// The teal border that marks a holding. Tile sizes live in supplyChain.ts, beside holdingSize.
const HOLDING_BORDER_WIDTH = 3;
// A supplier or customer several holdings depend on: a bigger tile with an amber border, so it stands out.
const SHARED_TILE_SIZE = 58;

/**
 * A company's first letter on a tile, as a picture Cytoscape can draw where the logo would be. A canvas cannot use the
 * page's web fonts inside a picture, so it names the usual system fonts.
 */
function letterTile(name: string): string {
  const letter = (plainName(name).match(/[A-Za-z0-9]/)?.[0] ?? '?').toUpperCase();
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">` +
    `<rect width="64" height="64" fill="${PANEL_RAISED}"/>` +
    `<text x="32" y="33" text-anchor="middle" dominant-baseline="central" fill="${TEXT}" ` +
    `font-family="system-ui, -apple-system, Segoe UI, Helvetica, Arial, sans-serif" font-size="30" font-weight="600">` +
    `${letter}</text></svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

// How faded the companies and arrows outside the focus are.
const FADED_OPACITY = 0.15;

// How far out and in the user can zoom: far enough to see a big portfolio whole, close enough to read any label.
const MIN_ZOOM = 0.3;
const MAX_ZOOM = 3;
const PADDING = 40;

// cola's settings for both phases: keep companies apart, and lay out unconnected groups side by side.
const COLA = { avoidOverlap: true, handleDisconnected: true };

// The live physics keeps pulling companies together after the first fit, so the view is fitted once more, shortly
// after, unless the user has already zoomed, panned or dragged.
const REFIT_AFTER_MS = 1500;

type Props = {
  nodes: GraphNode[];
  edges: GraphEdge[];
  onEdgeSelect: (relationshipId: number) => void;
  // A company clicked on the graph, or null when the background is clicked.
  onNodeSelect?: (nodeId: string | null) => void;
  // The company chosen on the page (from the graph or a list beside it): kept in focus and centred while set.
  selectedNodeId?: string | null;
  // The relationship chosen on the page: its arrow is drawn selected.
  selectedRelationshipId?: number | null;
  // The Logo.dev token from GET /api/settings. Empty when none is set: every company then shows its letter.
  logoToken?: string;
};

export function SupplyChainGraph({
  nodes,
  edges,
  onEdgeSelect,
  onNodeSelect,
  selectedNodeId = null,
  selectedRelationshipId = null,
  logoToken = '',
}: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const flowLayerRef = useRef<SVGSVGElement | null>(null);
  const cyRef = useRef<Core | null>(null);
  const onEdgeSelectRef = useRef(onEdgeSelect);
  onEdgeSelectRef.current = onEdgeSelect;
  const onNodeSelectRef = useRef(onNodeSelect);
  onNodeSelectRef.current = onNodeSelect;
  // Read by the graph's own handlers: pointing away from a company goes back to the chosen one, not to nothing.
  const selectedNodeRef = useRef(selectedNodeId);
  selectedNodeRef.current = selectedNodeId;

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const elements: ElementDefinition[] = [
      ...nodes.map((node) => ({
        data: {
          id: node.id,
          // A shared company carries what it supplies or buys under its label, e.g. "TSM" then "Supplies 4 holdings".
          label: node.sharedSummary ? `${nodeLabel(node)}\n${node.sharedSummary}` : nodeLabel(node),
          // The letter first; swapped for the logo once it has loaded (below).
          image: letterTile(node.name),
          holding: node.isHolding ? 1 : 0,
          // A holding grows with its weight; other companies keep the usual size (shared ones are made bigger below).
          size: node.isHolding ? holdingSize(node.weight) : TILE_SIZE,
          shared: node.sharedSummary ? 1 : 0,
          ticker: node.ticker ? 1 : 0,
        },
      })),
      ...edges.map((edge) => ({
        data: {
          id: edge.id,
          source: edge.source,
          target: edge.target,
          relationshipId: edge.relationshipId,
          reviewStatus: edge.reviewStatus,
        },
      })),
    ];

    const reducesMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

    const cy = cytoscape({
      container,
      elements,
      // Start from a circle that fits the box; the layouts below move the companies from there.
      layout: { name: 'circle', fit: true, padding: PADDING },
      minZoom: MIN_ZOOM,
      maxZoom: MAX_ZOOM,
      style: [
        {
          selector: 'node',
          style: {
            shape: 'rectangle',
            width: 'data(size)',
            height: 'data(size)',
            'background-color': PANEL_RAISED,
            'background-image': 'data(image)',
            'background-fit': 'contain',
            'border-width': 1,
            'border-color': BORDER,
            // The label sits under the tile, on a patch of the panel's colour that hides any arrow behind it.
            label: 'data(label)',
            color: TEXT,
            'font-size': 11,
            'text-valign': 'bottom',
            'text-halign': 'center',
            'text-margin-y': 5,
            'text-wrap': 'none',
            'text-background-color': PANEL,
            'text-background-opacity': 1,
            'text-background-padding': '2px',
            'text-background-shape': 'rectangle',
          },
        },
        // Holdings have a teal border, so they stand out from suppliers and customers.
        {
          selector: 'node[holding = 1]',
          style: { 'border-width': HOLDING_BORDER_WIDTH, 'border-color': ACCENT },
        },
        // Shared suppliers and customers: amber-bordered, bigger, and their label wraps onto a second line.
        {
          selector: 'node[shared = 1]',
          style: {
            width: SHARED_TILE_SIZE,
            height: SHARED_TILE_SIZE,
            'border-width': HOLDING_BORDER_WIDTH,
            'border-color': AMBER,
            'text-wrap': 'wrap',
            'text-max-width': '120px',
          },
        },
        {
          selector: 'node[ticker = 1]',
          style: { 'font-family': 'JetBrains Mono' },
        },
        // Arrows in the muted grey, which shows on the dark panel (the border colour hardly did).
        {
          selector: 'edge',
          style: {
            width: 1.5,
            'line-color': MUTED,
            'target-arrow-color': MUTED,
            'target-arrow-shape': 'triangle',
            'arrow-scale': 1,
            'curve-style': 'bezier',
            'line-style': 'solid',
          },
        },
        {
          selector: 'edge[reviewStatus = "APPROVED"]',
          style: { 'line-style': 'solid' },
        },
        {
          selector: 'edge[reviewStatus = "UNREVIEWED"]',
          style: { 'line-style': 'dashed' },
        },
        {
          selector: 'edge:selected',
          style: { width: 3, 'line-color': LIME, 'target-arrow-color': LIME },
        },
        // Last, so it wins: what is outside the focus fades.
        { selector: '.faded', style: { opacity: FADED_OPACITY } },
      ],
    });
    cyRef.current = cy;

    const running: Layouts[] = [];
    let refit: number | undefined;
    let userMoved = false;
    const noteUserMoved = () => {
      userMoved = true;
    };
    container.addEventListener('pointerdown', noteUserMoved);
    container.addEventListener('wheel', noteUserMoved, { passive: true });

    if (reducesMotion) {
      cy.layout({ name: 'breadthfirst', animate: false, fit: true, padding: PADDING, spacingFactor: 1.4 }).run();
    } else {
      // Phase 1: settle, fitting the view as the companies move. Phase 2: keep the physics running, without moving
      // the view, so the user's own zooming and panning are left alone.
      const settle = cy.layout({ name: 'cola', ...COLA, fit: true, padding: PADDING, maxSimulationTime: 2500 } as LayoutOptions);
      settle.one('layoutstop', () => {
        if (cy.destroyed()) return;
        const live = cy.layout({ name: 'cola', ...COLA, infinite: true, fit: false } as LayoutOptions);
        running.push(live);
        live.run();
        refit = window.setTimeout(() => {
          if (!userMoved && !cy.destroyed()) cy.animate({ fit: { eles: cy.elements(), padding: PADDING }, duration: 400 });
        }, REFIT_AFTER_MS);
      });
      running.push(settle);
      settle.run();
    }

    // Logos. Each is loaded first, off the graph: one that loads replaces the letter, one Logo.dev does not know
    // (fallback=404 makes it answer "not found") leaves the letter. Cytoscape loads images for "anonymous" use, which
    // Logo.dev allows, so the check loads them the same way.
    const logos: HTMLImageElement[] = [];
    for (const node of nodes) {
      const url = logoUrl(node, logoToken);
      if (!url) continue;
      const logo = new Image();
      logo.crossOrigin = 'anonymous';
      logo.onload = () => {
        if (!cy.destroyed()) cy.getElementById(node.id).data('image', url);
      };
      logo.src = url;
      logos.push(logo);
    }

    cy.on('tap', 'edge', (event) => {
      cy.edges().unselect();
      event.target.select();
      onEdgeSelectRef.current(event.target.data('relationshipId') as number);
    });

    // Focus: pointing at a company lights it and its direct connections, and fades the rest. Pointing away goes back
    // to the chosen company, if there is one. Clicking a company chooses it; clicking the background lets go.
    cy.on('mouseover', 'node', (event) => applyFocus(cy, edges, event.target.id()));
    cy.on('mouseout', 'node', () => applyFocus(cy, edges, selectedNodeRef.current));
    cy.on('tap', 'node', (event) => {
      applyFocus(cy, edges, event.target.id());
      onNodeSelectRef.current?.(event.target.id());
    });
    cy.on('tap', (event) => {
      if (event.target !== cy) return;
      applyFocus(cy, edges, null);
      cy.edges().unselect();
      onNodeSelectRef.current?.(null);
    });
    applyFocus(cy, edges, selectedNodeRef.current);

    // The teal markers carry flow direction independently from the solid/dashed review-status strokes.
    let frame: number | undefined;
    const flowLayer = flowLayerRef.current;
    const markersByEdge = new Map<string, SVGCircleElement[]>();
    flowLayer?.querySelectorAll<SVGCircleElement>('[data-flow-edge]').forEach((marker) => {
      const edgeId = marker.dataset.flowEdge;
      if (!edgeId) return;
      const markers = markersByEdge.get(edgeId) ?? [];
      markers.push(marker);
      markersByEdge.set(edgeId, markers);
    });
    if (flowLayer) flowLayer.style.visibility = reducesMotion ? 'hidden' : 'visible';
    if (!reducesMotion && edges.length > 0 && typeof window.requestAnimationFrame === 'function') {
      let startedAt: number | undefined;
      const step = (now: number) => {
        if (cy.destroyed()) return;
        startedAt ??= now;
        const progress = flowProgressAt(now - startedAt);
        cy.edges().forEach((edge) => {
          const markers = markersByEdge.get(edge.id()) ?? [];
          markers.forEach((marker, index) => {
            const markerProgress = (progress + index / Math.max(markers.length, 1)) % 1;
            const point = pointAlongEdge(edge, markerProgress);
            marker.setAttribute('cx', String(point.x));
            marker.setAttribute('cy', String(point.y));
            marker.setAttribute('opacity', String(Math.min(1, markerProgress * 8, (1 - markerProgress) * 8)));
          });
        });
        frame = window.requestAnimationFrame(step);
      };
      frame = window.requestAnimationFrame(step);
    }

    return () => {
      if (frame !== undefined) window.cancelAnimationFrame(frame);
      logos.forEach((logo) => {
        logo.onload = null;
      });
      window.clearTimeout(refit);
      container.removeEventListener('pointerdown', noteUserMoved);
      container.removeEventListener('wheel', noteUserMoved);
      running.forEach((layout) => layout.stop());
      cy.destroy();
      cyRef.current = null;
    };
    // onEdgeSelect is deliberately left out: it is read through the ref above, so that selecting an edge (which
    // changes the page's state, and so gives onEdgeSelect a new identity) does not recreate the whole graph and
    // throw away its pan, zoom and node positions.
  }, [nodes, edges, logoToken]);

  // The company chosen on the page stays in focus, and is centred when it is chosen.
  useEffect(() => {
    const cy = cyRef.current;
    if (!cy || cy.destroyed()) return;
    applyFocus(cy, edges, selectedNodeId);
    const node = selectedNodeId ? cy.getElementById(selectedNodeId) : null;
    if (node && node.nonempty()) cy.animate({ center: { eles: node }, duration: 300 });
  }, [selectedNodeId, edges]);

  // The relationship chosen on the page has its arrow drawn selected.
  useEffect(() => {
    const cy = cyRef.current;
    if (!cy || cy.destroyed()) return;
    cy.edges().unselect();
    if (selectedRelationshipId !== null) cy.edges(`[relationshipId = ${selectedRelationshipId}]`).select();
  }, [selectedRelationshipId, nodes, edges]);

  // Brings every company back into view, however far the user has zoomed, panned or dragged.
  function fitToScreen() {
    const cy = cyRef.current;
    cy?.animate({ fit: { eles: cy.elements(), padding: PADDING }, duration: 300 });
  }

  return (
    <div className="graph-frame">
      <div className="graph-viewport">
        <div ref={containerRef} className="supply-chain-graph" role="application" aria-label="Supply chain graph" />
        <svg ref={flowLayerRef} className="flow-marker-layer" aria-hidden="true" style={{ visibility: 'hidden' }}>
          {edges.flatMap((edge) =>
            [0, 1].map((markerIndex) => (
              <circle
                key={`${edge.id}-flow-${markerIndex}`}
                data-flow-edge={edge.id}
                cx="0"
                cy="0"
                r="3"
                fill={ACCENT}
                stroke={PANEL}
                strokeWidth="1.5"
              />
            )),
          )}
        </svg>
      </div>
      <div className="graph-controls">
        <button type="button" className="secondary" onClick={fitToScreen}>
          Fit to Screen
        </button>
        {edges.length > 0 && (
          <ul className="graph-legend" aria-label="Relationship review status">
            <li><span className="graph-legend-line approved" aria-hidden="true" />Approved</li>
            <li><span className="graph-legend-line unreviewed" aria-hidden="true" />Not yet reviewed</li>
          </ul>
        )}
      </div>
    </div>
  );
}

type Point = { x: number; y: number };
type EdgePath = {
  renderedSourceEndpoint: () => Point;
  renderedTargetEndpoint: () => Point;
  renderedControlPoints: () => Point[];
};

function pointAlongEdge(edge: EdgePath, progress: number): Point {
  const points = [edge.renderedSourceEndpoint(), ...edge.renderedControlPoints(), edge.renderedTargetEndpoint()];
  for (let remaining = points.length - 1; remaining > 0; remaining--) {
    for (let i = 0; i < remaining; i++) {
      points[i] = {
        x: points[i]!.x + (points[i + 1]!.x - points[i]!.x) * progress,
        y: points[i]!.y + (points[i + 1]!.y - points[i]!.y) * progress,
      };
    }
  }
  return points[0]!;
}

/** Lights one company and its direct suppliers and customers, and fades the rest; with none, lights everything. */
function applyFocus(cy: Core, edges: GraphEdge[], nodeId: string | null): void {
  if (!nodeId || cy.getElementById(nodeId).empty()) {
    cy.elements().removeClass('faded');
    return;
  }
  const { nodeIds, edgeIds } = focusOn(nodeId, edges);
  cy.nodes().forEach((node) => {
    node.toggleClass('faded', !nodeIds.has(node.id()));
  });
  cy.edges().forEach((edge) => {
    edge.toggleClass('faded', !edgeIds.has(edge.id()));
  });
}
