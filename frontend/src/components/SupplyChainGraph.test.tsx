// The graph's layout and zoom settings (TS-89). Cytoscape draws on a canvas, which the test browser cannot, so a
// stand-in records what the graph asks of it: the zoom limits, the two layout phases, and what Fit to Screen does.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

type Layout = { options: Record<string, unknown>; run: () => void; stop: () => void; one: (event: string, fn: () => void) => void; fire: () => void };
const made: { options?: Record<string, unknown>; layouts: Layout[]; animate: ReturnType<typeof vi.fn> } = {
  layouts: [],
  animate: vi.fn(),
};

vi.mock('cytoscape-cola', () => ({ default: {} }));
vi.mock('cytoscape', () => {
  const cytoscape = (options: Record<string, unknown>) => {
    made.options = options;
    return {
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
      on: vi.fn(),
      animate: made.animate,
      elements: () => 'every element',
      destroyed: () => false,
      destroy: vi.fn(),
    };
  };
  cytoscape.use = vi.fn();
  return { default: cytoscape };
});

import { SupplyChainGraph } from './SupplyChainGraph';

function drawGraph() {
  return render(<SupplyChainGraph nodes={[]} edges={[]} onEdgeSelect={() => {}} />);
}

beforeEach(() => {
  made.options = undefined;
  made.layouts = [];
  made.animate.mockClear();
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
});
