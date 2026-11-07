// A relationship's evidence with the words that matter marked, and a one-line key under it. It never searches the text:
// the ranges come from the API, which checked each one against the report's own words. With none, the quote is plain and
// has no key, so a quote not read yet shows exactly as it always has.
import type { CSSProperties, ReactNode } from 'react';
import type { Highlight, HighlightKind } from '../api/relationships';

const KINDS: { kind: HighlightKind; label: string }[] = [
  { kind: 'COMPANY', label: 'Company' },
  { kind: 'SUPPLIED', label: 'Supplied' },
  { kind: 'RELIANCE', label: 'Reliance' },
  { kind: 'FIGURE', label: 'Figure' },
];

function kindClass(kind: HighlightKind): string {
  return `quote-mark ${kind.toLowerCase()}`;
}

/** The text with each range in a mark of its kind. The API sends the ranges in order, and they never overlap. */
function marked(text: string, highlights: Highlight[]): ReactNode[] {
  const parts: ReactNode[] = [];
  let at = 0;
  for (const { kind, start, end } of highlights) {
    parts.push(text.slice(at, start));
    parts.push(
      <mark key={start} className={kindClass(kind)}>
        {text.slice(start, end)}
      </mark>,
    );
    at = end;
  }
  parts.push(text.slice(at));
  return parts;
}

export function HighlightedQuote({
  text,
  highlights,
  companyColour,
  children,
}: {
  text: string;
  highlights: Highlight[];
  /** The chain colour of the holding the relationship belongs to. A quote with none marks companies in teal. */
  companyColour?: string;
  /** Anything else inside the quote, below its words, such as which report it is from. */
  children?: ReactNode;
}) {
  const style = companyColour ? ({ '--quote-company': companyColour } as CSSProperties) : undefined;
  return (
    <div className="highlighted-quote" style={style}>
      <blockquote className="evidence">
        {marked(text, highlights)}
        {children}
      </blockquote>
      {highlights.length > 0 && (
        <p className="quote-key" aria-label="Highlight Key">
          {KINDS.map(({ kind, label }) => (
            <span key={kind} className={kindClass(kind)}>
              {label}
            </span>
          ))}
        </p>
      )}
    </div>
  );
}
