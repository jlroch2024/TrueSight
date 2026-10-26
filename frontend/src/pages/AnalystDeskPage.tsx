// The Analyst Desk, at /review: every analyst tool, each as a tab. Sprint 3 stories add their own tab to TABS; they never
// add a page.
//
// The open tab is in the address (/review?tab=relationships), so it survives a reload and can be shared. An address
// with no tab, or one this page does not know, opens the first tab. Tabs are buttons with role="tab": Left and Right
// move between them, Home and End jump to the first and last.
import { type KeyboardEvent, type ReactNode, useRef } from 'react';
import { useSearchParams } from 'react-router';
import { AiCallsPage } from './AiCallsPage';
import { ReviewPage } from './ReviewPage';
import { RecommendationsPage } from './RecommendationsPage';

type Tab = { id: string; label: string; content: () => ReactNode };

const TABS: Tab[] = [
  // Today's Review page, unchanged: the Easier Review story edits ReviewPage.tsx itself.
  { id: 'relationships', label: 'Relationships', content: () => <ReviewPage /> },
  { id: 'recommendations', label: 'Recommendations', content: () => <RecommendationsPage /> },
  { id: 'ai-calls', label: 'AI Calls', content: () => <AiCallsPage /> },
];

export function AnalystDeskPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);
  const open = TABS.find((tab) => tab.id === searchParams.get('tab')) ?? TABS[0]!;

  function choose(index: number) {
    const tab = TABS[index]!;
    setSearchParams({ tab: tab.id });
    buttons.current[index]?.focus();
  }

  function onKeyDown(event: KeyboardEvent, index: number) {
    const last = TABS.length - 1;
    const next: Record<string, number> = {
      ArrowRight: index === last ? 0 : index + 1,
      ArrowLeft: index === 0 ? last : index - 1,
      Home: 0,
      End: last,
    };
    if (event.key in next) {
      event.preventDefault();
      choose(next[event.key]!);
    }
  }

  return (
    <section>
      <h1>Analyst Desk</h1>
      <div className="desk-tabs" role="tablist" aria-label="Analyst Desk">
        {TABS.map((tab, index) => {
          const selected = tab.id === open.id;
          return (
            <button
              key={tab.id}
              ref={(button) => {
                buttons.current[index] = button;
              }}
              id={`desk-tab-${tab.id}`}
              type="button"
              role="tab"
              aria-selected={selected}
              aria-controls={`desk-panel-${tab.id}`}
              tabIndex={selected ? 0 : -1}
              className={selected ? 'desk-tab active' : 'desk-tab'}
              onClick={() => choose(index)}
              onKeyDown={(event) => onKeyDown(event, index)}
            >
              {tab.label}
            </button>
          );
        })}
      </div>
      <div id={`desk-panel-${open.id}`} role="tabpanel" aria-labelledby={`desk-tab-${open.id}`}>
        {open.content()}
      </div>
    </section>
  );
}
