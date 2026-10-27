// The Inspector's Alerts section for a company: the open alerts about it, whatever their age; for a holding, also the
// ones that reach it through a supplier or customer, with the path; and "No open alerts." when there are none. The
// companies and relationships are the real samples in docs/examples/relationships.json.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import sampleRelationshipsJson from '../../../docs/examples/relationships.json';
import type { Holding } from '../api/portfolios';
import type { Relationship } from '../api/relationships';
import { buildGraphElements, holdingNodeIds } from '../pages/supplyChain';
import { sampleAlert, tsmcSuppliesNvidia } from '../test/sampleAlerts';
import { formatDate } from '../format';
import { Inspector } from './Inspector';

const relationships = sampleRelationshipsJson as Relationship[];

function holding(id: number, ticker: string, companyName: string, companyDisplayName: string): Holding {
  return {
    id,
    ticker,
    weight: 20,
    companyName,
    companyDisplayName,
    status: 'DONE',
    statusReason: null,
    reportType: '10-K',
    reportDate: '2026-02-25',
    reportLink: 'https://www.sec.gov/example',
    lastAnalysed: null,
  };
}

// NVIDIA (company 1) and AMD (company 2) are holdings; TSMC (company 11) supplies both.
const holdings = [
  holding(1, 'NVDA', 'NVIDIA Corporation', 'NVIDIA'),
  holding(2, 'AMD', 'Advanced Micro Devices, Inc.', 'Advanced Micro Devices'),
];
const { nodes } = buildGraphElements(relationships, holdings);
const nodeIdByHolding = holdingNodeIds(holdings, nodes);
const TSMC_NAME = 'Taiwan Semiconductor Manufacturing Company Limited';

function inspect(nodeId: string, alerts: Parameters<typeof Inspector>[0]['alerts']) {
  return render(
    <Inspector
      selection={{ kind: 'company', nodeId }}
      nodes={nodes}
      relationships={relationships}
      holdings={holdings}
      nodeIdByHolding={nodeIdByHolding}
      onSelectNode={vi.fn()}
      onSelectRelationship={vi.fn()}
      alerts={alerts}
    />,
  );
}

const section = () => screen.getByRole('region', { name: 'Alerts' });

afterEach(() => {
  vi.restoreAllMocks();
});

describe('the Inspector, about a company', () => {
  it('lists the open alerts about it, however old, each with severity, headline, publisher, date, path and Open Article', () => {
    const recent = sampleAlert({ id: 1, headline: 0, severity: 'HIGH' });
    const old = sampleAlert({ id: 2, headline: 1, severity: 'LOW', daysOld: 60 });
    inspect('11', [recent, old]); // TSMC

    expect(within(section()).getByRole('heading', { name: /^Alerts/ })).toHaveTextContent('Alerts (2)');
    const [first, second] = within(section()).getAllByRole('listitem');
    expect(first).toHaveTextContent('High');
    expect(first).toHaveTextContent(recent.headline.article.title);
    expect(first).toHaveTextContent(`${recent.headline.article.source} · ${formatDate(recent.headline.article.publishedAt)}`);
    expect(first).toHaveTextContent('Taiwan Semiconductor Manufacturing supplies NVIDIA');
    expect(within(first!).getByRole('button', { name: 'Open Article' })).toBeInTheDocument();
    expect(second).toHaveTextContent('Low');
    expect(second).toHaveTextContent(old.headline.article.title);
    expect(second).toHaveTextContent(formatDate(old.headline.article.publishedAt));
  });

  it('tags each alert in the colour class of its severity, as the Alerts page does', () => {
    inspect('11', [
      sampleAlert({ id: 1, headline: 0, severity: 'HIGH' }),
      sampleAlert({ id: 2, headline: 1, severity: 'MEDIUM' }),
      sampleAlert({ id: 3, headline: 2, severity: 'LOW' }),
    ]);

    expect(within(section()).getByText('High')).toHaveClass('news-tag', 'severity-high');
    expect(within(section()).getByText('Medium')).toHaveClass('news-tag', 'severity-medium');
    expect(within(section()).getByText('Low')).toHaveClass('news-tag', 'severity-low');
  });

  it('opens the article in a new tab', async () => {
    const open = vi.spyOn(window, 'open').mockImplementation(() => null);
    const alert = sampleAlert();
    inspect('11', [alert]);

    await userEvent.click(within(section()).getByRole('button', { name: 'Open Article' }));

    expect(open).toHaveBeenCalledWith(alert.headline.article.url, '_blank', 'noopener,noreferrer');
  });

  it('lists nothing about a company the alerts are not about, and says there are no open alerts', () => {
    inspect('12', [sampleAlert()]); // Samsung: the alert is about TSMC, and runs through no relationship of Samsung's

    expect(within(section()).getByRole('heading', { name: /^Alerts/ })).toHaveTextContent('Alerts (0)');
    expect(within(section()).getByText('No open alerts.')).toBeInTheDocument();
    expect(within(section()).queryByRole('listitem')).not.toBeInTheDocument();
  });

  it('does not list a dismissed alert, whatever it is about', () => {
    inspect('11', [sampleAlert({ status: 'DISMISSED' })]);

    expect(within(section()).getByText('No open alerts.')).toBeInTheDocument();
  });

  it('finds an alert by the stored name of its article\'s company, not by its display name', () => {
    inspect('11', [sampleAlert({ companyName: 'Taiwan Semiconductor Manufacturing' })]); // a display name

    expect(within(section()).getByText('No open alerts.')).toBeInTheDocument();
  });

  it('shows no Alerts section while the alerts have not loaded', () => {
    inspect('11', undefined);

    expect(screen.queryByRole('region', { name: 'Alerts' })).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Suppliers' })).toBeInTheDocument();
  });
});

