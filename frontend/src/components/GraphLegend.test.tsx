import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { GraphLegend } from './GraphLegend';

describe('GraphLegend', () => {
  it('explains holdings, suppliers and customers, shared companies and the flow', () => {
    const { container } = render(<GraphLegend hasRelationships />);
    const key = screen.getByRole('list', { name: 'Graph key' });

    expect(key).toHaveTextContent('Holdings');
    expect(key).toHaveTextContent('Suppliers and Customers');
    expect(key).toHaveTextContent('Shared Companies');
    expect(key).toHaveTextContent('Supplier to Customer');
    expect(screen.getByRole('list', { name: 'Relationship criticality' })).toHaveTextContent('Sole or Single Source');
    expect(container.querySelector('.graph-legend-tile.holding')).toBeInTheDocument();
    expect(container.querySelector('.graph-legend-tile.shared')).toBeInTheDocument();
    expect(container.querySelector('.graph-legend-flow i')).toBeInTheDocument();
  });

  it('shows review styles only when the graph has arrows', () => {
    const { rerender } = render(<GraphLegend hasRelationships />);
    const status = screen.getByRole('list', { name: 'Relationship review status' });

    expect(within(status).getByText('Approved')).toBeInTheDocument();
    expect(within(status).getByText('Not Yet Reviewed')).toBeInTheDocument();

    rerender(<GraphLegend hasRelationships={false} />);
    expect(screen.queryByRole('list', { name: 'Relationship review status' })).not.toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Graph key' })).toBeInTheDocument();
  });
});
