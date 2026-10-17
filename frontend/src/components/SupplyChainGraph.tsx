// Draws the supply chain as an interactive graph: Cytoscape.js, laid out with cytoscape-cola.
//
// Each company is a square tile with its logo from Logo.dev, or its first letter while the logo loads, when Logo.dev
// does not know the company, or when there is no Logo.dev token. Its label sits underneath (a holding's ticker and
// weight, otherwise its ticker or a short name), on a patch of the panel's colour, so it stays readable where an arrow
// passes behind it. Holdings have a teal border.
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
// the arrows are plain lines instead of moving dashes (the arrowheads still show which way goods flow).
//
// The arrows are dashes that travel from supplier to customer. Pointing at a company (hovering, or tapping it on a
// touch screen) keeps it, its direct suppliers and customers, and the arrows between them lit, and fades the rest;
// moving off the company, or tapping the background, brings everything back.
import cytoscape, { type Core, type ElementDefinition, type Layouts, type LayoutOptions } from 'cytoscape';
import cola from 'cytoscape-cola';
import { useEffect, useRef } from 'react';
import {
  DASH_PATTERN,
  dashOffsetAt,
  focusOn,
  type GraphEdge,
  type GraphNode,
  logoUrl,
  nodeLabel,
  plainName,
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

// Each company's square tile, and the teal border that marks a holding.
const TILE_SIZE = 44;
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
  // The Logo.dev token from GET /api/settings. Empty when none is set: every company then shows its letter.
  logoToken?: string;
};

export function SupplyChainGraph({ nodes, edges, onEdgeSelect, logoToken = '' }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const cyRef = useRef<Core | null>(null);
  const onEdgeSelectRef = useRef(onEdgeSelect);
  onEdgeSelectRef.current = onEdgeSelect;

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
          shared: node.sharedSummary ? 1 : 0,
          ticker: node.ticker ? 1 : 0,
        },
      })),
      ...edges.map((edge) => ({
        data: { id: edge.id, source: edge.source, target: edge.target, relationshipId: edge.relationshipId },
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
            width: TILE_SIZE,
            height: TILE_SIZE,
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
            // Dashes that travel from supplier to customer; plain lines when the visitor asked for less motion.
            'line-style': reducesMotion ? 'solid' : 'dashed',
            'line-dash-pattern': DASH_PATTERN,
          },
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

    // Focus: light one company and its direct connections, fade the rest.
    const clearFocus = () => {
      cy.nodes().removeClass('faded');
      cy.edges().removeClass('faded');
    };
    const focus = (nodeId: string) => {
      const { nodeIds, edgeIds } = focusOn(nodeId, edges);
      cy.nodes().forEach((node) => {
        node.toggleClass('faded', !nodeIds.has(node.id()));
      });
      cy.edges().forEach((edge) => {
        edge.toggleClass('faded', !edgeIds.has(edge.id()));
      });
    };
    cy.on('mouseover', 'node', (event) => focus(event.target.id()));
    cy.on('mouseout', 'node', clearFocus);
    // A touch screen has no hover: tapping a company focuses it, and tapping the background lets go.
    cy.on('tap', 'node', (event) => focus(event.target.id()));
    cy.on('tap', (event) => {
      if (event.target === cy) clearFocus();
    });

    // Moving dashes. The offset gets smaller over time, which is what carries the dashes from supplier to customer.
    let frame: number | undefined;
    if (!reducesMotion && typeof window.requestAnimationFrame === 'function') {
      let startedAt: number | undefined;
      const step = (now: number) => {
        if (cy.destroyed()) return;
        startedAt ??= now;
        cy.edges().style('line-dash-offset', dashOffsetAt(now - startedAt));
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

  // Brings every company back into view, however far the user has zoomed, panned or dragged.
  function fitToScreen() {
    const cy = cyRef.current;
    cy?.animate({ fit: { eles: cy.elements(), padding: PADDING }, duration: 300 });
  }

  return (
    <div className="graph-frame">
      <div ref={containerRef} className="supply-chain-graph" role="application" aria-label="Supply chain graph" />
      <div className="graph-controls">
        <button type="button" className="secondary" onClick={fitToScreen}>
          Fit to Screen
        </button>
      </div>
    </div>
  );
}
