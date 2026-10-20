// Draws the supply chain as an interactive graph: Cytoscape.js, laid out as a flow spread to fill the frame
// (spreadLayout).
//
// Each company is a square tile with its logo from Logo.dev, on a white square with an even margin so every logo
// looks the same size, or its first letter while the logo loads, when Logo.dev does not know the company, or when
// there is no Logo.dev token. Under each tile is a label box (HTML, drawn over the graph): the ticker in bold and, for a
// holding, its weight; then the company's name. Holdings have a teal border, and are drawn bigger the bigger their
// weight (holdingSize). A supplier or customer that several holdings depend on (see sharedCompanies) is drawn bigger,
// with an amber border, and its label box says what it supplies or buys, e.g. "Supplies 4 Holdings".
//
// Layout: a flow, worked out before the graph is drawn (spreadLayout): each company in a band by its place in the
// chain, so every arrow runs left to right from supplier to customer, spread out inside its band so the graph takes
// the frame's shape, with separate groups packed into the frame's shape too. The legend and buttons sit in a bar under
// the graph, so no company can hide behind them. The view is fitted once and the graph fades in; nothing rearranges it afterwards. Dragging a company tugs its
// direct suppliers and customers along like elastic, and they ease back when it is let go; nothing else moves.
// An arrow that would pass through another company curves around it. What the user did (the companies they moved, the
// zoom and pan) is remembered per portfolio while the browser tab is open, so leaving the page and coming back changes
// nothing; Reset Layout returns to the calculated arrangement, and Fit to Screen brings everything back into view.
// A visitor whose computer is set to reduce motion gets no easing and no moving flow markers.
//
// It reacts to the pointer: a company's border and an arrow brighten and thicken under it, smoothly. Pointing at a
// company also keeps it, its direct suppliers and customers, and the arrows between them lit, and fades the rest.
// Clicking a company chooses it: corner brackets close in around it and dashed crosshair lines run through it. Clicking
// the background lets go. Cytoscape's own grey press circle and selection overlay are switched off.
import cytoscape, { type Core, type ElementDefinition, type NodeSingular } from 'cytoscape';
import { useEffect, useRef, useState } from 'react';
import { GraphLegend } from './GraphLegend';
import {
  type Box,
  detours,
  displayName,
  easeTowards,
  flowProgressAt,
  focusOn,
  followTarget,
  forgetLayout,
  formatWeight,
  holdingSize,
  type GraphEdge,
  type GraphNode,
  logoUrl,
  nodeLabel,
  plainName,
  type Point,
  readSavedLayout,
  saveLayout,
  spreadLayout,
  TILE_SIZE,
} from '../pages/supplyChain';


// Matches styles.css: Cytoscape draws to a <canvas>, so it cannot read the CSS custom properties there.
const ACCENT = '#2dd4bf';
const LIME = '#c4f36a';
const AMBER = '#f5b73b';
const MUTED = '#8ba9a5';
const TEXT = '#e4f3ef';
const BORDER = '#25434b';
const PANEL_RAISED = '#102a33';
const WHITE = '#ffffff';
const PANEL = '#0b2028';

// The teal border that marks a holding. Tile sizes live in supplyChain.ts, beside holdingSize.
const HOLDING_BORDER_WIDTH = 3;
// A supplier or customer several holdings depend on: a bigger tile with an amber border, so it stands out.
const SHARED_TILE_SIZE = 58;
// How much of its tile a logo fills, centred on a white square, so every logo has the same margin.
const LOGO_SHARE = '72%';
// How long, in seconds, a border or an arrow takes to change under the pointer.
const HOVER_TRANSITION = 0.15;

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
const PADDING = 48;

// The label boxes grow and shrink with the zoom, so each fits the space the layout kept for it, but never below 75%,
// where the text would be too small to read; on a small screen, zoomed far out, neighbouring boxes may then touch.
const LABEL_SCALE_MIN = 0.75;
// How far outside its tile the chosen company's corner brackets sit.
const BRACKET_GAP = 7;
// Approximate room for the arrow sentence, used to put it on the other side of the pointer near an edge.
const TOOLTIP_WIDTH = 320;
const TOOLTIP_HEIGHT = 90;
// How long after the user stops zooming or panning the view is remembered, and how long after a company is let go the
// arrows are checked again (its neighbours have eased back by then).
const REMEMBER_DELAY_MS = 500;
const DETOUR_DELAY_MS = 600;

