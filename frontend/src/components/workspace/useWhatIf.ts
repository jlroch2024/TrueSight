// Asks the backend what happens if a company stops supplying, while the Inspector shows that scenario. The workspace
// holds the answer, because the graph lights up from it as well as the Inspector reading it.
import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { type WhatIf, whatIfApi } from '../../api/whatIf';

export type Scenario =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; result: WhatIf };

/** The scenario for this company in this portfolio, or nothing while {@code companyId} is null (none is open). */
export function useWhatIf(portfolioId: string | undefined, companyId: number | null) {
  const [scenario, setScenario] = useState<Scenario>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!portfolioId || companyId === null) return;
    let current = true;
    setScenario({ status: 'loading' });
    whatIfApi
      .get(portfolioId, companyId)
      .then((result) => current && setScenario({ status: 'ready', result }))
      .catch((error) => {
        if (!current) return;
        const message = error instanceof ApiError ? error.message : 'The scenario could not be worked out. Please try again.';
        setScenario({ status: 'error', message });
      });
    return () => {
      current = false;
    };
  }, [portfolioId, companyId, attempt]);

  return { scenario, retry: () => setAttempt((count) => count + 1) };
}
