// The living graph's controls and clicks, on the real sample relationships. jsdom cannot draw on a <canvas>, so drawing
// is switched off here (the drawing's arithmetic is tested in camera.test.ts and physics.test.ts); everything around it
// is the real component.
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import sampleRelationshipsJson from '../../../docs/examples/relationships.json';
import type { Relationship } from '../api/relationships';
import { buildGraphElements, sharedCompanies, withSharedSummaries } from '../pages/supplyChain';
import { LivingGraph } from './LivingGraph';

const relationships = sampleRelationshipsJson as Relationship[];
const { nodes, edges } = buildGraphElements(relationships, []);

beforeEach(() => {
  // No drawing in jsdom; and a graph 800 by 500 on screen.
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
  vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
    x: 0,
    y: 0,
    left: 0,
    top: 0,
    right: 800,
    bottom: 500,
    width: 800,
    height: 500,
    toJSON: () => ({}),
  });
  window.sessionStorage.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
});

function drawGraph(props: Partial<Parameters<typeof LivingGraph>[0]> = {}) {
  const onNodeSelect = vi.fn();
  const onEdgeSelect = vi.fn();
  const view = render(
    <LivingGraph
      nodes={nodes}
      edges={edges}
      onNodeSelect={onNodeSelect}
      onEdgeSelect={onEdgeSelect}
      layoutKey="truesight.graph.1"
      {...props}
    />,
  );
  return { ...view, onNodeSelect, onEdgeSelect };
}

describe('LivingGraph', () => {
  it('says how many companies and links it shows, on a graph the keyboard can reach', () => {
    drawGraph();

    expect(screen.getByText(`${nodes.length} Companies · ${edges.length} Links`)).toBeInTheDocument();
    expect(screen.getByRole('application', { name: 'Supply chain graph' })).toHaveAttribute('tabindex', '0');
  });

  it('keys each holding with arrows by colour, and lights its chain when pressed, until pressed again', async () => {
    drawGraph();
    const chains = screen.getByRole('list', { name: "Each holding's chain" });
    const first = within(chains).getAllByRole('button')[0]!;

    expect(within(chains).getAllByRole('button').length).toBeGreaterThan(1);
    await userEvent.click(first);
    expect(first).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(first);
    expect(first).toHaveAttribute('aria-pressed', 'false');
  });

  it('explains the amber badge only when some company is shared by several holdings, keeping the key short', () => {
    const { unmount } = drawGraph({ nodes: withSharedSummaries(nodes, sharedCompanies(relationships, nodes)) });
    expect(screen.getByRole('list', { name: 'Graph key' })).toHaveTextContent('Holdings Sharing It');
    expect(screen.queryByText('Approved')).not.toBeInTheDocument();
    unmount();

    drawGraph({ edges: [] });
    expect(screen.queryByRole('list', { name: 'Graph key' })).not.toBeInTheDocument();
  });

  it('adds a disruption to the key when an alert runs through a link', () => {
    const risks = new Map([
      [edges[0]!.relationshipId, { severity: 'HIGH' as const, eventType: 'STRIKE', title: 'Walkout' }],
    ]);

    drawGraph({ risks });

    expect(screen.getByRole('list', { name: 'Graph key' })).toHaveTextContent('Disruption');
  });

  it('adds sole or single source to the key only when a link is critical', () => {
    const { unmount } = drawGraph();
    expect(screen.queryByText('Sole or Single Source')).not.toBeInTheDocument();
    unmount();

    drawGraph({ edges: [{ ...edges[0]!, criticality: 'SOLE_SOURCE' }] });
    expect(screen.getByRole('list', { name: 'Graph key' })).toHaveTextContent('Sole or Single Source');
  });

  it('finds a company by name or ticker, and chooses it with Enter', async () => {
    const { onNodeSelect } = drawGraph();
    const nvidia = nodes.find((node) => node.name === 'NVIDIA Corporation')!;

    await userEvent.type(screen.getByRole('textbox', { name: 'Find a company' }), 'nvidia');
    const found = screen.getByRole('listbox', { name: 'Companies found' });
    expect(within(found).getAllByRole('option')[0]).toHaveTextContent(/nvidia/i);
    await userEvent.keyboard('{Enter}');

    expect(onNodeSelect).toHaveBeenCalledWith(nvidia.id);
  });

  it('says so when no company matches the search', async () => {
    drawGraph();

    await userEvent.type(screen.getByRole('textbox', { name: 'Find a company' }), 'zzzz');

    expect(screen.getByText('No company by that name is on the graph.')).toBeInTheDocument();
  });

  it('opens the search with the / key', async () => {
    drawGraph();
    screen.getByRole('application', { name: 'Supply chain graph' }).focus();

    await userEvent.keyboard('/');

    await waitFor(() => expect(screen.getByRole('textbox', { name: 'Find a company' })).toHaveFocus());
  });

  it('zooms in and out with its buttons, showing how close the view is', async () => {
    drawGraph();
    const shown = () => Number(screen.getByText(/^\d+%$/).textContent!.replace('%', ''));
    const before = shown();

    await userEvent.click(screen.getByRole('button', { name: 'Zoom In' }));
    await waitFor(() => expect(shown()).toBeGreaterThan(before));
    const closer = shown();
    await userEvent.click(screen.getByRole('button', { name: 'Zoom Out' }));
    await waitFor(() => expect(shown()).toBeLessThan(closer));
  });

  it('fills the screen and comes back, with its button or the F key', async () => {
    const { container } = drawGraph();
    const root = container.querySelector('.lg-root')!;

    await userEvent.click(screen.getByRole('button', { name: 'Full Screen' }));
    expect(root).toHaveClass('lg-full');
    await userEvent.click(screen.getByRole('button', { name: 'Leave Full Screen' }));
    expect(root).not.toHaveClass('lg-full');

    screen.getByRole('application', { name: 'Supply chain graph' }).focus();
    await userEvent.keyboard('f');
    expect(root).toHaveClass('lg-full');
  });

  it('lets go of whatever is chosen when the empty background is clicked', () => {
    const { onNodeSelect } = drawGraph();
    const canvas = screen.getByRole('application', { name: 'Supply chain graph' });

    // The top left corner, under the title: nothing is drawn there.
    act(() => {
      fireEvent.pointerDown(canvas, { pointerId: 1, clientX: 4, clientY: 496, buttons: 1 });
      fireEvent.pointerUp(canvas, { pointerId: 1, clientX: 4, clientY: 496 });
    });

    expect(onNodeSelect).toHaveBeenCalledWith(null);
  });

  it('shows a hint until the graph is first touched', async () => {
    drawGraph();
    const hint = await screen.findByText(/Drag Companies · Scroll to Zoom/);
    const canvas = screen.getByRole('application', { name: 'Supply chain graph' });

    act(() => {
      fireEvent.pointerDown(canvas, { pointerId: 1, clientX: 4, clientY: 496, buttons: 1 });
    });

    await waitFor(() => expect(hint).not.toBeInTheDocument());
  });
});