// A label box's height for the layout: each line, plus its padding and the gap under the tile.
const LABEL_LINE = 14;
const LABEL_EXTRA = 14;
// For guessing a label box's width before it is drawn: about how wide a letter is, the box's padding and border, and
// the widest it gets (see .graph-label).
const LABEL_CHAR = 6.5;
const LABEL_PADDING = 16;
const LABEL_MAX_WIDTH = 170;
// The frame's width ÷ height, when the frame has no size yet.
const DEFAULT_FRAME_RATIO = 1.8;

type Props = {
  nodes: GraphNode[];
  edges: GraphEdge[];
  onEdgeSelect: (relationshipId: number) => void;
  // A company clicked on the graph, or null when the background is clicked.
  onNodeSelect?: (nodeId: string | null) => void;
  // The company chosen on the page (from the graph or a list beside it): kept in focus and centred while set.
  selectedNodeId?: string | null;
  // The relationship chosen on the page: its arrow is drawn chosen.
  selectedRelationshipId?: number | null;
  // The Logo.dev token from GET /api/settings. Empty when none is set: every company then shows its letter.
  logoToken?: string;
  // Company name to website domain, for companies without a ticker (from GET /api/logos/domains). Logo.dev knows
  // companies by domain far better than by name.
  logoDomains?: Record<string, string>;
  // Where this portfolio's moved companies, zoom and pan are kept for the browser tab (sessionStorage), e.g.
  // "truesight.graph.7". Without one, nothing is remembered.
  layoutKey?: string;
};

type EdgeTooltip = { text: string; x: number; y: number; flipX: boolean; flipY: boolean };

