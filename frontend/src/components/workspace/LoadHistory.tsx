// The Load History button, in the graph's top bar after the search. It reads each holding's two previous annual reports
// (the SEC Filing Timeline), shows how far that has got, and says why when it fails, with a way to retry. Once every
// holding is read it goes away; a holding added later is offered on its own.
import { History as HistoryIcon, LoaderCircle } from 'lucide-react';
import { plural } from '../motion';
import type { HistoryState } from './useHistory';

export function LoadHistory({
  state,
  starting,
  startError,
  onLoad,
  onRetry,
}: {
  state: HistoryState;
  // Asking the backend to start reading.
  starting: boolean;
  // Why that could not be asked.
  startError: string | null;
  onLoad: () => void;
  // Asks how it is going again, after the history itself could not be loaded.
  onRetry: () => void;
}) {
  if (state.status === 'loading') return null;
  if (state.status === 'error') {
    return (
      <button type="button" className="history-bar glass ticks" title={state.message} onClick={onRetry}>
        <HistoryIcon size={16} />
        Retry History
      </button>
    );
  }

  const { history } = state;
  if (history.status === 'READING' || starting) {
    const progress = history.progress;
    return (
      <span className="history-bar glass ticks" role="status">
        <LoaderCircle size={16} className="spin" />
        <span className="history-text">
          Reading Earlier Reports
          {progress && <span className="history-sub">{progress.done} of {progress.total} Read</span>}
        </span>
      </span>
    );
  }

  if (history.status === 'FAILED') {
    return (
      <span className="history-bar glass ticks">
        <span className="history-text" role="alert">
          Earlier Reports Not Read
          <span className="history-sub">{history.reason ?? 'Earlier reports could not be read.'}</span>
        </span>
        <button type="button" className="btn small" onClick={onLoad}>
          Retry
        </button>
      </span>
    );
  }

  if (history.unread === 0) return null;
  // Some years already read means these are holdings added since: offer them on their own.
  const added = history.years.length > 1 ? Math.ceil(history.unread / 2) : 0;
  return (
    <button type="button" className="history-bar glass ticks" onClick={onLoad}>
      <HistoryIcon size={16} />
      <span className="history-text">
        {added > 0 ? `Load History for ${plural(added, 'New Holding', 'New Holdings')}` : 'Load History'}
        <span className="history-sub">
          {startError ?? `Reads ${plural(history.unread, 'Earlier Report', 'Earlier Reports')}`}
        </span>
      </span>
    </button>
  );
}
