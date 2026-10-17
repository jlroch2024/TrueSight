// The graph's layout and zoom settings (TS-89), its moving dashes and its focus on one company (TS-97), and its logo
// tiles and labels (TS-95). Cytoscape draws
// on a canvas, which the test browser cannot, so a stand-in records what the graph asks of it: the zoom limits, the two
// layout phases, what Fit to Screen does, which companies and arrows are faded, and the dash offsets.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { dashOffsetAt, type GraphEdge, type GraphNode } from '../pages/supplyChain';

type Layout = { options: Record<string, unknown>; run: () => void; stop: () => void; one: (event: string, fn: () => void) => void; fire: () => void };
type Handler = (event: { target: unknown }) => void;
// A stand-in company or arrow: it remembers its classes, so a test can see what was faded.
type Fake = {
  id: () => string;
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
} = {
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

function collection(items: Fake[], style?: ReturnType<typeof vi.fn>) {
  return {
    forEach: (fn: (item: Fake) => void) => items.forEach((item) => fn(item)),
    removeClass: (name: string) => items.forEach((item) => item.removeClass(name)),
    unselect: vi.fn(),
    style: style ?? vi.fn(),
  };
}

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
      animate: made.animate,
      elements: () => 'every element',
      nodes: () => collection(made.nodes),
      edges: () => collection(made.edges, made.style),
      getElementById: (id: string) => ({
        data: (field: string, value: unknown) => made.dataSet.push([id, field, value]),
      }),
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

// Runs the next animation frame the graph asked for, as if {@code now} milliseconds had passed.
function runFrame(now: number) {
  const next = made.frames.shift();
  next?.(now);
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
  { id: 'relationship-1', source: 'supplier', target: 'holding', relationshipId: 1 },
  { id: 'relationship-2', source: 'holding', target: 'customer', relationshipId: 2 },
  { id: 'relationship-3', source: 'supplier', target: 'other', relationshipId: 3 },
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

  it('settles first while fitting the view, then keeps the physics running without moving the view', () => {
    drawGraph();

    expect(made.layouts).toHaveLength(1);
    expect(made.layouts[0].options).toMatchObject({ name: 'cola', fit: true, maxSimulationTime: 2500 });
    expect(made.layouts[0].options).not.toHaveProperty('infinite');

    made.layouts[0].fire(); // the first phase has settled

    expect(made.layouts).toHaveLength(2);
    expect(made.layouts[1].options).toMatchObject({ name: 'cola', infinite: true, fit: false });
    expect(made.layouts[1].run).toHaveBeenCalled();
  });

  it('brings every company back into view with Fit to Screen', async () => {
    drawGraph();

    await userEvent.setup().click(screen.getByRole('button', { name: 'Fit to Screen' }));

    expect(made.animate).toHaveBeenCalledWith(expect.objectContaining({ fit: { eles: 'every element', padding: 40 } }));
  });

  it('uses blueprint colors, square nodes and a mono font for tickers', () => {
    drawGraph([{ id: 'holding-1', name: 'NVIDIA Corporation', ticker: 'NVDA', isHolding: true, weight: null }]);

    const options = made.options as { elements: Array<{ data: { label: string; ticker: number } }>; style: unknown[] };
    expect(options.elements[0].data).toMatchObject({ label: 'NVDA', ticker: 1 });
    expect(options.style).toContainEqual(expect.objectContaining({
      selector: 'node',
      style: expect.objectContaining({ shape: 'rectangle' }),
    }));
    expect(options.style).toContainEqual(expect.objectContaining({
      selector: 'node[ticker = 1]',
      style: { 'font-family': 'JetBrains Mono' },
    }));
    expect(options.style).toContainEqual(expect.objectContaining({
      selector: 'edge:selected',
      style: expect.objectContaining({ 'line-color': '#c4f36a' }),
    }));
  });

  describe('the arrows', () => {
    it('are dashes that travel from supplier to customer', () => {
      drawGraph(FOCUS_NODES, FOCUS_EDGES);

      const options = made.options as { style: Array<{ selector: string; style: Record<string, unknown> }> };
      expect(options.style).toContainEqual(expect.objectContaining({
        selector: 'edge',
        style: expect.objectContaining({ 'line-style': 'dashed' }),
      }));

      runFrame(1000); // the first frame starts the clock
      runFrame(1250);
      runFrame(1500);

      const offsets = made.style.mock.calls.map((call) => call[1] as number);
      expect(made.style.mock.calls.every((call) => call[0] === 'line-dash-offset')).toBe(true);
      expect(offsets).toEqual([dashOffsetAt(0), dashOffsetAt(250), dashOffsetAt(500)]);
      // A smaller offset slides the dashes along the edge from its source, the supplier, to its target, the customer.
      expect(offsets[2]).toBeLessThan(offsets[1]);
      expect(offsets[1]).toBeLessThan(offsets[0]);
    });

    it('are still lines when the computer is set to reduce motion', () => {
      setReducedMotion(true);

      drawGraph(FOCUS_NODES, FOCUS_EDGES);

      const options = made.options as { style: Array<{ selector: string; style: Record<string, unknown> }> };
      expect(options.style).toContainEqual(expect.objectContaining({
        selector: 'edge',
        style: expect.objectContaining({ 'line-style': 'solid' }),
      }));
      expect(window.requestAnimationFrame).not.toHaveBeenCalled();
      expect(made.style).not.toHaveBeenCalled();
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

    it('labels every company: a holding with its ticker and weight, others with a ticker or a short name', () => {
      drawGraph(TILE_NODES);

      expect(elementsDrawn().map((element) => element.data.label)).toEqual(['NVDA 18%', 'TSM', 'Samsung Elect\u2026']);
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

    it('puts the label under the tile, on a patch that hides any arrow passing behind it', () => {
      drawGraph(TILE_NODES);

      expect(styles()).toContainEqual(expect.objectContaining({
        selector: 'node',
        style: expect.objectContaining({
          'text-valign': 'bottom',
          'text-background-color': '#0b2028',
          'text-background-opacity': 1,
        }),
      }));
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

      expect(made.dataSet).toEqual([['nvda', 'image', made.images[0]!.src]]);
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
      expect(elementsDrawn().map((element) => element.data.label)).toEqual(['NVDA 18%', 'TSM', 'Samsung Elect\u2026']);
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
      drawGraph([SHARED]);

      const options = made.options as { elements: Array<{ data: Record<string, unknown> }> };
      expect(options.elements[0]!.data).toMatchObject({ label: 'TSM\nSupplies 4 holdings', shared: 1, holding: 0 });
    });

    it('draws it bigger, with an amber border, and lets the two-line label wrap', () => {
      drawGraph([SHARED]);

      const options = made.options as { style: unknown[] };
      expect(options.style).toContainEqual({
        selector: 'node[shared = 1]',
        style: expect.objectContaining({
          width: 58,
          height: 58,
          'border-color': '#f5b73b',
          'text-wrap': 'wrap',
        }),
      });
    });

    it('labels a company that is not shared as before', () => {
      drawGraph([{ ...SHARED, sharedSummary: undefined }]);

      const options = made.options as { elements: Array<{ data: Record<string, unknown> }> };
      expect(options.elements[0]!.data).toMatchObject({ label: 'TSM', shared: 0 });
    });
  });
});
