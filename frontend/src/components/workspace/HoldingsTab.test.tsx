import { render, screen } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { Holding, HoldingStatus } from '../../api/portfolios';
import { HoldingRow, HoldingsTab } from './HoldingsTab';
import { useWorkspaceView } from './view';

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
    suppliersRead: 0,
    suppliersToRead: 0,
  };
}

type Links = ComponentProps<typeof HoldingRow>['links'];

// Only the counts matter to the row, so each connection is an empty stand-in.
function linksOf(suppliers: number, customers: number): Links {
  const connection = {} as NonNullable<Links>['suppliers'][number];
  return { suppliers: new Array(suppliers).fill(connection), customers: new Array(customers).fill(connection) };
}

function renderRow(status: HoldingStatus, suppliers: number, customers: number) {
  return render(
    <HoldingRow
      holding={holding(status)}
      beacon={null}
      links={linksOf(suppliers, customers)}
      share={0.2}
      current={false}
      index={0}
      onPeek={vi.fn()}
      onLeave={vi.fn()}
      onChoose={vi.fn()}
    />,
  );
}

function weighted(ticker: string, id: number, weight: number | null): Holding {
  return { ...holding('WAITING'), id, ticker, weight, companyName: null, companyDisplayName: null };
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
    const before = [weighted('AAA', 1, 5), weighted('BBB', 2, 20), weighted('CCC', 3, 10)];
    const { rerender } = render(<Drawer holdings={before} />);
    expect(order()).toEqual(['BBB (BBB)', 'CCC (CCC)', 'AAA (AAA)']);

    rerender(<Drawer holdings={[weighted('AAA', 1, 50), weighted('BBB', 2, 20), weighted('CCC', 3, 10)]} />);

    expect(order()).toEqual(['AAA (AAA)', 'BBB (BBB)', 'CCC (CCC)']);
  });

  it('saysHowManySuppliersReportsAreReadWhileTheyAreBeingRead', () => {
    const reading = { suppliersRead: 12, suppliersToRead: 30 };
    render(<Drawer holdings={[{ ...holding('AMD', 1, 10), ...reading }, { ...holding('NVDA', 2, 20), ...reading }]} />);

    expect(screen.getByRole('status')).toHaveTextContent("Reading Suppliers' Reports, 12 of 30");
  });

  it('showsNothingOnceEverySuppliersReportIsRead', () => {
    const done = { suppliersRead: 30, suppliersToRead: 30 };
    const { rerender } = render(<Drawer holdings={[{ ...holding('AMD', 1, 10), suppliersRead: 29, suppliersToRead: 30 }]} />);
    expect(screen.getByRole('status')).toHaveTextContent("Reading Suppliers' Reports, 29 of 30");

    rerender(<Drawer holdings={[{ ...holding('AMD', 1, 10), ...done }]} />);

    expect(screen.queryByText(/Reading Suppliers' Reports/)).not.toBeInTheDocument();
  });

  it('showsNothingWhenNoSupplierIsToBeRead', () => {
    render(<Drawer holdings={[holding('AMD', 1, 10)]} />);

    expect(screen.queryByText(/Reading Suppliers' Reports/)).not.toBeInTheDocument();
  });
});

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
