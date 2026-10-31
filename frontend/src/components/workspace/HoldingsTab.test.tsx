import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { Holding, HoldingStatus } from '../../api/portfolios';
import { HoldingRow } from './HoldingsTab';

function holding(status: HoldingStatus): Holding {
  return {
    id: 1,
    ticker: 'NVDA',
    weight: 18,
    companyName: 'NVIDIA Corporation',
    companyDisplayName: 'NVIDIA',
    status,
    statusReason: null,
    reportType: null,
    reportDate: null,
    reportLink: null,
    lastAnalysed: null,
  };
}

function renderRow(status: HoldingStatus, suppliers: number, customers: number) {
  const links = { suppliers: new Array(suppliers).fill({}), customers: new Array(customers).fill({}) };
  return render(
    <HoldingRow
      holding={holding(status)}
      beacon={null}
      links={links as never}
      share={0.2}
      current={false}
      index={0}
      onPeek={vi.fn()}
      onLeave={vi.fn()}
      onChoose={vi.fn()}
    />,
  );
}

describe('a holding row', () => {
  it('showsSuppliersAboveCustomersWithTheNumberInTheColourOfItsKind', () => {
    const { container } = renderRow('DONE', 3, 1);

    const lines = container.querySelectorAll('.link-line');
    expect(lines).toHaveLength(2);
    expect(lines[0]).toHaveTextContent('3 Suppliers');
    expect(lines[1]).toHaveTextContent('1 Customer');
    expect(lines[1]).not.toHaveTextContent('1 Customers');
    expect(lines[0]).toHaveClass('supplier');
    expect(lines[1]).toHaveClass('customer');
    expect(lines[0]!.querySelector('.link-number')).toHaveTextContent('3');
    expect(lines[0]).not.toHaveClass('zero');
  });

  it('keepsAZeroButMakesItFaint', () => {
    const { container } = renderRow('DONE', 2, 0);

    const lines = container.querySelectorAll('.link-line');
    expect(lines[0]).toHaveTextContent('2 Suppliers');
    expect(lines[1]).toHaveTextContent('0 Customers');
    expect(lines[1]).toHaveClass('zero');
    expect(lines[0]).not.toHaveClass('zero');
  });

  it('saysNothingFoundInFaintTextOnTheTopLineWhenThereAreNeitherSuppliersNorCustomers', () => {
    const { container } = renderRow('DONE', 0, 0);

    const lines = container.querySelectorAll('.link-line');
    expect(lines).toHaveLength(1);
    expect(lines[0]).toHaveTextContent('Nothing Found');
    expect(lines[0]).toHaveClass('faint');
    expect(container).not.toHaveTextContent(/Suppliers|Customers/);
  });

  it('showsTheStatusInsteadWhileTheHoldingIsStillBeingAnalysed', () => {
    const { container } = renderRow('ANALYSING', 3, 1);

    expect(screen.getByText('Analysing')).toBeInTheDocument();
    expect(container.querySelector('.link-line')).toBeNull();
  });

  it('showsNinetyNineSuppliersAndCustomersWhole', () => {
    const { container } = renderRow('DONE', 99, 99);

    const lines = container.querySelectorAll('.link-line');
    expect(lines[0]).toHaveTextContent('99 Suppliers');
    expect(lines[1]).toHaveTextContent('99 Customers');
  });
});
