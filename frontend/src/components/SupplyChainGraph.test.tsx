// The graph's layout and zoom settings (TS-89), its moving dashes and its focus on one company (TS-97), and its logo
// tiles and labels (TS-95). Cytoscape draws
// on a canvas, which the test browser cannot, so a stand-in records what the graph asks of it: the zoom limits, the two
// layout phases, what Fit to Screen does, which companies and arrows are faded, and the dash offsets.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flowProgressAt, type GraphEdge, type GraphNode } from '../pages/supplyChain';

type Layout = { options: Record<string, unknown>; run: () => void; stop: () => void; one: (event: string, fn: () => void) => void; fire: () => void };
type Handler = (event: { target: unknown }) => void;
// A stand-in company or arrow: it remembers its classes, so a test can see what was faded.
type Fake = {
  id: () => string;
  renderedSourceEndpoint: () => { x: number; y: number };
  renderedTargetEndpoint: () => { x: number; y: number };
  renderedControlPoints: () => { x: number; y: number }[];
  classes: Set<string>;
  toggleClass: (name: string, on: boolean) => Fake;
  removeClass: (name: string) => Fake;
};
const made: {
  options?: Record<string, unknown>;
  layouts: Layout[];
  animate: ReturnType<typeof vi.fn>;
  handlers: Map<string, Handler>;
  nodes: Fake[];
  edges: Fake[];
  style: ReturnType<typeof vi.fn>;
  frames: FrameRequestCallback[];
  cy?: object;
  // What the graph set on a company after it was drawn, e.g. its logo once loaded: [company id, field, value].
  dataSet: Array<[string, string, unknown]>;
  // Every logo the graph started loading.
  images: FakeImage[];
  // The selectors of the arrows the graph drew chosen, e.g. "[relationshipId = 2]".
  selectedEdges: string[];
  // The ids of the companies the graph drew chosen.
  chosenNodes: string[];
  fit: ReturnType<typeof vi.fn>;
} = {
  selectedEdges: [],
  chosenNodes: [],
  fit: vi.fn(),
  dataSet: [],
  images: [],
  layouts: [],
  animate: vi.fn(),
  handlers: new Map(),
  nodes: [],
  edges: [],
  style: vi.fn(),
  frames: [],
};

// A stand-in for the browser's Image: the test browser loads no pictures, so a test says whether a logo loaded.
class FakeImage {
  crossOrigin: string | null = null;
  src = '';
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  constructor() {
    made.images.push(this);
  }
}

function fake(id: string): Fake {
  const item: Fake = {
    id: () => id,
    renderedSourceEndpoint: () => ({ x: 0, y: 0 }),
    renderedTargetEndpoint: () => ({ x: 100, y: 0 }),
    renderedControlPoints: () => [],
    classes: new Set(),
    toggleClass: (name, on) => {
      if (on) item.classes.add(name);
      else item.classes.delete(name);
      return item;
    },
    removeClass: (name) => {
      item.classes.delete(name);
      return item;
    },
  };
  return item;
}

function collection(items: Fake[], style?: ReturnType<typeof vi.fn>, selector?: string) {
  return {
    forEach: (fn: (item: Fake) => void) => items.forEach((item) => fn(item)),
    removeClass: (name: string) => items.forEach((item) => item.removeClass(name)),
    addClass: (name: string) => {
      if (selector && name === 'chosen') made.selectedEdges.push(selector);
    },
    style: style ?? vi.fn(),
  };
}

// What cy.elements() answers: every company and arrow at once.
const everyElement = {
  removeClass: (name: string) => [...made.nodes, ...made.edges].forEach((item) => item.removeClass(name)),
};

