// What the workspace works out from what it loaded: the spotlight of a chosen alert, the order alerts are read in, and
// how much of the portfolio an alert reaches. The alerts are about the real TSMC headlines (test/sampleAlerts.ts).
import { describe, expect, it } from 'vitest';
import type { ReachedHolding } from '../../api/alerts';
import { sampleAlert, tsmcSuppliesNvidia } from '../../test/sampleAlerts';
import { TSMC } from '../../test/sampleNews';
import { alertKey, byUrgency, exposureOf, joinYears, namedIn, offGraphId, spotlightOf } from './view';

const view = {
  nodeIdByHolding: new Map([
    [1, '1'],
    [6, '6'],
  ]),
  nodeIdOfName: new Map([
    [TSMC.name, String(TSMC.id)],
    ['Lam Research Corporation', '6'],
  ]),
};

describe('spotlightOf', () => {
  it('lightsTheCompanyAnAlertIsAboutTheHoldingsItReachesAndTheLinkBetween', () => {
    const spotlight = spotlightOf(sampleAlert({ holdings: [tsmcSuppliesNvidia(1, 1)] }), view);

    expect(spotlight).toEqual({ nodeIds: ['1', String(TSMC.id)], relationshipIds: [1] });
  });

  it('lightsBothLinksOfAHoldingReachedInTwoSteps', () => {
    // Lam Research supplies TSMC (relationship 14), which supplies NVIDIA (relationship 1).
    const twoSteps: ReachedHolding = { ...tsmcSuppliesNvidia(1, 14), indirect: true, secondRelationshipId: 1 };

    const spotlight = spotlightOf(sampleAlert({ companyName: 'Lam Research Corporation', holdings: [twoSteps] }), view);

    expect(spotlight.relationshipIds).toEqual([14, 1]);
    expect(spotlight.nodeIds).toEqual(['1', '6']);
  });

  it('lightsOnlyTheHoldingForAnAlertAboutTheHoldingItself', () => {
    const itself: ReachedHolding = {
      ...tsmcSuppliesNvidia(1, 1),
      supplierName: null,
      customerName: null,
      reviewStatus: null,
      relationshipId: null,
    };

    expect(spotlightOf(sampleAlert({ companyName: null, holdings: [itself] }), view)).toEqual({
      nodeIds: ['1'],
      relationshipIds: [],
    });
  });
});

describe('byUrgency', () => {
  it('putsTheMostSevereFirstThenTheMostExposedThenTheNewest', () => {
    const low = sampleAlert({ id: 1, severity: 'LOW' });
    const high = sampleAlert({ id: 2, severity: 'HIGH' });
    const wider = { ...sampleAlert({ id: 3, severity: 'HIGH' }), exposure: 60 };

    expect([low, high, wider].sort(byUrgency).map(alertKey)).toEqual(['3', '2', '1']);
  });
});

describe('exposureOf', () => {
  it('isAShareOfThePortfolioWhenTheHoldingsHaveWeightsOrACountOfHoldingsWhenTheyDoNot', () => {
    const alert = { exposure: 12.25 };
    const holdings = [{}, {}, {}] as never[];

    expect(exposureOf(alert, { weighted: true, holdings })).toEqual({ value: 12.3, suffix: '%', label: 'of Portfolio' });
    expect(exposureOf({ exposure: 1 }, { weighted: false, holdings })).toEqual({ value: 1, suffix: '', label: 'of 3 Holdings' });
  });
});

describe('offGraphId', () => {
  it('givesAHoldingNotOnTheGraphAnIdNoGraphNodeHas', () => {
    expect(offGraphId(7)).toBe('holding-7');
    expect(offGraphId(7)).not.toMatch(/^\d+$/);
  });
});

describe('joinYears', () => {
  it('writesYearsAsASentencesList', () => {
    expect(joinYears([2026])).toBe('2026');
    expect(joinYears([2025, 2026])).toBe('2025 and 2026');
    expect(joinYears([2024, 2025, 2026])).toBe('2024, 2025 and 2026');
  });
});

describe('namedIn', () => {
  const entry = (counterpartyId: number, type: 'SUPPLIER' | 'CUSTOMER') => ({ counterpartyId, type }) as never;
  const history = {
    status: 'IDLE',
    reason: null,
    progress: null,
    unread: 0,
    years: [
      { year: 2024, relationships: [entry(5, 'SUPPLIER')], gone: [] },
      { year: 2025, relationships: [entry(5, 'SUPPLIER'), entry(5, 'CUSTOMER'), entry(6, 'SUPPLIER')], gone: [] },
      { year: 2026, relationships: [], gone: [] },
    ],
  } as never;

  it('givesTheYearsACompanyWasNamedInAsEachRoleAndNothingForACompanyNeverNamed', () => {
    expect(namedIn(history, 5)).toEqual([
      { role: 'Supplier', years: [2024, 2025] },
      { role: 'Customer', years: [2025] },
    ]);
    expect(namedIn(history, 7)).toEqual([]);
  });
});
