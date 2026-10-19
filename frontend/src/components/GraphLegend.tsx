type Props = {
  hasRelationships: boolean;
};

/** The graph's visual key, including the review styles that apply only when arrows exist. */
export function GraphLegend({ hasRelationships }: Props) {
  return (
    <div className="graph-key">
      <ul className="graph-legend" aria-label="Graph key">
        <li><span className="graph-legend-tile holding" aria-hidden="true" />Holdings</li>
        <li><span className="graph-legend-tile" aria-hidden="true" />Suppliers and Customers</li>
        <li><span className="graph-legend-tile shared" aria-hidden="true" />Shared Companies</li>
        <li><span className="graph-legend-flow" aria-hidden="true"><i /></span>Supplier to Customer</li>
      </ul>
      {hasRelationships && (
        <ul className="graph-legend" aria-label="Relationship review status">
          <li><span className="graph-legend-line approved" aria-hidden="true" />Approved</li>
          <li><span className="graph-legend-line unreviewed" aria-hidden="true" />Not yet reviewed</li>
        </ul>
      )}
    </div>
  );
}