vi.mock('cytoscape-cola', () => ({ default: {} }));
vi.mock('cytoscape', () => {
  const cytoscape = (options: Record<string, unknown>) => {
    made.options = options;
    const elements = options.elements as Array<{ data: { id: string; source?: string } }>;
    made.nodes = elements.filter((element) => element.data.source === undefined).map((element) => fake(element.data.id));
    made.edges = elements.filter((element) => element.data.source !== undefined).map((element) => fake(element.data.id));
    const cy = {
      layout: (layoutOptions: Record<string, unknown>) => {
        let onStop = () => {};
        const layout: Layout = {
          options: layoutOptions,
          run: vi.fn(),
          stop: vi.fn(),
          one: (_event, fn) => {
            onStop = fn;
          },
          fire: () => onStop(),
        };
        made.layouts.push(layout);
        return layout;
      },
      // Remembers each handler under its event and selector, e.g. "mouseover node" or "tap" for the background.
      on: (event: string, selectorOrHandler: string | Handler, handler?: Handler) => {
        if (typeof selectorOrHandler === 'string') made.handlers.set(`${event} ${selectorOrHandler}`, handler!);
        else made.handlers.set(event, selectorOrHandler);
      },
      one: (event: string, selectorOrHandler: string | Handler, handler?: Handler) => {
        if (typeof selectorOrHandler === 'string') made.handlers.set(`${event} ${selectorOrHandler}`, handler!);
        else made.handlers.set(event, selectorOrHandler);
      },
      animate: made.animate,
      elements: () => everyElement,
      fit: made.fit,
      nodes: () => collection(made.nodes),
      edges: (selector?: string) => collection(made.edges, made.style, selector),
      getElementById: (id: string) => {
        const exists = made.nodes.some((node) => node.id() === id);
        return {
          data: (field: string, value: unknown) => made.dataSet.push([id, field, value]),
          empty: () => !exists,
          nonempty: () => exists,
          addClass: (name: string) => {
            if (name === 'chosen') made.chosenNodes.push(id);
          },
        };
      },
      destroyed: () => false,
      destroy: vi.fn(),
    };
    made.cy = cy;
    return cy;
  };
  cytoscape.use = vi.fn();
  return { default: cytoscape };
});

import { SupplyChainGraph } from './SupplyChainGraph';

function drawGraph(nodes: GraphNode[] = [], edges: GraphEdge[] = [], logoToken = '') {
  return render(<SupplyChainGraph nodes={nodes} edges={edges} onEdgeSelect={() => {}} logoToken={logoToken} />);
}

// Pretends the visitor's computer is (or is not) set to reduce motion.
function setReducedMotion(reduces: boolean) {
  window.matchMedia = vi.fn().mockReturnValue({ matches: reduces }) as unknown as typeof window.matchMedia;
}

// Runs the animation frames the graph has asked for so far, as if {@code now} milliseconds had passed.
function runFrame(now: number) {
  made.frames.splice(0).forEach((frame) => frame(now));
}

// Three companies and two arrows that matter, and one that does not touch the focused company:
//   supplier -> holding -> customer, and supplier -> other.
const FOCUS_NODES: GraphNode[] = [
  { id: 'supplier', name: 'Supplier', ticker: null, isHolding: false, weight: null },
  { id: 'holding', name: 'Holding', ticker: 'HLD', isHolding: true, weight: null },
  { id: 'customer', name: 'Customer', ticker: null, isHolding: false, weight: null },
  { id: 'other', name: 'Other', ticker: 'OTH', isHolding: true, weight: null },
];
const FOCUS_EDGES: GraphEdge[] = [
  { id: 'relationship-1', source: 'supplier', target: 'holding', relationshipId: 1, reviewStatus: 'APPROVED' },
  { id: 'relationship-2', source: 'holding', target: 'customer', relationshipId: 2, reviewStatus: 'UNREVIEWED' },
  { id: 'relationship-3', source: 'supplier', target: 'other', relationshipId: 3, reviewStatus: 'UNREVIEWED' },
];

function fadedIds(): string[] {
  return [...made.nodes, ...made.edges].filter((item) => item.classes.has('faded')).map((item) => item.id()).sort();
}

