// How far a portfolio's analysis has got: the ring beside its name, and each holding's status as a coloured label. And
// how close the trouble is to a holding, as a label.
import { Check, Clock, Crosshair, FileX, LoaderCircle, MoveRight, Route, TriangleAlert } from 'lucide-react';
import type { ReactNode } from 'react';
import type { HoldingStatus } from '../../api/portfolios';
import { STATUS_LABELS } from '../../format';

/**
 * A ring that fills as each holding is done. While analysis runs, a short bright arc travels round the part still to
 * fill.
 */
export function Ring({ done, total, working = false, size = 22 }: { done: number; total: number; working?: boolean; size?: number }) {
  const radius = 8;
  const length = 2 * Math.PI * radius;
  const share = total === 0 ? 0 : done / total;
  return (
    <span className={`ring-wrap${working ? ' working' : ''}${share === 1 ? ' full' : ''}`} style={{ width: size, height: size }}>
      <svg className="ring" viewBox="0 0 22 22" aria-hidden="true">
        <circle className="track" cx="11" cy="11" r={radius} />
        <circle className="done" cx="11" cy="11" r={radius} strokeDasharray={length} strokeDashoffset={length * (1 - share)} />
      </svg>
      {working && (
        <svg className="ring comet" viewBox="0 0 22 22" aria-hidden="true">
          <circle cx="11" cy="11" r={radius} strokeDasharray={`${length * 0.16} ${length}`} />
        </svg>
      )}
    </span>
  );
}

const LOOK: Record<HoldingStatus, { tone: string; icon: ReactNode }> = {
  DONE: { tone: 'done', icon: <Check size={14} strokeWidth={2.4} /> },
  ANALYSING: { tone: 'working', icon: <LoaderCircle size={14} className="spin" /> },
  WAITING: { tone: 'waiting', icon: <Clock size={14} /> },
  FAILED: { tone: 'failed', icon: <TriangleAlert size={14} /> },
  NO_REPORT_FOUND: { tone: 'none', icon: <FileX size={14} /> },
};

/** A holding's analysis status, as a coloured label with its icon. */
export function State({ status }: { status: HoldingStatus }) {
  const look = LOOK[status];
  return (
    <span className={`state ${look.tone}`}>
      {look.icon}
      {STATUS_LABELS[status]}
    </span>
  );
}

/**
 * How close the trouble is to a holding: Direct is one link from the company in question, Indirect is two, and Itself is
 * the company in question. Drawn in the colour of what it belongs to (the --tone around it).
 */
export function ReachKind({ indirect, itself = false }: { indirect: boolean; itself?: boolean }) {
  if (itself) {
    return (
      <span className="reach-kind" title="This Holding Is the Company in Question">
        <Crosshair size={13} aria-hidden="true" />
        Itself
      </span>
    );
  }
  return indirect ? (
    <span className="reach-kind indirect" title="Two Links Away, Through Another Company">
      <Route size={13} aria-hidden="true" />
      Indirect
    </span>
  ) : (
    <span className="reach-kind" title="One Link Away, With Nothing Between">
      <MoveRight size={13} aria-hidden="true" />
      Direct
    </span>
  );
}