export function SupplyChainGraph({
  nodes,
  edges,
  onEdgeSelect,
  onNodeSelect,
  selectedNodeId = null,
  selectedRelationshipId = null,
  logoToken = '',
  logoDomains,
  layoutKey,
}: Props) {
  const [edgeTooltip, setEdgeTooltip] = useState<EdgeTooltip | null>(null);
  const frameRef = useRef<HTMLDivElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const flowLayerRef = useRef<SVGSVGElement | null>(null);
  const cyRef = useRef<Core | null>(null);
  const labelRefs = useRef(new Map<string, HTMLDivElement>());
  const crosshairRef = useRef<SVGSVGElement | null>(null);
  const bracketsRef = useRef<HTMLDivElement | null>(null);
  // Asks for the label boxes, brackets and crosshair to be put where the graph now draws its companies.
  const placeOverlayRef = useRef<() => void>(() => {});
  const onEdgeSelectRef = useRef(onEdgeSelect);
  onEdgeSelectRef.current = onEdgeSelect;
  const onNodeSelectRef = useRef(onNodeSelect);
  onNodeSelectRef.current = onNodeSelect;
  // Read by the graph's own handlers: pointing away from a company goes back to the chosen one, not to nothing.
  const selectedNodeRef = useRef(selectedNodeId);
  selectedNodeRef.current = selectedNodeId;
  // Read by the arrow's hover sentence: the chosen arrow's sentence is already in the Inspector.
  const selectedRelationshipRef = useRef(selectedRelationshipId);
  selectedRelationshipRef.current = selectedRelationshipId;
  // Set by the graph once it is drawn: puts every company back where the layout placed it.
  const resetLayoutRef = useRef<() => void>(() => {});

  useEffect(() => {
    const container = containerRef.current;
    const frame = frameRef.current;
    if (!container || !frame) return;
    setEdgeTooltip(null);
    const edgeById = new Map(edges.map((edge) => [edge.id, edge]));

    // Where every company goes, worked out before anything is drawn, so nothing moves into place on screen.
    const sizeOf = (node: GraphNode) =>
      node.sharedSummary ? SHARED_TILE_SIZE : node.isHolding ? holdingSize(node.weight) : TILE_SIZE;
    const nodeById = new Map(nodes.map((node) => [node.id, node]));
    // The frame's shape; before the page has settled the frame can still be flat, so a usual shape stands in.
    const frameRatio =
      container.clientHeight > 100 ? container.clientWidth / container.clientHeight : DEFAULT_FRAME_RATIO;
    const positions = spreadLayout(
      nodes.map((node) => node.id),
      edges,
      (id) => {
        const node = nodeById.get(id)!;
        const lines = labelLines(node);
        const tile = sizeOf(node);
        const label = lines.length * LABEL_LINE + LABEL_EXTRA;
        // The label box's width, from its longest line (at most LABEL_MAX_WIDTH, then cut short).
        const longest = Math.max(...lines.map((line) => line.length));
        const width = Math.min(LABEL_MAX_WIDTH, longest * LABEL_CHAR + LABEL_PADDING);
        return { width: Math.max(tile, width), height: tile + label, below: label / 2 };
      },
      frameRatio,
    );

    const elements: ElementDefinition[] = [
      ...nodes.map((node) => ({
        position: { ...(positions.get(node.id) ?? { x: 0, y: 0 }) },
        data: {
          id: node.id,
          // The label box's lines. The canvas lays them out invisibly, only so the graph's size counts every box (see
          // the node style); the box itself is HTML, over the graph.
          label: labelLines(node).join('\n'),
          // The letter first; swapped for the logo once it has loaded (below).
          image: letterTile(node.name),
          logo: 0,
          holding: node.isHolding ? 1 : 0,
          // A holding grows with its weight; other companies keep the usual size (shared ones are made bigger below).
          size: node.isHolding ? holdingSize(node.weight) : TILE_SIZE,
          shared: node.sharedSummary ? 1 : 0,
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
      // The companies are already in place (positions above).
      layout: { name: 'preset', fit: false },
      minZoom: MIN_ZOOM,
      maxZoom: MAX_ZOOM,
      // No rubber-band selection, and no Cytoscape selection state: what is chosen is the page's, drawn with classes.
      boxSelectionEnabled: false,
      autounselectify: true,
      style: [
        // No grey circle under the pointer while pressing or dragging.
        // (Cytoscape's types list every core property as required, though each has a default.)
        { selector: 'core', style: { 'active-bg-opacity': 0, 'selection-box-opacity': 0 } as unknown as cytoscape.Css.Core },
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
            // The label box is HTML over the graph, in the app's own font. The canvas lays out the same lines,
            // invisibly, at the box's size, so packing the groups and fitting the view leave room for each box.
            label: 'data(label)',
            'font-family': 'Space Grotesk, system-ui, sans-serif',
            'font-size': 11,
            'font-weight': 600,
            'text-opacity': 0,
            'text-valign': 'bottom',
            'text-halign': 'center',
            'text-margin-y': 6,
            'text-wrap': 'wrap',
            'text-max-width': '170px',
            'text-background-opacity': 0,
            'text-background-padding': '6px',
            // No translucent shape over a company while it is pressed or chosen.
            'overlay-opacity': 0,
            'transition-property': 'border-width, border-color',
            'transition-duration': HOVER_TRANSITION,
          },
        },
        // A logo sits on a white square, at the same share of every tile, so all logos look the same size.
        {
          selector: 'node[logo = 1]',
          style: {
            'background-color': WHITE,
            'background-fit': 'none',
            'background-width': LOGO_SHARE,
            'background-height': LOGO_SHARE,
          },
        },
        // Holdings have a teal border, so they stand out from suppliers and customers.
        {
          selector: 'node[holding = 1]',
          style: { 'border-width': HOLDING_BORDER_WIDTH, 'border-color': ACCENT },
        },
        // Shared suppliers and customers: amber-bordered and bigger.
        {
          selector: 'node[shared = 1]',
          style: {
            width: SHARED_TILE_SIZE,
            height: SHARED_TILE_SIZE,
            'border-width': HOLDING_BORDER_WIDTH,
            'border-color': AMBER,
          },
        },
        // Under the pointer: the border brightens and thickens.
        { selector: 'node.hover', style: { 'border-color': LIME, 'border-width': HOLDING_BORDER_WIDTH + 1 } },
        // Chosen on the page: a lime border (the brackets and crosshair are drawn over the graph).
        { selector: 'node.chosen', style: { 'border-color': LIME, 'border-width': HOLDING_BORDER_WIDTH + 1 } },
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
            'overlay-opacity': 0,
            'transition-property': 'width, line-color, target-arrow-color',
            'transition-duration': HOVER_TRANSITION,
          },
        },
        // An arrow that would pass through another company curves around it (see applyDetours).
        {
          selector: 'edge.detour',
          style: {
            'curve-style': 'unbundled-bezier',
            'control-point-distances': 'data(bend)',
            'control-point-weights': 'data(bendAt)',
          },
        },
        { selector: 'edge[reviewStatus = "APPROVED"]', style: { 'line-style': 'solid' } },
        { selector: 'edge[reviewStatus = "UNREVIEWED"]', style: { 'line-style': 'dashed' } },
        { selector: 'edge.hover', style: { width: 3, 'line-color': TEXT, 'target-arrow-color': TEXT } },
        { selector: 'edge.chosen', style: { width: 3, 'line-color': LIME, 'target-arrow-color': LIME } },
        // Last, so it wins: what is outside the focus fades.
        { selector: '.faded', style: { opacity: FADED_OPACITY } },
      ],
    });
    cyRef.current = cy;

    // The label boxes, brackets and crosshair are HTML and SVG over the canvas. They are moved to where the graph
    // draws its companies whenever the companies or the view move, at most once a frame.
    let placing = false;
    const place = () => {
      placing = false;
      if (cy.destroyed()) return;
      placeOverlay(cy, labelRefs.current, crosshairRef.current, bracketsRef.current, selectedNodeRef.current);
    };
    const schedule = () => {
      if (placing) return;
      placing = true;
      window.requestAnimationFrame(place);
    };
    placeOverlayRef.current = schedule;
    cy.on('pan zoom resize position', schedule);

    // When the graph's frame changes size (as the page settles, or a drawer opens or closes), Cytoscape is told. Until
    // the user has chosen a view (a zoom or pan of their own, or one remembered from earlier), the graph is fitted to the
    // new size: the first fit can happen before the frame has its full size. After that, the view keeps its centre.
    let viewChosen = false;
    let size = { width: container.clientWidth, height: container.clientHeight };
    const observer =
      typeof ResizeObserver === 'undefined'
        ? null
        : new ResizeObserver(() => {
            if (cy.destroyed()) return;
            const next = { width: container.clientWidth, height: container.clientHeight };
            cy.resize();
            if (viewChosen) cy.panBy({ x: (next.width - size.width) / 2, y: (next.height - size.height) / 2 });
            else cy.fit(cy.elements(), PADDING);
            size = next;
            schedule();
          });
    observer?.observe(container);

    // Separate groups are packed into the frame's shape. Where every company then rests is home: Reset Layout comes back
    // here.
    packGroups(cy, container.clientWidth / Math.max(1, container.clientHeight));
    const home = new Map<string, Point>();
    cy.nodes().forEach((node) => {
      home.set(node.id(), { ...node.position() });
    });

    // What the user did to this portfolio's graph earlier in the tab: the companies they moved, and the view.
    const saved = layoutKey ? readSavedLayout(layoutKey) : null;
    const moved: Record<string, Point> = {};
    for (const [id, point] of Object.entries(saved?.positions ?? {})) {
      const node = cy.getElementById(id);
      if (node.nonempty()) {
        node.position(point);
        moved[id] = point;
      }
    }
    if (saved?.zoom !== undefined && saved.pan) {
      cy.viewport({ zoom: saved.zoom, pan: saved.pan });
      viewChosen = true;
    } else {
      cy.fit(cy.elements(), PADDING);
    }
    applyDetours(cy, edges);
    // Cytoscape measures the label boxes when it first draws them, so the arrows are checked again once it has.
    cy.one('render', () => {
      if (!cy.destroyed()) applyDetours(cy, edges);
    });
    schedule();

    // Dragging a company tugs its direct suppliers and customers along like elastic; they ease back when it is let go.
    const elastic = reducesMotion ? { stop: () => {}, forget: () => {} } : elasticDrag(cy, schedule);

    // Remembered for the tab: a company the user lets go, and a zoom or pan the user makes (not one the graph makes).
    const remember = () => {
      if (layoutKey) saveLayout(layoutKey, { positions: moved, zoom: cy.zoom(), pan: { ...cy.pan() } });
    };
    let viewTimer: number | undefined;
    cy.on('dragpan scrollzoom pinchzoom', () => {
      viewChosen = true;
      window.clearTimeout(viewTimer);
      viewTimer = window.setTimeout(remember, REMEMBER_DELAY_MS);
    });
    let detourTimer: number | undefined;
    cy.on('dragfree', 'node', (event) => {
      moved[event.target.id()] = { ...event.target.position() };
      remember();
      // Once the neighbours have eased back, the arrows are checked again against where everything now is.
      window.clearTimeout(detourTimer);
      detourTimer = window.setTimeout(() => {
        if (!cy.destroyed()) applyDetours(cy, edges);
      }, DETOUR_DELAY_MS);
    });

    resetLayoutRef.current = () => {
      if (cy.destroyed()) return;
      if (layoutKey) forgetLayout(layoutKey);
      Object.keys(moved).forEach((id) => delete moved[id]);
      elastic.forget();
      viewChosen = false;
      cy.nodes().forEach((node) => {
        const point = home.get(node.id());
        if (point) node.position(point);
      });
      cy.fit(cy.elements(), PADDING);
      applyDetours(cy, edges);
      schedule();
    };

    cy.on('tap', 'edge', (event) => {
      setEdgeTooltip(null);
      onEdgeSelectRef.current(event.target.data('relationshipId') as number);
    });

    // Under the pointer: the company or arrow reacts, and a company lights its own connections.
    const hover = (on: boolean) => (event: { target: NodeSingular }) => {
      event.target.toggleClass('hover', on);
      container.style.cursor = on ? 'pointer' : '';
      schedule();
    };
    const showEdgeTooltip = (event: { target: NodeSingular; renderedPosition?: Point }) => {
      const edge = edgeById.get(event.target.id());
      const point = event.renderedPosition;
      if (!edge || !point) return;
      // The chosen arrow's sentence is already the Inspector's heading.
      if (edge.relationshipId === selectedRelationshipRef.current) {
        setEdgeTooltip(null);
        return;
      }
      // Above the pointer, so it never covers the companies under the arrow; below only when there is no room above.
      setEdgeTooltip({
        text: edge.summary,
        x: point.x,
        y: point.y,
        flipX: point.x > container.clientWidth - TOOLTIP_WIDTH,
        flipY: point.y < TOOLTIP_HEIGHT,
      });
    };
    cy.on('mouseover', 'edge', (event) => {
      hover(true)(event);
      showEdgeTooltip(event);
    });
    cy.on('mousemove', 'edge', showEdgeTooltip);
    cy.on('mouseout', 'edge', (event) => {
      hover(false)(event);
      setEdgeTooltip(null);
    });
    cy.on('mouseover', 'node', (event) => {
      hover(true)(event);
      applyFocus(cy, edges, event.target.id());
    });
    cy.on('mouseout', 'node', (event) => {
      hover(false)(event);
      applyFocus(cy, edges, selectedNodeRef.current);
    });
    cy.on('tap', 'node', (event) => {
      applyFocus(cy, edges, event.target.id());
      onNodeSelectRef.current?.(event.target.id());
      schedule();
    });
    cy.on('tap', (event) => {
      if (event.target !== cy) return;
      applyFocus(cy, edges, null);
      onNodeSelectRef.current?.(null);
      schedule();
    });
    applyFocus(cy, edges, selectedNodeRef.current);

    // Teal markers carry flow direction independently from the status shown by the base line.
    let flowFrame: number | undefined;
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
        flowFrame = window.requestAnimationFrame(step);
      };
      flowFrame = window.requestAnimationFrame(step);
    }

    return () => {
      setEdgeTooltip(null);
      if (flowFrame !== undefined) window.cancelAnimationFrame(flowFrame);
      window.clearTimeout(viewTimer);
      window.clearTimeout(detourTimer);
      elastic.stop();
      observer?.disconnect();
      resetLayoutRef.current = () => {};
      placeOverlayRef.current = () => {};
      cy.destroy();
      cyRef.current = null;
    };
    // onEdgeSelect is deliberately left out: it is read through the ref above, so that selecting an edge (which
    // changes the page's state, and so gives onEdgeSelect a new identity) does not recreate the whole graph and
    // throw away its pan, zoom and node positions.
  }, [nodes, edges, layoutKey]);

  // Logos. Each is loaded first, off the graph: one that loads replaces the letter, one Logo.dev does not know
  // (fallback=404 makes it answer "not found") leaves the letter. Cytoscape loads images for "anonymous" use, which
  // Logo.dev allows, so the check loads them the same way. Kept apart from the graph itself, so domains arriving later
  // swap in logos without rebuilding the graph.
  useEffect(() => {
    const cy = cyRef.current;
    if (!cy) return;
    const logos: HTMLImageElement[] = [];
    for (const node of nodes) {
      const url = logoUrl(node, logoToken, logoDomains?.[node.name]);
      if (!url) continue;
      const logo = new Image();
      logo.crossOrigin = 'anonymous';
      logo.onload = () => {
        if (cy.destroyed()) return;
        const element = cy.getElementById(node.id);
        element.data('image', url);
        element.data('logo', 1);
      };
      logo.src = url;
      logos.push(logo);
    }
    return () => {
      logos.forEach((logo) => {
        logo.onload = null;
      });
    };
  }, [nodes, logoToken, logoDomains]);

  // The company chosen on the page stays in focus and is centred; its brackets and crosshair follow.
  useEffect(() => {
    const cy = cyRef.current;
    if (!cy || cy.destroyed()) return;
    applyFocus(cy, edges, selectedNodeId);
    cy.nodes().removeClass('chosen');
    const node = selectedNodeId ? cy.getElementById(selectedNodeId) : null;
    if (node && node.nonempty()) {
      node.addClass('chosen');
      cy.animate({ center: { eles: node }, duration: 300 });
    }
    // Restarts the brackets' closing-in animation for the newly chosen company.
    const brackets = bracketsRef.current;
    if (brackets) {
      brackets.classList.remove('closing');
      void brackets.offsetWidth;
      brackets.classList.add('closing');
    }
    placeOverlayRef.current();
  }, [selectedNodeId, edges]);

  // The relationship chosen on the page has its arrow drawn chosen.
  useEffect(() => {
    const cy = cyRef.current;
    if (!cy || cy.destroyed()) return;
    cy.edges().removeClass('chosen');
    if (selectedRelationshipId !== null) {
      cy.edges(`[relationshipId = ${selectedRelationshipId}]`).addClass('chosen');
      setEdgeTooltip(null);
    }
  }, [selectedRelationshipId, nodes, edges]);

  // Brings every company back into view, however far the user has zoomed, panned or dragged.
  function fitToScreen() {
    const cy = cyRef.current;
    cy?.animate({ fit: { eles: cy.elements(), padding: PADDING }, duration: 300 });
  }

  return (
    <div ref={frameRef} className="graph-frame">
      {/* The graph itself, with nothing over it but its own labels and markers. */}
      <div className="graph-stage">
        <div ref={containerRef} className="supply-chain-graph" role="application" aria-label="Supply chain graph" />
        <div className="graph-overlay" aria-hidden="true">
          <svg ref={flowLayerRef} className="flow-marker-layer" style={{ visibility: 'hidden' }}>
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
          <svg ref={crosshairRef} className="graph-crosshair">
            <line className="crosshair-x" />
            <line className="crosshair-y" />
          </svg>
          <div ref={bracketsRef} className="graph-brackets">
            <span className="bracket top-left" />
            <span className="bracket top-right" />
            <span className="bracket bottom-left" />
            <span className="bracket bottom-right" />
          </div>
          {nodes.map((node) => (
            <div
              key={node.id}
              ref={(element) => {
                if (element) labelRefs.current.set(node.id, element);
                else labelRefs.current.delete(node.id);
              }}
              className={`graph-label${node.isHolding ? ' holding' : ''}${node.sharedSummary ? ' shared' : ''}`}
            >
              <LabelBox node={node} />
            </div>
          ))}
        </div>
        {edgeTooltip && (
          <p
            role="tooltip"
            className={`graph-tooltip${edgeTooltip.flipX ? ' flip-x' : ''}${edgeTooltip.flipY ? ' flip-y' : ''}`}
            style={{ left: edgeTooltip.x, top: edgeTooltip.y }}
          >
            {edgeTooltip.text}
          </p>
        )}
      </div>
      {/* Under the graph, so no company can hide behind it: the key, how to use the graph, and its two buttons. */}
      <div className="graph-bar">
        <GraphLegend hasRelationships={edges.length > 0} />
        <p className="graph-hint">Drag to Move · Scroll to Zoom · Click an Arrow for Its Evidence</p>
        <span className="graph-buttons">
          <button type="button" className="secondary" onClick={() => resetLayoutRef.current()}>
            Reset Layout
          </button>
          <button type="button" className="secondary" onClick={fitToScreen}>
            Fit to Screen
          </button>
        </span>
      </div>
    </div>
  );
}

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

