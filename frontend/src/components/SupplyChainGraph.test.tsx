// The graph's layout and zoom settings (TS-89), its moving dashes and its focus on one company (TS-97). Cytoscape draws
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
} = {
  layouts: [],
  animate: vi.fn(),
  handlers: new Map(),
  nodes: [],
  edges: [],
  style: vi.fn(),
  frames: [],
};

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

function drawGraph(nodes: GraphNode[] = [], edges: GraphEdge[] = []) {
  return render(<SupplyChainGraph nodes={nodes} edges={edges} onEdgeSelect={() => {}} />);
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
  { id: 'supplier', name: 'Supplier', ticker: null, isHolding: false },
  { id: 'holding', name: 'Holding', ticker: 'HLD', isHolding: true },
  { id: 'customer', name: 'Customer', ticker: null, isHolding: false },
  { id: 'other', name: 'Other', ticker: 'OTH', isHolding: true },
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
    drawGraph([{ id: 'holding-1', name: 'NVIDIA Corporation', ticker: 'NVDA', isHolding: true }]);

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
      runFrame(1500);
      runFrame(2000);

      const offsets = made.style.mock.calls.map((call) => call[1] as number);
      expect(made.style.mock.calls.every((call) => call[0] === 'line-dash-offset')).toBe(true);
      expect(offsets).toEqual([dashOffsetAt(0), dashOffsetAt(500), dashOffsetAt(1000)]);
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
});
