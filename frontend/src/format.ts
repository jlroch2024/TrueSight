import type { HoldingStatus } from './api/portfolios';

// How each holding status reads on the page.
export const STATUS_LABELS: Record<HoldingStatus, string> = {
  WAITING: 'Waiting',
  ANALYSING: 'Analysing',
  DONE: 'Done',
  FAILED: 'Failed',
  NO_REPORT_FOUND: 'No Report Found',
};

export function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
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
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(then);
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
