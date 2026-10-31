// What the workspace works out from what it loaded: the spotlight of a chosen alert, the order alerts are read in, and
// how much of the portfolio an alert reaches. The alerts are about the real TSMC headlines (test/sampleAlerts.ts).
import { describe, expect, it } from 'vitest';
import type { ReachedHolding } from '../../api/alerts';
import { sampleAlert, tsmcSuppliesNvidia } from '../../test/sampleAlerts';
import { TSMC } from '../../test/sampleNews';
import { alertKey, byUrgency, companyIdOfNode, exposureOf, joinYears, namedIn, offGraphId, scenarioExposure, scenarioFocus, spotlightOf } from './view';

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

describe('companyIdOfNode', () => {
  it('isTheCompanyIdOfANodeOnTheGraphAndNothingForAHoldingOffTheGraph', () => {
    expect(companyIdOfNode('11')).toBe(11);
    expect(companyIdOfNode(offGraphId(7))).toBeNull();
  });
});

describe('scenarioExposure', () => {
  it('isAShareOfThePortfolioWhenTheHoldingsHaveWeightsOrACountOutOfAllTheHoldingsWhenTheyDoNot', () => {
    expect(scenarioExposure({ exposure: 42.5, weighted: true, holdingCount: 7 })).toEqual({
      value: 42.5,
      suffix: '%',
      label: 'of Portfolio',
    });
    expect(scenarioExposure({ exposure: 3, weighted: false, holdingCount: 7 })).toEqual({
      value: 3,
      suffix: '',
      label: 'of 7 Holdings',
    });
    expect(scenarioExposure({ exposure: 1, weighted: false, holdingCount: 1 }).label).toBe('of 1 Holding');
  });
});

describe('scenarioFocus', () => {
  const nodes = ['1', '2', '6', '11'].map((id) => ({ id })) as never[];
  const scenarioView = { nodes, nodeIdByHolding: view.nodeIdByHolding };
  const company = (id: number) => ({ id, name: `Company ${id}`, displayName: `Company ${id}`, ticker: null });
  const link = (id: number, companyId: number, counterpartyId: number) =>
    ({ id, company: company(companyId), counterparty: company(counterpartyId), type: 'SUPPLIER' }) as never;
  const result = (holdings: { holdingId: number; path: number[] }[], links: never[]) =>
    ({
      company: company(11),
      holdings: holdings.map((reached) => ({ ...reached, ticker: 'X', weight: null, indirect: reached.path.length > 1 })),
      links,
      criticalLinks: [],
      exposure: 0,
      weighted: false,
      holdingCount: 0,
      assumptions: '',
    }) as never;

  it('lightsTheStoppedCompanyTheHoldingsItReachesAndEveryLinkOfTheirPaths', () => {
    const focus = scenarioFocus(
      result([{ holdingId: 1, path: [1] }], [link(1, 1, 11)]),
      scenarioView,
    );

    expect(focus.nodeIds.sort()).toEqual(['1', '11']);
    expect(focus.relationshipIds).toEqual([1]);
  });

  it('lightsTheCompanyBetweenAndBothLinksOfAHoldingReachedInTwoSteps', () => {
    // 11 supplies 6 (relationship 14), which supplies 1 (relationship 1).
    const focus = scenarioFocus(
      result([{ holdingId: 1, path: [14, 1] }], [link(14, 6, 11), link(1, 1, 6)]),
      scenarioView,
    );

    expect(focus.nodeIds.sort()).toEqual(['1', '11', '6']);
    expect(focus.relationshipIds).toEqual([14, 1]);
  });

  it('lightsOnlyTheCompanyItselfWhenItIsTheHolding', () => {
    const focus = scenarioFocus({ ...(result([{ holdingId: 1, path: [] }], []) as object), company: company(1) } as never, scenarioView);

    expect(focus).toEqual({ nodeIds: ['1'], relationshipIds: [] });
  });

  it('lightsNothingBeyondTheGraphForACompanyReachingNoHolding', () => {
    expect(scenarioFocus(result([], []), scenarioView)).toEqual({ nodeIds: ['11'], relationshipIds: [] });
  });
});
