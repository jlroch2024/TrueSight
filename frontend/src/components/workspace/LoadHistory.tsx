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
    const reading = progress ? `Reading Earlier Reports: ${progress.done} of ${progress.total}` : 'Reading Earlier Reports';
    // Where the bar is crowded, only the spinner shows (styles.css): the words stay as its tooltip.
    return (
      <span className="history-bar history-folds glass ticks" role="status" title={reading}>
        <LoaderCircle size={16} className="spin" />
        <span className="history-text">{reading}</span>
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
  const label = added > 0 ? `Load History for ${plural(added, 'New Holding', 'New Holdings')}` : 'Load History';
  const cost = `Reads ${plural(history.unread, 'Earlier Report', 'Earlier Reports')}`;
  // Where the bar is crowded, only the icon shows (styles.css), as Open Alerts shows only its count: the words stay as
  // the button's tooltip and name. A start error is never folded away.
  return (
    <span className={`history-bar glass ticks${startError ? '' : ' history-folds'}`}>
      <button type="button" className="btn quiet" title={`${label}: ${cost}`} onClick={onLoad}>
        <HistoryIcon size={16} />
        <span className="history-text">
          {label}
          <span className="history-sub" role={startError ? 'alert' : undefined}>
            {startError ?? cost}
          </span>
        </span>
      </button>
    </span>
  );
}
