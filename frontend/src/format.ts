import type { EventType } from './api/alerts';
import type { HoldingStatus } from './api/portfolios';

// How each holding status reads on the page.
export const STATUS_LABELS: Record<HoldingStatus, string> = {
  WAITING: 'Waiting',
  ANALYSING: 'Analysing',
  DONE: 'Done',
  FAILED: 'Failed',
  NO_REPORT_FOUND: 'No Report Found',
};

// One date style on every page and in every browser language: "Oct 18, 2026" and "Oct 18, 2026, 4:55 AM".
// How severity and confidence read: each Low, Medium or High.
export const SEVERITY_LABELS = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' } as const;

// How each event type reads, in Title Case with "or" in lower case.
export const EVENT_TYPE_LABELS: Record<EventType, string> = {
  PRODUCTION_STOP: 'Production Stop',
  SUPPLY_SHORTAGE: 'Supply Shortage',
  LABOUR_ACTION: 'Labour Action',
  NATURAL_DISASTER: 'Natural Disaster',
  TRADE_RESTRICTION: 'Trade Restriction',
  FINANCIAL_DISTRESS: 'Financial Distress',
  LEGAL_OR_REGULATORY: 'Legal or Regulatory',
  CYBER_ATTACK: 'Cyber Attack',
  OTHER_DISRUPTION: 'Other Disruption',
};

export function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

/**
 * A date with no time, "Oct 18, 2026". A date-only string such as a filing date, "2026-02-24", is read as that calendar
 * day; new Date() would read it as midnight UTC, which is the day before west of Greenwich.
 */
export function formatDate(value: string): string {
  const calendarDay = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  const date = calendarDay
    ? new Date(Number(calendarDay[1]), Number(calendarDay[2]) - 1, Number(calendarDay[3]))
    : new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' }).format(date);
}

const SMALL_WORDS = new Set(['a', 'an', 'and', 'as', 'at', 'by', 'for', 'in', 'of', 'on', 'or', 'the', 'to', 'with']);

/**
 * Text as a label: "lithium-ion battery cells" becomes "Lithium-Ion Battery Cells". Small words such as "and" and "of"
 * stay lower case unless they come first. No letter is made lower case, so "AMD GPUs" and "SoC" keep their capitals.
 */
export function titleCase(text: string): string {
  return text
    .split(' ')
    .map((word, i) =>
      i > 0 && SMALL_WORDS.has(word)
        ? word
        : word
            .split('-')
            .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
            .join('-'),
    )
    .join(' ');
}

/**
 * How long ago something happened, in words: "Just Now", "5 Minutes Ago", "1 Hour Ago", "2 Days Ago". Past a week,
 * the date itself.
 */
export function formatAge(value: string, now: number = Date.now()): string {
  const then = new Date(value).getTime();
  if (Number.isNaN(then)) return '—';
  const minutes = Math.floor((now - then) / 60_000);
  if (minutes < 1) return 'Just Now';
  const ago = (count: number, unit: string) => `${count} ${unit}${count === 1 ? '' : 's'} Ago`;
  if (minutes < 60) return ago(minutes, 'Minute');
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return ago(hours, 'Hour');
  const days = Math.floor(hours / 24);
  if (days <= 7) return ago(days, 'Day');
  return formatDate(value);
}

export function openInNewTab(url: string): void {
  window.open(url, '_blank', 'noopener,noreferrer');
}

/**
 * The address that opens a report on the SEC website scrolled to the evidence, highlighted, using a Text Fragment
 * (https://wicg.github.io/scroll-to-text-fragment/). If the browser cannot find the sentence, or does not support
 * Text Fragments, the report simply opens at the top.
 *
 * Evidence of 10 words or fewer is used whole. Longer evidence uses only its first 5 and last 5 words, so the fragment
 * still matches when the SEC's own formatting puts something unexpected in between, such as a line break.
 */
export function showInReportUrl(reportUrl: string, evidence: string): string {
  const words = evidence.trim().split(/\s+/);
  const fragment =
    words.length <= 10
      ? encodeForFragment(words.join(' '))
      : `${encodeForFragment(words.slice(0, 5).join(' '))},${encodeForFragment(words.slice(-5).join(' '))}`;
  return `${reportUrl}#:~:text=${fragment}`;
}

// encodeURIComponent leaves "-" as it is, but a Text Fragment uses "-" to separate a prefix or suffix from the quoted
// text, so a hyphen inside the quote itself must be escaped too, or the browser misreads where the quote ends.
function encodeForFragment(text: string): string {
  return encodeURIComponent(text).replaceAll('-', '%2D');
}
