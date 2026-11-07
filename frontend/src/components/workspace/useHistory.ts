// A portfolio's SEC Filing Timeline: what has been read, and where reading earlier reports has got. Asked for when the
// workspace opens, and again every few seconds while earlier reports are being read.
import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { type History, historyApi } from '../../api/history';

/** How often progress is asked for while earlier reports are being read, in milliseconds. */
export const HISTORY_POLL_MS = 2000;

export type HistoryState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; history: History };

/**
 * {@code analysed} is how many of the portfolio's holdings are analysed. Only analysed holdings have earlier reports to
 * read, so the history is asked for again whenever that changes (an analysis finishing, a holding added or removed),
 * without blanking what is shown meanwhile.
 */
export function useHistory(portfolioId: string | undefined, analysed: number) {
  const [state, setState] = useState<HistoryState>({ status: 'loading' });
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!portfolioId) return;
    try {
      setState({ status: 'ready', history: await historyApi.get(portfolioId) });
    } catch (error) {
      setState({ status: 'error', message: error instanceof ApiError ? error.message : 'The history could not be loaded.' });
    }
  }, [portfolioId]);

  // A different portfolio: nothing is known about it yet.
  useEffect(() => {
    setState({ status: 'loading' });
  }, [portfolioId]);

  useEffect(() => {
    void refresh();
  }, [refresh, analysed]);

  const reading = state.status === 'ready' && state.history.status === 'READING';
  useEffect(() => {
    if (!reading) return;
    const timer = window.setInterval(() => void refresh(), HISTORY_POLL_MS);
    return () => window.clearInterval(timer);
  }, [reading, refresh]);

  /** Reads every holding's missing earlier reports, then shows how it is going. */
  const load = useCallback(async () => {
    if (!portfolioId) return;
    setStarting(true);
    setStartError(null);
    try {
      await historyApi.load(portfolioId);
    } catch (error) {
      setStartError(error instanceof ApiError ? error.message : 'Earlier reports could not be loaded.');
    } finally {
      setStarting(false);
    }
    await refresh();
  }, [portfolioId, refresh]);

  return { state, starting, startError, load, refresh };
}
