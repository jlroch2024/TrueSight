// The graph of every year at once, and what each year shows of it, on the real sample relationships.
import { renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import sampleRelationshipsJson from '../../../../docs/examples/relationships.json';
import type { History, HistoryEntry } from '../../api/history';
import type { Holding } from '../../api/portfolios';
import type { Relationship } from '../../api/relationships';
import { buildTimeline, keyOf } from './timeline';
import { useWorkspaceView } from './view';

const sample = sampleRelationshipsJson as Relationship[];
const NVIDIA = sample[0]!.company;
const TSMC = sample[0]!.counterparty;
// Companies nothing today links to NVIDIA, named by older years.
const OLD = sample.find((item) => item.counterparty.id !== TSMC.id && item.counterparty.id !== NVIDIA.id)!.counterparty;
const NEW = sample.find((item) => ![TSMC.id, OLD.id, NVIDIA.id].includes(item.counterparty.id))!.counterparty;

const holding = (id: number, company: { name: string; displayName: string; ticker: string | null }): Holding => ({
  id,
  ticker: company.ticker ?? company.name,
  weight: null,
  companyName: company.name,
  companyDisplayName: company.displayName,
  status: 'DONE',
  statusReason: null,
  reportType: null,
  reportDate: null,
  reportLink: null,
  lastAnalysed: null,
  suppliersRead: 0,
  suppliersToRead: 0,
});

const entry = (counterparty: number, change: HistoryEntry['change'], type: HistoryEntry['type'] = 'SUPPLIER'): HistoryEntry => ({
  key: keyOf(NVIDIA.id, counterparty, type),
  companyId: NVIDIA.id,
  counterpartyId: counterparty,
  type,
  provides: 'wafers',
  evidence: sample[0]!.evidence,
  reportUrl: sample[0]!.report.url,
  filingDate: '2024-02-21',
  change,
});

const named = (...companies: { id: number; name: string; displayName: string; ticker: string | null }[]) =>
  companies.map(({ id, name, displayName, ticker }) => ({ id, name, displayName, ticker }));

function history(): History {
  return {
    status: 'IDLE',
    reason: null,
    progress: null,
    unread: 0,
    unreadHoldings: 0,
    holdingCount: 1,
    years: [
      { year: 2024, relationships: [entry(TSMC.id, 'NEW'), entry(OLD.id, 'NEW')], gone: [] },
      { year: 2025, relationships: [entry(TSMC.id, 'KEPT'), entry(NEW.id, 'NEW', 'CUSTOMER')], gone: [entry(OLD.id, 'NEW').key] },
    ],
    companies: named(NVIDIA, TSMC, OLD, NEW),
  };
}

function timeline(withHistory: History | null = history(), holdings = [holding(1, NVIDIA)]) {
  const { result } = renderHook(() => useWorkspaceView(holdings, [sample[0]!], [], true));
  return { view: result.current, timeline: buildTimeline(result.current, withHistory) };
}

describe('the timeline', () => {
  it('is today’s graph, whole in every year, when no history has been read', () => {
    const { view, timeline: none } = timeline(null);

    expect(none.nodes).toBe(view.nodes);
    expect(none.edges).toBe(view.edges);
    expect(none.years).toEqual([]);
    expect(none.at(null).shown.nodeIds).toEqual(view.nodes.map((node) => node.id));
  });

  it('lays out every company and arrow of every year, and today’s', () => {
    const { timeline: all } = timeline();

    expect(all.nodes.map((node) => node.id).sort()).toEqual([NVIDIA, TSMC, OLD, NEW].map((company) => String(company.id)).sort());
    expect(all.edges).toHaveLength(3);
    expect(all.years).toEqual([2024, 2025]);
  });

  it('gives an arrow only older years name a negative id of its own, running from supplier to customer', () => {
    const { timeline: all } = timeline();

    const past = all.edges.filter((edge) => edge.relationshipId < 0);
    expect(past.map((edge) => edge.relationshipId).sort((a, b) => b - a)).toEqual([-1, -2]);
    const oldSupplier = past.find((edge) => edge.source === String(OLD.id))!;
    expect(oldSupplier.target).toBe(String(NVIDIA.id));
    // A customer: NVIDIA to the buyer.
    const buyer = past.find((edge) => edge.target === String(NEW.id))!;
    expect(buyer.source).toBe(String(NVIDIA.id));
  });

  it('shows a year’s relationships, the companies they name and every holding; and today shows today’s only', () => {
    const { view, timeline: all } = timeline();

    expect(all.at(2024).shown.nodeIds.sort()).toEqual([NVIDIA, TSMC, OLD].map((company) => String(company.id)).sort());
    expect(all.at(2025).shown.nodeIds.sort()).toEqual([NVIDIA, TSMC, NEW].map((company) => String(company.id)).sort());
    expect(all.at(null).shown.edgeIds).toEqual(view.edges.map((edge) => edge.id));
    expect(all.at(null).shown.nodeIds.sort()).toEqual(view.nodes.map((node) => node.id).sort());
  });

  it('shares an arrow with today’s when the key is the same, so it is one arrow', () => {
    const { view, timeline: all } = timeline();

    expect(all.at(2024).shown.edgeIds).toContain(view.edges[0]!.id);
    expect(all.at(2025).shown.edgeIds).toContain(view.edges[0]!.id);
  });

  it('calls new what a year names that the year before did not, and tags it with the year', () => {
    const { timeline: all } = timeline();

    expect(all.at(2024).fresh).toBeNull();
    expect(all.at(null).fresh).toBeNull();
    const fresh = all.at(2025).fresh!;
    expect(fresh.tag).toBe('New in 2025');
    expect(fresh.nodeIds).toEqual([String(NEW.id)]);
    expect(fresh.edgeIds).toHaveLength(1);
  });

  it('finds the relationship a link was in a year, for the Inspector', () => {
    const { view, timeline: all } = timeline();

    expect(all.entryOf(2024, view.edges[0]!.relationshipId)?.key).toBe(entry(TSMC.id, 'NEW').key);
    expect(all.entryOf(2024, -1)?.counterpartyId).toBe(OLD.id);
    expect(all.entryOf(2025, -1)).toBeNull();
    expect(all.entryOf(null, view.edges[0]!.relationshipId)).toBeNull();
  });

  it('keeps a holding no link names as the holding’s own node rather than adding a second', () => {
    // NVIDIA's only link is today's; its older year names a holding with no links today (AMD, found by name).
    const amd = sample.find((item) => item.company.id !== NVIDIA.id)!.company;
    const past: History = {
      ...history(),
      years: [{ year: 2024, relationships: [{ ...entry(amd.id, 'NEW'), key: keyOf(NVIDIA.id, amd.id, 'SUPPLIER') }], gone: [] }],
      companies: named(NVIDIA, amd),
    };
    const { view, timeline: all } = timeline(past, [holding(1, NVIDIA), holding(2, amd)]);

    const nodesOfAmd = all.nodes.filter((node) => node.name === amd.name);
    expect(nodesOfAmd).toHaveLength(1);
    expect(all.at(2024).shown.nodeIds).toContain(nodesOfAmd[0]!.id);
    expect(view.nodeIdOfName.get(amd.name)).toBe(nodesOfAmd[0]!.id);
  });
});
