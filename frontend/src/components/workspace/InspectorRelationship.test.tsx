import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Relationship } from '../../api/relationships';
import { UNREVIEWED_CONFIDENCE } from '../../test/sampleConfidence';
import { RelationshipView } from './InspectorRelationship';
import type { WorkspaceView } from './view';

/** NVIDIA's own words, from its 10-K in docs/examples. */
const EVIDENCE = 'We purchase memory from SK Hynix Inc., Micron Technology, Inc., and Samsung.';

function at(phrase: string) {
  const start = EVIDENCE.indexOf(phrase);
  return { start, end: start + phrase.length };
}

function relationship(highlights: Relationship['highlights']): Relationship {
  return {
    id: 1,
    company: { id: 1, name: 'NVIDIA CORP', displayName: 'NVIDIA', ticker: 'NVDA' },
    counterparty: { id: 2, name: 'SK Hynix Inc.', displayName: 'SK Hynix', ticker: null },
    type: 'SUPPLIER',
    provides: 'memory',
    evidence: EVIDENCE,
    source: 'AI',
    foundBy: 'REPORT',
    confidence: UNREVIEWED_CONFIDENCE,
    highlights,
    criticality: null,
    criticalityEvidence: null,
    report: { form: '10-K', filingDate: '2026-02-25', url: 'https://www.sec.gov/report-1' },
    reviewStatus: 'UNREVIEWED',
    reviewedAt: null,
    reviewNote: null,
  };
}

function renderView(found: Relationship, colourOf = new Map<string, string>()) {
  const view = { relationships: [found], colourOf, holdingOfNode: new Map() } as unknown as WorkspaceView;
  return render(<RelationshipView view={view} relationshipId={found.id} onChoose={() => {}} />);
}

describe('The Inspector’s relationship view', () => {
  it('shows each highlight in its kind’s style, with the key below the quote', () => {
    const { container } = renderView(
      relationship([
        { kind: 'SUPPLIED', ...at('memory') },
        { kind: 'COMPANY', ...at('SK Hynix Inc.') },
      ]),
    );

    const marks = [...container.querySelectorAll('blockquote mark')];
    expect(marks.map((mark) => mark.textContent)).toEqual(['memory', 'SK Hynix Inc.']);
    expect(marks[0]).toHaveClass('supplied');
    expect(marks[1]).toHaveClass('company');
    const key = screen.getByLabelText('Highlight Key');
    expect(key).toHaveTextContent('CompanySuppliedRelianceFigure');
    expect(container.querySelector('blockquote')!.compareDocumentPosition(key)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  it('marks companies in the chain colour of the holding the relationship belongs to', () => {
    const { container } = renderView(
      relationship([{ kind: 'COMPANY', ...at('SK Hynix Inc.') }]),
      new Map([['1', '#a78bfa']]),
    );

    expect(container.querySelector<HTMLElement>('.highlighted-quote')!.style.getPropertyValue('--quote-company')).toBe('#a78bfa');
  });

  it('shows a quote not read yet plain, with its report, and no key', () => {
    const { container } = renderView(relationship([]));

    const quote = container.querySelector('blockquote')!;
    expect(quote).toHaveTextContent(EVIDENCE);
    expect(quote).toHaveTextContent('From NVIDIA’s 10-K');
    expect(quote.querySelector('mark')).toBeNull();
    expect(screen.queryByLabelText('Highlight Key')).not.toBeInTheDocument();
  });
});
