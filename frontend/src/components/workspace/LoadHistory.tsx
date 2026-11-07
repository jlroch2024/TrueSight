// Load History, in the graph's top bar after the search: a glass panel holding a quiet button. It reads each holding's
// two previous annual reports (the SEC Filing Timeline), says first how many it will read, shows how far it has got,
// and says why when it fails, with a way to retry. Once every holding is read it goes away; a holding added later is
// offered on its own.
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
      <span className="history-bar glass ticks">
        <span className="history-text">
          History Not Loaded
          <span className="history-sub" role="alert">
            {state.message}
          </span>
        </span>
        <button type="button" className="btn quiet small" onClick={onRetry}>
          Retry
        </button>
      </span>
    );
  }

  const { history } = state;
  if (history.status === 'READING' || starting) {
    const progress = history.progress;
    return (
      <span className="history-bar glass ticks" role="status">
        <LoaderCircle size={16} className="spin" />
        <span className="history-text">
          {progress ? `Reading Earlier Reports: ${progress.done} of ${progress.total}` : 'Reading Earlier Reports'}
        </span>
      </span>
    );
  }

  if (history.status === 'FAILED') {
    return (
      <span className="history-bar glass ticks">
        <span className="history-text">
          Earlier Reports Not Read
          <span className="history-sub" role="alert">
            {history.reason ?? 'Earlier reports could not be read.'}
          </span>
        </span>
        <button type="button" className="btn quiet small" onClick={onLoad}>
          Retry
        </button>
      </span>
    );
  }

  if (history.unread === 0) return null;
  // If only some of the holdings have reports left to read, the others are done: these are the holdings added since, or
  // left unfinished, and are offered on their own. The backend counts them (a holding half read counts once).
  const added = history.unreadHoldings < history.holdingCount ? history.unreadHoldings : 0;
  return (
    <span className="history-bar glass ticks">
      <button type="button" className="btn quiet" onClick={onLoad}>
        <HistoryIcon size={16} />
        <span className="history-text">
          {added > 0 ? `Load History for ${plural(added, 'New Holding', 'New Holdings')}` : 'Load History'}
          <span className="history-sub" role={startError ? 'alert' : undefined}>
            {startError ?? `Reads ${plural(history.unread, 'Earlier Report', 'Earlier Reports')}`}
          </span>
        </span>
      </button>
    </span>
  );
}