/** A company's label box, line by line: its ticker and weight (or its name), its name, and what a shared one supplies. */
function labelLines(node: GraphNode): string[] {
  const name = displayName(node.name);
  const lines = [node.ticker ? nodeLabel(node) : name];
  if (node.ticker) lines.push(name);
  if (node.sharedSummary) lines.push(node.sharedSummary);
  return lines;
}

/**
 * What a company's label box says: its ticker in bold and, for a holding, its weight; then its name, muted. A company
 * without a ticker shows its name in bold. A shared company adds what it supplies or buys.
 */
function LabelBox({ node }: { node: GraphNode }) {
  const weight = node.isHolding ? formatWeight(node.weight) : null;
  const name = displayName(node.name);
  return (
    <>
      <span className="graph-label-top">
        <b>{node.ticker ?? name}</b>
        {weight && <span className="graph-label-weight">{weight}</span>}
      </span>
      {node.ticker && <span className="graph-label-name">{name}</span>}
      {node.sharedSummary && <span className="graph-label-shared">{node.sharedSummary}</span>}
    </>
  );
}

/**
 * Puts each label box under its company, and the brackets and crosshair on the chosen company, where the graph draws
 * them now. Label boxes follow the zoom, and fade with their company.
 */
function placeOverlay(
  cy: Core,
  labels: Map<string, HTMLDivElement>,
  crosshair: SVGSVGElement | null,
  brackets: HTMLDivElement | null,
  chosenId: string | null,
): void {
  const scale = Math.max(LABEL_SCALE_MIN, typeof cy.zoom === 'function' ? cy.zoom() : 1);
  cy.nodes().forEach((node) => {
    const label = labels.get(node.id());
    if (!label || typeof node.renderedPosition !== 'function') return;
    const position = node.renderedPosition();
    const below = position.y + node.renderedOuterHeight() / 2 + 4;
    label.style.transform = `translate(${position.x}px, ${below}px) translate(-50%, 0) scale(${scale})`;
    label.classList.toggle('faded', node.hasClass('faded'));
    label.classList.toggle('hover', node.hasClass('hover') || node.id() === chosenId);
  });

  const chosen = chosenId ? cy.getElementById(chosenId) : null;
  const shown = Boolean(chosen && chosen.nonempty() && typeof chosen.renderedBoundingBox === 'function');
  crosshair?.classList.toggle('shown', shown);
  brackets?.classList.toggle('shown', shown);
  if (!shown || !chosen) return;
  const box = chosen.renderedBoundingBox({ includeLabels: false, includeOverlays: false });
  const x = (box.x1 + box.x2) / 2;
  const y = (box.y1 + box.y2) / 2;
  if (crosshair) {
    const across = crosshair.querySelector('.crosshair-x');
    const down = crosshair.querySelector('.crosshair-y');
    across?.setAttribute('x1', '0');
    across?.setAttribute('x2', '100%');
    across?.setAttribute('y1', String(y));
    across?.setAttribute('y2', String(y));
    down?.setAttribute('x1', String(x));
    down?.setAttribute('x2', String(x));
    down?.setAttribute('y1', '0');
    down?.setAttribute('y2', '100%');
  }
  if (brackets) {
    brackets.style.left = `${box.x1 - BRACKET_GAP}px`;
    brackets.style.top = `${box.y1 - BRACKET_GAP}px`;
    brackets.style.width = `${box.x2 - box.x1 + BRACKET_GAP * 2}px`;
    brackets.style.height = `${box.y2 - box.y1 + BRACKET_GAP * 2}px`;
  }
}

