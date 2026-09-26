// Draws the supply chain as an interactive graph: Cytoscape.js, laid out with cytoscape-cola, kept running so
// connected companies keep pulling together while the user drags one, and shared suppliers stand out.
//
// A visitor whose computer is set to reduce motion instead gets a layout computed once, still: cola never runs.
import cytoscape, { type ElementDefinition, type LayoutOptions } from 'cytoscape';
import cola from 'cytoscape-cola';
import { useEffect, useRef } from 'react';
import type { GraphEdge, GraphNode } from '../pages/supplyChain';

cytoscape.use(cola);

// Matches styles.css: Cytoscape draws to a <canvas>, so it cannot read the CSS custom properties there.
const ACCENT = '#5b8cff';
const MUTED = '#8a94a3';
const TEXT = '#e6e8eb';
const BORDER = '#262c36';

type Props = {
  nodes: GraphNode[];
  edges: GraphEdge[];
  onEdgeSelect: (relationshipId: number) => void;
};

export function SupplyChainGraph({ nodes, edges, onEdgeSelect }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
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
      layout: { name: 'preset' }, // the real layout is chosen and run just below
      wheelSensitivity: 0.2,
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
        {
          selector: 'edge',
          style: {
            width: 1.5,
            'line-color': BORDER,
            'target-arrow-color': BORDER,
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

    const reducesMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    const layout = cy.layout(
      (reducesMotion
        ? { name: 'breadthfirst', animate: false, fit: true, spacingFactor: 1.4 }
        : { name: 'cola', infinite: true, fit: false, randomize: false, avoidOverlap: true }) as LayoutOptions,
    );
    layout.run();

    cy.on('tap', 'edge', (event) => {
      cy.edges().unselect();
      event.target.select();
      onEdgeSelectRef.current(event.target.data('relationshipId') as number);
    });

    return () => {
      cy.destroy();
    };
    // onEdgeSelect is deliberately left out: it is read through the ref above, so that selecting an edge (which
    // changes the page's state, and so gives onEdgeSelect a new identity) does not recreate the whole graph and
    // throw away its pan, zoom and node positions.
  }, [nodes, edges]);

  return <div ref={containerRef} className="supply-chain-graph" role="application" aria-label="Supply chain graph" />;
}
