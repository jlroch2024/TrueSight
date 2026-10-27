// The Analyst Desk's Quality tab: how often analysts agreed with each AI step, week by week for the last 8 weeks.
//
// Drawn without a chart library: each bar is a row of divs whose widths are percentages, with the figures written
// beside it. A week with nothing to count says "No Decisions" (or "No Calls"), never 0%.
import { type ReactNode, useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { type Quality, qualityApi } from '../api/quality';

type Tone = 'teal' | 'lime' | 'amber' | 'danger' | 'muted';
type Segment = { label: string; count: number; tone: Tone };

const DAY = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric' });

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong.';
}

/** "2026-10-21" to "2026-10-27" reads "Oct 21 – Oct 27". Each date is read as that calendar day. */
function weekLabel(start: string, end: string): string {
  const day = (value: string) => {
    const [year, month, date] = value.split('-').map(Number);
    return DAY.format(new Date(year!, month! - 1, date!));
  };
  return `${day(start)} – ${day(end)}`;
}

function percent(count: number, total: number): string {
  return `${Math.round((count / total) * 100)}%`;
}

function duration(milliseconds: number): string {
  return milliseconds < 1000 ? `${milliseconds} ms` : `${(milliseconds / 1000).toFixed(1)} s`;
}

/**
 * One bar, its segments sized as a share of the total, with the figures beside it: the lead figure if there is one, each
 * segment's share, then the total.
 */
function Bar(props: { segments: Segment[]; total: number; totalLabel: string; lead?: string }) {
  const { segments, total, totalLabel, lead } = props;
  return (
    <div className="quality-measure">
      <div className="quality-bar" aria-hidden="true">
        {segments.map((segment) => (
          <div
            key={segment.label}
            className={`quality-segment ${segment.tone}`}
            data-label={segment.label}
            style={{ width: `${(segment.count / total) * 100}%` }}
          />
        ))}
      </div>
      <p className="quality-figures">
        {[lead, ...segments.map((segment) => `${percent(segment.count, total)} ${segment.label}`)]
          .filter(Boolean)
          .join(' · ')}
        <span className="muted"> · {totalLabel}</span>
      </p>
    </div>
  );
}

function Legend({ items }: { items: { label: string; tone: Tone }[] }) {
  return (
    <ul className="quality-legend">
      {items.map((item) => (
        <li key={item.label}>
          <span className={`quality-swatch ${item.tone}`} aria-hidden="true" />
          {item.label}
        </li>
      ))}
    </ul>
  );
}