// How much wider than the frame's exact shape a row of groups may be.
const ROW_SLACK = 1.3;

// The space between groups of companies that are not connected to each other.
const GROUP_GAP = 30;

/**
 * Moves groups of companies that are not connected to each other (for example Tesla and its battery suppliers, apart
 * from the chip makers) close together, in rows, so the whole graph roughly matches the frame's shape and the view can
 * be fitted at a readable zoom. Each group keeps its own arrangement; groups are placed largest first.
 */
function packGroups(cy: Core, frameRatio: number): void {
  if (typeof cy.elements !== 'function' || typeof cy.elements().components !== 'function') return;
  const groups = cy
    .elements()
    .components()
    .map((group) => ({ group, box: group.boundingBox({ includeLabels: true }) }))
    .sort((a, b) => b.box.h - a.box.h || b.box.w - a.box.w);
  if (groups.length < 2) return;
  const area = groups.reduce((sum, { box }) => sum + (box.w + GROUP_GAP) * (box.h + GROUP_GAP), 0);
  const widest = Math.max(...groups.map(({ box }) => box.w));
  // A little wider than the exact shape, so a group that nearly fits beside another goes there rather than below.
  const rowWidth = Math.max(widest, Math.sqrt(area * frameRatio) * ROW_SLACK);
  let x = 0;
  let y = 0;
  let rowHeight = 0;
  for (const { group, box } of groups) {
    if (x > 0 && x + box.w > rowWidth) {
      x = 0;
      y += rowHeight + GROUP_GAP;
      rowHeight = 0;
    }
    const dx = x - box.x1;
    const dy = y - box.y1;
    group.nodes().positions((node) => ({ x: node.position('x') + dx, y: node.position('y') + dy }));
    x += box.w + GROUP_GAP;
    rowHeight = Math.max(rowHeight, box.h);
  }
}

