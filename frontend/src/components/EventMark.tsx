// The marks of a news event, the same wherever an event is shown (alert cards, the Inspector, the Analyst Desk): its
// kind as an icon on a chamfered tile lit in its severity's colour, how serious as three rising bars, and how sure the
// assessment is as three small squares.
import {
  Ban,
  CloudLightning,
  Gavel,
  Landmark,
  PackageX,
  ShieldAlert,
  TriangleAlert,
  Users,
  Zap,
  type LucideIcon,
} from 'lucide-react';
import type { CSSProperties } from 'react';
import type { EventType } from '../api/alerts';
import type { Severity } from '../api/news';

const EVENT_ICON: Record<EventType, LucideIcon> = {
  PRODUCTION_STOP: Ban,
  SUPPLY_SHORTAGE: PackageX,
  LABOUR_ACTION: Users,
  NATURAL_DISASTER: CloudLightning,
  TRADE_RESTRICTION: Landmark,
  FINANCIAL_DISTRESS: Zap,
  LEGAL_OR_REGULATORY: Gavel,
  CYBER_ATTACK: ShieldAlert,
  OTHER_DISRUPTION: TriangleAlert,
};

/**
 * An event's kind, as an icon on a tile lit in its severity's colour. The chamfer marks it as something about a
 * company. Its edge is one 1px line drawn along the tile's outline, chamfer included, half a pixel inside it, so it is
 * the same thickness all the way round at any size.
 */
export function EventMark({ type, severity, size = 34 }: { type: EventType; severity: Severity; size?: number }) {
  const Icon = EVENT_ICON[type];
  const cut = size * 0.22;
  // The diagonal moved half a pixel inwards, square to itself, meets the top and right edges this far from the corner.
  const meet = cut + 0.5 * (Math.SQRT2 - 1);
  const edge = `M0.5 0.5H${size - meet}L${size - 0.5} ${meet}V${size - 0.5}H0.5Z`;
  return (
    <span className="event-mark" data-severity={severity} style={{ '--size': `${size}px` } as CSSProperties} aria-hidden="true">
      <svg className="event-mark-edge" viewBox={`0 0 ${size} ${size}`}>
        <path d={edge} />
      </svg>
      <Icon size={Math.round(size * 0.5)} strokeWidth={1.8} />
    </span>
  );
}

const LIT: Record<Severity, number> = { HIGH: 3, MEDIUM: 2, LOW: 1 };

/** How serious: three rising bars, as many lit as the severity, in its colour. */
export function SeverityBars({ severity }: { severity: Severity }) {
  return (
    <span className="sev-bars" data-severity={severity} aria-hidden="true">
      {[1, 2, 3].map((bar) => (
        <i key={bar} className={bar <= LIT[severity] ? 'on' : ''} />
      ))}
    </span>
  );
}

/** How sure: three small squares, as many filled as the confidence. */
export function Confidence({ level }: { level: Severity }) {
  return (
    <span className="confidence" aria-hidden="true">
      {[1, 2, 3].map((dot) => (
        <i key={dot} className={dot <= LIT[level] ? 'on' : ''} />
      ))}
    </span>
  );
}