function Section(props: {
  id: string;
  title: string;
  explanation: string;
  legend: { label: string; tone: Tone }[];
  columns: string[];
  rows: { week: string; cells: ReactNode }[];
}) {
  return (
    <section className="glass ticks quality-section" aria-labelledby={`quality-${props.id}`}>
      <h2 id={`quality-${props.id}`}>{props.title}</h2>
      <p className="muted">{props.explanation}</p>
      <Legend items={props.legend} />
      <div className="table-scroll">
        <table className="quality-table">
          <thead>
            <tr>
              <th scope="col">Week</th>
              {props.columns.map((column) => (
                <th key={column} scope="col">{column}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {props.rows.map((row) => (
              <tr key={row.week}>
                <th scope="row">{row.week}</th>
                {row.cells}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function Nothing({ text, columns }: { text: string; columns: number }) {
  return (
    <td colSpan={columns} className="muted">
      {text}
    </td>
  );
}

function hasAnything(quality: Quality): boolean {
  return (
    quality.relationships.some((week) => week.reviewed > 0) ||
    quality.news.some((week) => week.decided > 0) ||
    quality.recommendations.some((week) => week.decided > 0) ||
    quality.calls.some((week) => week.answered + week.unreadable + week.failed > 0)
  );
}

function Dashboard({ quality }: { quality: Quality }) {
  return (
    <>
      <Section
        id="relationships"
        title="Finding Relationships"
        explanation="Of the relationships the AI found and an analyst reviewed, how many were approved or rejected, and how often a Second Check doubt was then rejected."
        legend={[
          { label: 'Approved', tone: 'teal' },
          { label: 'Rejected', tone: 'danger' },
        ]}
        columns={['Approved and Rejected', 'Doubts Later Rejected']}
        rows={quality.relationships.map((week) => ({
          week: weekLabel(week.weekStart, week.weekEnd),
          cells:
            week.reviewed === 0 ? (
              <Nothing text="No Decisions" columns={2} />
            ) : (
              <>
                <td>
                  <Bar
                    total={week.reviewed}
                    totalLabel={`${week.reviewed} Reviewed`}
                    segments={[
                      { label: 'Approved', count: week.approved, tone: 'teal' },
                      { label: 'Rejected', count: week.rejected, tone: 'danger' },
                    ]}
                  />
                </td>
                <td>
                  {week.doubtsReviewed === 0 ? (
                    <span className="muted">No Doubts Reviewed</span>
                  ) : (
                    <Bar
                      total={week.doubtsReviewed}
                      totalLabel={`${week.doubtsRejected} of ${week.doubtsReviewed} Doubts`}
                      segments={[{ label: 'Rejected', count: week.doubtsRejected, tone: 'danger' }]}
                    />
                  )}
                </td>
              </>
            ),
        }))}
      />

      <Section
        id="news"
        title="Judging News"
        explanation="Of the articles an analyst confirmed or overruled, how many overruled the AI's judgement, and which way."
        legend={[
          { label: 'Noise Made Events', tone: 'lime' },
          { label: 'Events Made Noise', tone: 'amber' },
          { label: 'Type or Severity Changed', tone: 'muted' },
        ]}
        columns={['Overruled']}
        rows={quality.news.map((week) => ({
          week: weekLabel(week.weekStart, week.weekEnd),
          cells:
            week.decided === 0 ? (
              <Nothing text="No Decisions" columns={1} />
            ) : (
              <td>
                <Bar
                  total={week.decided}
                  lead={`${percent(week.overruled, week.decided)} Overruled`}
                  totalLabel={`${week.decided} Decided`}
                  segments={[
                    { label: 'Noise Made Events', count: week.noiseMadeEvents, tone: 'lime' },
                    { label: 'Events Made Noise', count: week.eventsMadeNoise, tone: 'amber' },
                    {
                      label: 'Type or Severity Changed',
                      count: week.overruled - week.noiseMadeEvents - week.eventsMadeNoise,
                      tone: 'muted',
                    },
                  ]}
                />
              </td>
            ),
        }))}
      />

      <Section
        id="recommendations"
        title="Recommendations"
        explanation="Of the suggested next steps decided, how many were approved as written, edited, or withheld."
        legend={[
          { label: 'Approved as Written', tone: 'teal' },
          { label: 'Edited', tone: 'lime' },
          { label: 'Withheld', tone: 'danger' },
        ]}
        columns={['Decisions']}
        rows={quality.recommendations.map((week) => ({
          week: weekLabel(week.weekStart, week.weekEnd),
          cells:
            week.decided === 0 ? (
              <Nothing text="No Decisions" columns={1} />
            ) : (
              <td>
                <Bar
                  total={week.decided}
                  totalLabel={`${week.decided} Decided`}
                  segments={[
                    { label: 'Approved as Written', count: week.approvedAsWritten, tone: 'teal' },
                    { label: 'Edited', count: week.edited, tone: 'lime' },
                    { label: 'Withheld', count: week.withheld, tone: 'danger' },
                  ]}
                />
              </td>
            ),
        }))}
      />

      <Section
        id="calls"
        title="Calls"
        explanation="Every call to Gemini, by how it ended, and the median time a call took."
        legend={[
          { label: 'Answered', tone: 'teal' },
          { label: 'Unreadable', tone: 'amber' },
          { label: 'Failed', tone: 'danger' },
        ]}
        columns={['Outcome', 'Median Time']}
        rows={quality.calls.map((week) => {
          const total = week.answered + week.unreadable + week.failed;
          return {
            week: weekLabel(week.weekStart, week.weekEnd),
            cells:
              total === 0 ? (
                <Nothing text="No Calls" columns={2} />
              ) : (
                <>
                  <td>
                    <Bar
                      total={total}
                      totalLabel={`${total} ${total === 1 ? 'Call' : 'Calls'}`}
                      segments={[
                        { label: 'Answered', count: week.answered, tone: 'teal' },
                        { label: 'Unreadable', count: week.unreadable, tone: 'amber' },
                        { label: 'Failed', count: week.failed, tone: 'danger' },
                      ]}
                    />
                  </td>
                  <td className="quality-median">{week.medianMs === null ? '—' : duration(week.medianMs)}</td>
                </>
              ),
          };
        })}
      />
    </>
  );
}

export function QualityPage() {
  const [quality, setQuality] = useState<Quality>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    qualityApi
      .get()
      .then((found) => {
        if (active) setQuality(found);
      })
      .catch((reason: unknown) => {
        if (active) setError(messageOf(reason));
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <section className="quality">
      {error ? (
        <p className="error">{error}</p>
      ) : quality === undefined ? (
        <p className="muted">Loading…</p>
      ) : !hasAnything(quality) ? (
        <p className="muted">
          Nothing was decided and no AI call was made in the last 8 weeks. Review relationships, events and
          recommendations on the other tabs, and the AI's track record appears here.
        </p>
      ) : (
        <Dashboard quality={quality} />
      )}
    </section>
  );
}