/**
 * Makes dragging feel elastic: while a company is dragged, its direct suppliers and customers follow a share of its move
 * (followTarget), easing towards it frame by frame; when it is let go, it stays where it was dropped and they ease back
 * to where they were. Nothing else moves. Returns stop, which ends any easing still running, and forget, which drops
 * the remembered resting places (after Reset Layout).
 */
function elasticDrag(cy: Core, onMove: () => void): { stop: () => void; forget: () => void } {
  const rest = new Map<string, Point>();
  const targets = new Map<string, Point>();
  let dragged: NodeSingular | null = null;
  let from: Point | null = null;
  let frame: number | undefined;

  // Where a company rests: remembered the first time it is needed, before anything has moved it.
  const restOf = (node: NodeSingular): Point => {
    if (!rest.has(node.id())) rest.set(node.id(), { ...node.position() });
    return rest.get(node.id())!;
  };
  const tick = () => {
    frame = undefined;
    if (cy.destroyed()) return;
    for (const [id, target] of targets) {
      const node = cy.getElementById(id);
      if (node.empty() || node.grabbed()) {
        targets.delete(id);
        continue;
      }
      const { position, arrived } = easeTowards(node.position(), target);
      node.position(position);
      if (arrived && dragged === null) targets.delete(id);
    }
    onMove();
    if (targets.size > 0) frame = window.requestAnimationFrame(tick);
  };
  const run = () => {
    if (frame === undefined) frame = window.requestAnimationFrame(tick);
  };

  cy.on('grab', 'node', (event) => {
    dragged = event.target as NodeSingular;
    from = restOf(dragged);
  });
  cy.on('drag', 'node', (event) => {
    const held = dragged;
    const start = from;
    if (!held || !start || event.target !== held) return;
    const to = held.position();
    held.neighborhood('node').forEach((neighbour) => {
      if (!neighbour.grabbed()) targets.set(neighbour.id(), followTarget(restOf(neighbour), start, to));
    });
    run();
  });
  cy.on('free', 'node', (event) => {
    const held = dragged;
    if (!held || event.target !== held) return;
    rest.set(held.id(), { ...held.position() });
    held.neighborhood('node').forEach((neighbour) => {
      targets.set(neighbour.id(), restOf(neighbour));
    });
    dragged = null;
    from = null;
    run();
  });

  return {
    stop: () => {
      if (frame !== undefined) window.cancelAnimationFrame(frame);
    },
    // After Reset Layout: every company's resting place is read again from where it now is.
    forget: () => {
      rest.clear();
      targets.clear();
    },
  };
}

/** Curves each arrow that would pass through another company around it, and straightens the others (see detours). */
function applyDetours(cy: Core, edges: GraphEdge[]): void {
  const boxes = new Map<string, Box>();
  const tiles = new Map<string, Point>();
  cy.nodes().forEach((node) => {
    boxes.set(node.id(), node.boundingBox({ includeLabels: true }));
    tiles.set(node.id(), { ...node.position() });
  });
  const found = detours(boxes, tiles, edges);
  cy.edges().forEach((edge) => {
    const detour = found.get(edge.id());
    if (detour) edge.data({ bend: detour.distance, bendAt: detour.weight }).addClass('detour');
    else edge.removeClass('detour');
  });
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
