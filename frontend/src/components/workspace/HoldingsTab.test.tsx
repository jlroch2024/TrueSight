import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { Holding } from '../../api/portfolios';
import { HoldingsTab } from './HoldingsTab';
import { useWorkspaceView } from './view';

function holding(ticker: string, id: number, weight: number | null): Holding {
  return {
    id,
    ticker,
    weight,
    companyName: null,
    companyDisplayName: null,
    status: 'WAITING',
    statusReason: null,
    reportType: null,
    reportDate: null,
    reportLink: null,
    lastAnalysed: null,
  };
}

// The drawer as the workspace draws it: the holdings go through useWorkspaceView, which puts them in order.
function Drawer({ holdings }: { holdings: Holding[] }) {
  const view = useWorkspaceView(holdings, [], [], true);
  return (
    <HoldingsTab
      view={view}
      chosenNode={null}
      worst={null}
      onWorst={vi.fn()}
      onPeek={vi.fn()}
      onChoose={vi.fn()}
      onAdd={vi.fn()}
      onUpload={vi.fn()}
      uploading={false}
    />
  );
}

const order = () => screen.getAllByRole('listitem').map((item) => item.getAttribute('aria-label'));

describe('the holdings tab', () => {
  it('movesAHoldingToItsNewPlaceStraightAwayWhenItsWeightChanges', () => {
    const before = [holding('AAA', 1, 5), holding('BBB', 2, 20), holding('CCC', 3, 10)];
    const { rerender } = render(<Drawer holdings={before} />);
    expect(order()).toEqual(['BBB (BBB)', 'CCC (CCC)', 'AAA (AAA)']);

    rerender(<Drawer holdings={[holding('AAA', 1, 50), holding('BBB', 2, 20), holding('CCC', 3, 10)]} />);

    expect(order()).toEqual(['AAA (AAA)', 'BBB (BBB)', 'CCC (CCC)']);
  });
});
