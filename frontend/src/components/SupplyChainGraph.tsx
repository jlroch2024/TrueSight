// Draws the supply chain as an interactive graph: Cytoscape.js, laid out with cytoscape-cola.
//
// Layout: first the companies settle while the view fits them all; then the physics keeps running, so connected
// companies keep pulling together while the user drags one, and shared suppliers stand out. The view is fitted once
// more shortly after, unless the user has already zoomed, panned or dragged, and Fit to Screen brings everything back
// into view at any time. Scrolling zooms at the normal speed, within limits.
//
// A visitor whose computer is set to reduce motion instead gets a layout computed once, still: cola never runs.
import cytoscape, { type Core, type ElementDefinition, type Layouts, type LayoutOptions } from 'cytoscape';
import cola from 'cytoscape-cola';
import { useEffect, useRef } from 'react';
import type { GraphEdge, GraphNode } from '../pages/supplyChain';

cytoscape.use(cola);

// Matches styles.css: Cytoscape draws to a <canvas>, so it cannot read the CSS custom properties there.
const ACCENT = '#5b8cff';
const MUTED = '#8a94a3';
const TEXT = '#e6e8eb';
const BORDER = '#262c36';

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
};

export function SupplyChainGraph({ nodes, edges, onEdgeSelect }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const cyRef = useRef<Core | null>(null);
  const onEdgeSelectRef = useRef(onEdgeSelect);
  onEdgeSelectRef.current = onEdgeSelect;

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const elements: ElementDefinition[] = [
      ...nodes.map((node) => ({
        data: { id: node.id, label: node.ticker ?? node.name, holding: node.isHolding ? 1 : 0 },
      })),
      ...edges.map((edge) => ({
        data: { id: edge.id, source: edge.source, target: edge.target, relationshipId: edge.relationshipId },
      })),
    ];

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
            label: 'data(label)',
            color: TEXT,
            'font-size': 11,
            'text-valign': 'center',
            'text-halign': 'center',
            'text-wrap': 'wrap',
            'text-max-width': '70px',
            'background-color': MUTED,
            shape: 'ellipse',
            width: 46,
            height: 46,
            'border-width': 1,
            'border-color': BORDER,
          },
        },
        // Holdings look different in shape as well as colour, so they stand out from suppliers and customers.
        {
          selector: 'node[holding = 1]',
          style: { 'background-color': ACCENT, shape: 'round-rectangle', width: 60, height: 46 },
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
          },
        },
        {
          selector: 'edge:selected',
          style: { width: 3, 'line-color': ACCENT, 'target-arrow-color': ACCENT },
        },
      ],
    });
    cyRef.current = cy;

    const reducesMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
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

    cy.on('tap', 'edge', (event) => {
      cy.edges().unselect();
      event.target.select();
      onEdgeSelectRef.current(event.target.data('relationshipId') as number);
    });

    return () => {
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
  }, [nodes, edges]);

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