beforeEach(() => {
  made.options = undefined;
  made.layouts = [];
  made.animate.mockClear();
  made.handlers = new Map();
  made.nodes = [];
  made.edges = [];
  made.style.mockClear();
  made.frames = [];
  made.selectedEdges = [];
  made.chosenNodes = [];
  made.fit.mockClear();
  made.dataSet = [];
  made.images = [];
  vi.stubGlobal('Image', FakeImage);
  setReducedMotion(false);
  window.requestAnimationFrame = vi.fn((callback: FrameRequestCallback) => made.frames.push(callback)) as unknown as typeof window.requestAnimationFrame;
  window.cancelAnimationFrame = vi.fn();
});

describe('SupplyChainGraph', () => {
  it('zooms at the normal speed, between 0.3x and 3x', () => {
    drawGraph();

    expect(made.options).not.toHaveProperty('wheelSensitivity');
    expect(made.options).toMatchObject({ minZoom: 0.3, maxZoom: 3 });
  });

  it('settles out of sight, shows the graph fitted, and starts the live physics only when a company is first dragged', () => {
    const { container } = drawGraph();
    const frame = container.querySelector('.graph-frame')!;

    expect(frame).toHaveClass('settling');
    expect(screen.getByText('Arranging the Graph…')).toBeInTheDocument();
    expect(made.layouts).toHaveLength(1);
    expect(made.layouts[0].options).toMatchObject({ name: 'cola', fit: true, maxSimulationTime: 2500, nodeDimensionsIncludeLabels: true });

    made.layouts[0].fire(); // the companies have settled

    expect(made.fit).toHaveBeenCalled();
    expect(frame).not.toHaveClass('settling');
    expect(made.layouts).toHaveLength(1); // nothing rearranges them once they are shown

    made.handlers.get('grab node')!({ target: made.nodes[0] }); // the user starts dragging a company

    expect(made.layouts).toHaveLength(2);
    expect(made.layouts[1].options).toMatchObject({ name: 'cola', infinite: true, fit: false });
    expect(made.layouts[1].run).toHaveBeenCalled();
  });

  it('brings every company back into view with Fit to Screen', async () => {
    drawGraph();

    await userEvent.setup().click(screen.getByRole('button', { name: 'Fit to Screen' }));

    expect(made.animate).toHaveBeenCalledWith(expect.objectContaining({ fit: { eles: everyElement, padding: 48 } }));
  });

  it("uses blueprint colors, square nodes and the app's one font for every label, with no monospace", () => {
    drawGraph([{ id: 'holding-1', name: 'NVIDIA Corporation', ticker: 'NVDA', isHolding: true, weight: null }]);

    const options = made.options as { style: unknown[] };
    expect(options.style).toContainEqual(expect.objectContaining({
      selector: 'node',
      style: expect.objectContaining({ shape: 'rectangle', 'font-family': expect.stringMatching(/^Space Grotesk/) }),
    }));
    expect(JSON.stringify(options.style)).not.toMatch(/Mono/);
    expect(options.style).toContainEqual(expect.objectContaining({
      selector: 'edge.chosen',
      style: expect.objectContaining({ 'line-color': '#c4f36a' }),
    }));
  });

  it("shows none of Cytoscape's own press circle, selection box or selection overlay", () => {
    drawGraph(FOCUS_NODES, FOCUS_EDGES);

    const options = made.options as { boxSelectionEnabled: boolean; autounselectify: boolean; style: unknown[] };
    expect(options).toMatchObject({ boxSelectionEnabled: false, autounselectify: true });
    expect(options.style).toContainEqual({ selector: 'core', style: { 'active-bg-opacity': 0, 'selection-box-opacity': 0 } });
    for (const selector of ['node', 'edge']) {
      expect(options.style).toContainEqual(expect.objectContaining({
        selector,
        style: expect.objectContaining({ 'overlay-opacity': 0 }),
      }));
    }
  });

  it('reacts to the pointer: a company or an arrow under it brightens, with a short transition, and lets go after', () => {
    drawGraph(FOCUS_NODES, FOCUS_EDGES);
    const options = made.options as { style: Array<{ selector: string; style: Record<string, unknown> }> };

    made.handlers.get('mouseover node')!({ target: made.nodes[1] });
    made.handlers.get('mouseover edge')!({ target: made.edges[0] });
    expect(made.nodes[1]!.classes.has('hover')).toBe(true);
    expect(made.edges[0]!.classes.has('hover')).toBe(true);

    made.handlers.get('mouseout node')!({ target: made.nodes[1] });
    made.handlers.get('mouseout edge')!({ target: made.edges[0] });
    expect(made.nodes[1]!.classes.has('hover')).toBe(false);
    expect(made.edges[0]!.classes.has('hover')).toBe(false);

    expect(options.style).toContainEqual(expect.objectContaining({
      selector: 'node.hover',
      style: expect.objectContaining({ 'border-color': '#c4f36a' }),
    }));
    expect(options.style).toContainEqual(expect.objectContaining({
      selector: 'edge.hover',
      style: expect.objectContaining({ width: 3 }),
    }));
    expect(options.style.find((rule) => rule.selector === 'node')!.style).toMatchObject({ 'transition-duration': 0.15 });
  });

  describe('the arrows', () => {
    it('uses solid and dashed base strokes for review status', () => {
      drawGraph(FOCUS_NODES, FOCUS_EDGES);

      const options = made.options as {
        elements: Array<{ data: { source?: string; reviewStatus?: string } }>;
        style: Array<{ selector: string; style: Record<string, unknown> }>;
      };
      expect(options.elements.filter((element) => element.data.source !== undefined).map((element) => element.data.reviewStatus))
        .toEqual(['APPROVED', 'UNREVIEWED', 'UNREVIEWED']);
      expect(options.style).toContainEqual(expect.objectContaining({
        selector: 'edge[reviewStatus = "APPROVED"]',
        style: expect.objectContaining({ 'line-style': 'solid' }),
      }));
      expect(options.style).toContainEqual(expect.objectContaining({
        selector: 'edge[reviewStatus = "UNREVIEWED"]',
        style: expect.objectContaining({ 'line-style': 'dashed' }),
      }));
    });

    it('moves separate teal markers from supplier to customer without adding graph elements', () => {
      const { container } = drawGraph(FOCUS_NODES, FOCUS_EDGES);
      const graphOptions = made.options as { elements: unknown[] };
      const markers = [...container.querySelectorAll<SVGCircleElement>('[data-flow-edge="relationship-1"]')];
      expect(graphOptions.elements).toHaveLength(FOCUS_NODES.length + FOCUS_EDGES.length);
      expect(markers).toHaveLength(2);

      runFrame(1000);
      runFrame(1550);

      expect(markers.map((marker) => Number(marker.getAttribute('cx')))).toEqual([25, 75]);
      expect(markers[0]).toHaveAttribute('fill', '#2dd4bf');
      expect(flowProgressAt(550)).toBe(0.25);
    });

    it('keeps status strokes and hides moving markers when the computer is set to reduce motion', () => {
      setReducedMotion(true);

      const { container } = drawGraph(FOCUS_NODES, FOCUS_EDGES);

      const options = made.options as { style: Array<{ selector: string; style: Record<string, unknown> }> };
      expect(options.style).toContainEqual(expect.objectContaining({
        selector: 'edge',
        style: expect.objectContaining({ 'line-style': 'solid' }),
      }));
      expect(options.style).toContainEqual(expect.objectContaining({
        selector: 'edge[reviewStatus = "UNREVIEWED"]',
        style: expect.objectContaining({ 'line-style': 'dashed' }),
      }));
      runFrame(1000);
      runFrame(1500);
      expect(window.requestAnimationFrame).toHaveBeenCalledTimes(1); // one layout overlay frame, no repeating flow frame
      expect(made.style).not.toHaveBeenCalled();
      expect(container.querySelector('.flow-marker-layer')?.getAttribute('style')).toContain('hidden');
      expect(made.fit).toHaveBeenCalled(); // laid out once, still, and shown at once
    });

    it('shows both status styles in the legend whenever arrows are present', () => {
      const { container, rerender } = drawGraph(FOCUS_NODES, FOCUS_EDGES);

      const legend = screen.getByRole('list', { name: 'Relationship review status' });
      expect(legend).toHaveTextContent('Approved');
      expect(legend).toHaveTextContent('Not yet reviewed');
      expect(legend.querySelector('.graph-legend-line.approved')).toBeInTheDocument();
      expect(legend.querySelector('.graph-legend-line.unreviewed')).toBeInTheDocument();

      rerender(<SupplyChainGraph nodes={FOCUS_NODES} edges={[]} onEdgeSelect={() => {}} />);
      expect(container.querySelector('[aria-label="Relationship review status"]')).not.toBeInTheDocument();
    });

    it('stop moving when the graph is taken off the page', () => {
      const { unmount } = drawGraph(FOCUS_NODES, FOCUS_EDGES);

      unmount();

      expect(window.cancelAnimationFrame).toHaveBeenCalled();
    });
  });

  describe('focus', () => {
    it('lights the company pointed at, its suppliers and customers and their arrows, and fades the rest', () => {
      drawGraph(FOCUS_NODES, FOCUS_EDGES);

      made.handlers.get('mouseover node')!({ target: made.nodes[1] }); // the holding

      expect(fadedIds()).toEqual(['other', 'relationship-3']);
    });

    it('brings everything back when the pointer moves off the company', () => {
      drawGraph(FOCUS_NODES, FOCUS_EDGES);
      made.handlers.get('mouseover node')!({ target: made.nodes[1] });

      made.handlers.get('mouseout node')!({ target: made.nodes[1] });

      expect(fadedIds()).toEqual([]);
    });

    it('focuses a company that is tapped, and lets go when the background is tapped', () => {
      drawGraph(FOCUS_NODES, FOCUS_EDGES);

      made.handlers.get('tap node')!({ target: made.nodes[0] }); // the supplier

      expect(fadedIds()).toEqual(['customer', 'relationship-2']);

      made.handlers.get('tap')!({ target: made.cy });

      expect(fadedIds()).toEqual([]);
    });

    it('keeps the company chosen on the page in focus, centres it, and draws it chosen, with its brackets', () => {
      const { container } = render(
        <SupplyChainGraph nodes={FOCUS_NODES} edges={FOCUS_EDGES} onEdgeSelect={() => {}} selectedNodeId="holding" />,
      );

      expect(fadedIds()).toEqual(['other', 'relationship-3']);
      expect(made.animate).toHaveBeenCalledWith(expect.objectContaining({ center: expect.anything() }));
      expect(made.chosenNodes).toEqual(['holding']);
      expect(container.querySelector('.graph-brackets')).toHaveClass('closing');
      expect(container.querySelectorAll('.graph-crosshair line')).toHaveLength(2);
    });

    it('goes back to the chosen company when the pointer moves off another one', () => {
      render(<SupplyChainGraph nodes={FOCUS_NODES} edges={FOCUS_EDGES} onEdgeSelect={() => {}} selectedNodeId="holding" />);
      made.handlers.get('mouseover node')!({ target: made.nodes[3] }); // other

      made.handlers.get('mouseout node')!({ target: made.nodes[3] });

      expect(fadedIds()).toEqual(['other', 'relationship-3']);
    });

    it('reports a company clicked, and nothing chosen when the background is clicked', () => {
      const onNodeSelect = vi.fn();
      render(<SupplyChainGraph nodes={FOCUS_NODES} edges={FOCUS_EDGES} onEdgeSelect={() => {}} onNodeSelect={onNodeSelect} />);

      made.handlers.get('tap node')!({ target: made.nodes[0] });
      made.handlers.get('tap')!({ target: made.cy });

      expect(onNodeSelect.mock.calls).toEqual([['supplier'], [null]]);
    });

    it('draws the arrow of the relationship chosen on the page selected', () => {
      render(<SupplyChainGraph nodes={FOCUS_NODES} edges={FOCUS_EDGES} onEdgeSelect={() => {}} selectedRelationshipId={2} />);

      expect(made.selectedEdges).toEqual(['[relationshipId = 2]']);
    });

    it('leaves the focus alone when something other than the background is tapped', () => {
      drawGraph(FOCUS_NODES, FOCUS_EDGES);
      made.handlers.get('tap node')!({ target: made.nodes[0] });

      made.handlers.get('tap')!({ target: made.nodes[0] });

      expect(fadedIds()).toEqual(['customer', 'relationship-2']);
    });

    it('fades what is outside the focus to a low opacity', () => {
      drawGraph(FOCUS_NODES, FOCUS_EDGES);

      const options = made.options as { style: unknown[] };
      expect(options.style).toContainEqual({ selector: '.faded', style: { opacity: 0.15 } });
    });
  });

  describe('company tiles', () => {
    // Real companies from docs/examples/relationships.json: a holding with a weight, a supplier with a ticker, and a
    // supplier with none.
    const TILE_NODES: GraphNode[] = [
      { id: 'nvda', name: 'NVIDIA Corporation', ticker: 'NVDA', isHolding: true, weight: 18 },
      { id: 'tsm', name: 'Taiwan Semiconductor Manufacturing Company Limited', ticker: 'TSM', isHolding: false, weight: null },
      { id: 'samsung', name: 'Samsung Electronics Co., Ltd.', ticker: null, isHolding: false, weight: null },
    ];

    type Element = { data: { id: string; label: string; image: string } };
    const elementsDrawn = () => (made.options as { elements: Element[] }).elements;
    const styles = () => (made.options as { style: Array<{ selector: string; style: Record<string, unknown> }> }).style;
    const letterIn = (image: string) => decodeURIComponent(image).match(/>([^<]+)<\/text>/)?.[1];

    it("gives every company a label box: a holding's ticker and weight over its name, others' ticker over their name", () => {
      const { container } = drawGraph(TILE_NODES);

      const boxes = [...container.querySelectorAll('.graph-label')];
      expect(boxes.map((box) => [...box.children].map((line) => line.textContent))).toEqual([
        ['NVDA18%', 'NVIDIA'],
        ['TSM', 'Taiwan Semiconductor Manufacturing'],
        ['Samsung Electronics'],
      ]);
      expect(boxes[0]).toHaveClass('holding');
      expect(boxes[1]).not.toHaveClass('holding');
    });

    it('shows each company\'s first letter on its square tile until a logo arrives', () => {
      drawGraph(TILE_NODES);

      const images = elementsDrawn().map((element) => element.data.image);
      expect(images.every((image) => image.startsWith('data:image/svg+xml'))).toBe(true);
      expect(images.map(letterIn)).toEqual(['N', 'T', 'S']);
      expect(styles()).toContainEqual(expect.objectContaining({
        selector: 'node',
        style: expect.objectContaining({
          shape: 'rectangle', width: 'data(size)', height: 'data(size)', 'background-image': 'data(image)',
        }),
      }));
    });

    it('draws a holding bigger by its weight, and suppliers and customers at the usual size', () => {
      drawGraph(TILE_NODES);

      const sizes = (made.options as { elements: Array<{ data: { size: number } }> }).elements.map((e) => e.data.size);
      expect(sizes[0]).toBeCloseTo(44 + 9 * Math.sqrt(18)); // NVIDIA, 18% of the portfolio
      expect(sizes.slice(1)).toEqual([44, 44]);
    });

    it("keeps room for each label box: the canvas lays out the box's lines, invisibly, and the layout counts them", () => {
      drawGraph(TILE_NODES);

      expect(elementsDrawn().map((element) => element.data.label)).toEqual([
        'NVDA 18%\nNVIDIA',
        'TSM\nTaiwan Semiconductor Manufacturing',
        'Samsung Electronics',
      ]);
      expect(styles()).toContainEqual(expect.objectContaining({
        selector: 'node',
        style: expect.objectContaining({ label: 'data(label)', 'text-opacity': 0, 'text-valign': 'bottom' }),
      }));
      expect(made.layouts[0]!.options).toMatchObject({ nodeDimensionsIncludeLabels: true, avoidOverlap: true });
    });

    it('gives holdings a teal border', () => {
      drawGraph(TILE_NODES);

      expect(styles()).toContainEqual({
        selector: 'node[holding = 1]',
        style: { 'border-width': 3, 'border-color': '#2dd4bf' },
      });
    });

    it('swaps a company\'s letter for its logo once the logo has loaded', () => {
      drawGraph(TILE_NODES, [], 'pk_test_token');

      expect(made.images.map((image) => new URL(image.src).pathname)).toEqual([
        '/ticker/NVDA',
        '/ticker/TSM',
        '/name/Samsung%20Electronics',
      ]);
      expect(made.images.every((image) => image.crossOrigin === 'anonymous')).toBe(true);

      made.images[0]!.onload!();

      expect(made.dataSet).toEqual([['nvda', 'image', made.images[0]!.src], ['nvda', 'logo', 1]]);
      // A logo sits on a white square, at the same share of every tile.
      expect(styles()).toContainEqual({
        selector: 'node[logo = 1]',
        style: { 'background-color': '#ffffff', 'background-fit': 'none', 'background-width': '72%', 'background-height': '72%' },
      });
    });

    it("asks Logo.dev by website domain for a company without a ticker, when its domain is known", () => {
      render(
        <SupplyChainGraph
          nodes={TILE_NODES}
          edges={[]}
          onEdgeSelect={() => {}}
          logoToken="pk_test_token"
          logoDomains={{ 'Samsung Electronics Co., Ltd.': 'samsung.com' }}
        />,
      );

      expect(made.images.map((image) => new URL(image.src).pathname)).toEqual(['/ticker/NVDA', '/ticker/TSM', '/samsung.com']);
    });

    it('keeps the letter for a company Logo.dev does not know', () => {
      drawGraph(TILE_NODES, [], 'pk_test_token');

      made.images[2]!.onerror?.(); // Logo.dev answers 404 for it

      expect(made.dataSet).toEqual([]);
      expect(letterIn(elementsDrawn()[2]!.data.image)).toBe('S');
    });

    it('still draws every company and its label without a Logo.dev token, with letters instead of logos', () => {
      drawGraph(TILE_NODES, [], '');

      expect(elementsDrawn()).toHaveLength(3);
      expect(document.querySelectorAll('.graph-label')).toHaveLength(3);
      expect(made.images).toEqual([]);
    });

    it('stops waiting for logos when the graph is taken off the page', () => {
      const { unmount } = drawGraph(TILE_NODES, [], 'pk_test_token');

      unmount();

      expect(made.images.every((image) => image.onload === null)).toBe(true);
    });
  });

  describe('shared suppliers and customers', () => {
    const SHARED: GraphNode = {
      id: '11',
      name: 'Taiwan Semiconductor Manufacturing Company Limited',
      ticker: 'TSM',
      isHolding: false,
      weight: null,
      sharedSummary: 'Supplies 4 holdings',
    };

    it('labels a company several holdings depend on with what it supplies, under its usual label', () => {
      const { container } = drawGraph([SHARED]);

      const box = container.querySelector('.graph-label')!;
      expect(box).toHaveClass('shared');
      expect(box.lastElementChild).toHaveTextContent('Supplies 4 holdings');
      const options = made.options as { elements: Array<{ data: Record<string, unknown> }> };
      expect(options.elements[0]!.data).toMatchObject({ shared: 1, holding: 0 });
    });

    it('draws it bigger, with an amber border', () => {
      drawGraph([SHARED]);

      const options = made.options as { style: unknown[] };
      expect(options.style).toContainEqual({
        selector: 'node[shared = 1]',
        style: expect.objectContaining({ width: 58, height: 58, 'border-color': '#f5b73b' }),
      });
    });

    it('labels a company that is not shared as before', () => {
      const { container } = drawGraph([{ ...SHARED, sharedSummary: undefined }]);

      expect(container.querySelector('.graph-label')).not.toHaveClass('shared');
      expect(container.querySelector('.graph-label-shared')).toBeNull();
    });
  });
});