describe('the Inspector, about a holding', () => {
  it('also lists the open alerts that reach it through a supplier, with the path', () => {
    const reaching = sampleAlert({ id: 1, companyName: TSMC_NAME, holdings: [tsmcSuppliesNvidia(1, 1)] });
    inspect('1', [reaching]); // NVIDIA

    expect(within(section()).getByRole('heading', { name: /^Alerts/ })).toHaveTextContent('Alerts (1)');
    expect(within(section()).getByText(reaching.headline.article.title)).toBeInTheDocument();
    expect(within(section()).getByText('Taiwan Semiconductor Manufacturing supplies NVIDIA')).toBeInTheDocument();
  });

  it('lists the alerts about the holding itself, and those that reach it, once each', () => {
    const about = sampleAlert({
      id: 1,
      headline: 0,
      companyName: 'NVIDIA Corporation',
      holdings: [{ ...tsmcSuppliesNvidia(1, 1), supplierName: null, customerName: null, relationshipId: null }],
    });
    const reaching = sampleAlert({ id: 2, headline: 1, companyName: TSMC_NAME });
    inspect('1', [about, reaching]);

    expect(within(section()).getAllByRole('listitem')).toHaveLength(2);
    expect(within(section()).getByText('Named in the Article')).toBeInTheDocument();
    expect(within(section()).getByText('Taiwan Semiconductor Manufacturing supplies NVIDIA')).toBeInTheDocument();
  });

  // As the backend's RelationshipConfidence works it out for the path; the website only shows it (See How Confident
  // TrueSight Is). An alert about the holding itself reaches it through no relationship, so it has no tag.
  it('tags each path with how confident TrueSight is about it, and an alert named in the article with none', () => {
    const about = sampleAlert({
      id: 1,
      headline: 0,
      companyName: 'NVIDIA Corporation',
      holdings: [
        { ...tsmcSuppliesNvidia(1, 1), supplierName: null, customerName: null, relationshipId: null, confidence: null },
      ],
    });
    const reaching = sampleAlert({
      id: 2,
      headline: 1,
      companyName: TSMC_NAME,
      holdings: [
        {
          ...tsmcSuppliesNvidia(1, 1),
          confidence: {
            level: 'HIGH',
            reasons: [
              { text: 'An Analyst Approved It', effect: 'RAISES' },
              { text: 'Quote Match Not Recorded', effect: 'NONE' },
              { text: 'Not Yet Second-Checked', effect: 'NONE' },
            ],
          },
        },
      ],
    });
    inspect('1', [about, reaching]); // NVIDIA

    const path = within(section()).getByText('Taiwan Semiconductor Manufacturing supplies NVIDIA').closest('p')!;
    expect(within(path).getByText('High Confidence')).toHaveClass('confidence-high');
    const named = within(section()).getByText('Named in the Article').closest('p')!;
    expect(named).not.toHaveTextContent(/Confidence/);
  });

  it('words a two-step path through a middle company', () => {
    const twoStep = sampleAlert({
      companyName: 'Carl Zeiss SMT GmbH',
      holdings: [
        {
          ...tsmcSuppliesNvidia(1, 1),
          indirect: true,
          supplierName: 'Carl Zeiss SMT',
          middleName: 'Taiwan Semiconductor Manufacturing',
          customerName: 'NVIDIA',
        },
      ],
    });
    inspect('1', [twoStep]);

    expect(
      within(section()).getByText('Carl Zeiss SMT supplies Taiwan Semiconductor Manufacturing, which supplies NVIDIA'),
    ).toBeInTheDocument();
  });

  it('leaves out the alerts that reach only another holding', () => {
    const reachingAmd = sampleAlert({
      companyName: TSMC_NAME,
      holdings: [{ ...tsmcSuppliesNvidia(2, 3), ticker: 'AMD', customerName: 'Advanced Micro Devices' }],
    });
    inspect('1', [reachingAmd]); // NVIDIA is not reached

    expect(within(section()).getByText('No open alerts.')).toBeInTheDocument();
  });

  it('shows only the paths into the holding that is chosen, when an alert reaches several', () => {
    const reachingBoth = sampleAlert({
      companyName: TSMC_NAME,
      holdings: [
        tsmcSuppliesNvidia(1, 1),
        { ...tsmcSuppliesNvidia(2, 3), ticker: 'AMD', customerName: 'Advanced Micro Devices' },
      ],
    });
    inspect('1', [reachingBoth]);

    expect(within(section()).getByText('Taiwan Semiconductor Manufacturing supplies NVIDIA')).toBeInTheDocument();
    expect(within(section()).queryByText(/Advanced Micro Devices/)).not.toBeInTheDocument();
  });
});
