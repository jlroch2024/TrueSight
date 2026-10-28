// The Analyst Desk, at /review: every analyst tool, each a tile with its own colour saying how much waits there, and
// the open tool's work below. Relationships, Events and Recommendations are review queues, each decided with Approve
// and Reject; AI Calls is the record of every Gemini call. Sprint 3 and 4 stories add their own tool to TOOLS; they
// never add a page.
//
// The open tool is in the address (/review?tab=relationships), so it survives a reload and can be shared. An address
// with no tab, or one this page does not know, opens the first. The tiles are tabs: Left and Right move between them,
// Home and End jump to the first and last.
import { AnimatePresence, type Variants, motion } from 'motion/react';
import { ArrowLeft, Gauge, Lightbulb, Link2, Radar, ScrollText, ShieldCheck, type LucideIcon } from 'lucide-react';
import { type CSSProperties, type KeyboardEvent, type ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router';
import { aiCallApi } from '../api/aiCalls';
import { newsReviewApi } from '../api/newsReview';
import { type Portfolio, portfolioApi } from '../api/portfolios';
import { qualityApi } from '../api/quality';
import { recommendationApi } from '../api/recommendations';
import { reviewApi } from '../api/reviews';
import { AccountMenu } from '../components/AccountMenu';
import { Count } from '../components/Count';
import { Brand } from '../components/PageFrame';
import { useSlidingPlate } from '../components/useSlidingPlate';
import { paths } from '../paths';
import { AiCallsPage } from './AiCallsPage';
import { EventReviewPage } from './EventReviewPage';
import { QualityPage } from './QualityPage';
import { RecommendationsPage } from './RecommendationsPage';
import { ReviewPage } from './ReviewPage';

type ToolId = 'relationships' | 'events' | 'recommendations' | 'ai-calls' | 'quality';

type Tool = {
  id: ToolId;
  label: string;
  icon: LucideIcon;
  // Each tool's own colour: the desk's accent eases to it while it is open.
  colour: string;
  group: 'Review' | 'Record';
  unit: string;
  // Written after the figure, for a share ("86%").
  suffix?: string;
  // onChanged: the tool calls it after a decision, so the tiles' counts follow.
  content: (onChanged: () => void) => ReactNode;
};

const TOOLS: Tool[] = [
  { id: 'relationships', label: 'Relationships', icon: Link2, colour: '#2dd4bf', group: 'Review', unit: 'Waiting', content: (onChanged) => <ReviewPage onChanged={onChanged} /> },
  { id: 'events', label: 'Events', icon: Radar, colour: '#ff8a3d', group: 'Review', unit: 'Waiting', content: (onChanged) => <EventReviewPage onChanged={onChanged} /> },
  { id: 'recommendations', label: 'Recommendations', icon: Lightbulb, colour: '#c4f36a', group: 'Review', unit: 'Waiting', content: (onChanged) => <RecommendationsPage onChanged={onChanged} /> },
  { id: 'ai-calls', label: 'AI Calls', icon: ScrollText, colour: '#a5b4fc', group: 'Record', unit: 'Today', content: () => <AiCallsPage /> },
  { id: 'quality', label: 'Quality', icon: Gauge, colour: '#7dd3fc', group: 'Record', unit: 'Agreed', suffix: '%', content: () => <QualityPage /> },
];

// A tool's work: the old one goes quickly, the new one comes in from the side its tile lies on.
const SLIDE: Variants = {
  enter: (direction: number) => ({ opacity: 0, x: direction * 28 }),
  shown: { opacity: 1, x: 0, transition: { duration: 0.34, ease: [0.22, 1, 0.36, 1] } },
  leave: (direction: number) => ({ opacity: 0, x: direction * -18, transition: { duration: 0.14, ease: [0.4, 0, 1, 1] } }),
};

/** Today's date as the AI Calls filter takes it, in the browser's own time zone: "2026-10-28". */
function today(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

/** How much waits in each tool; a count that could not be loaded is left out (its tile shows a dash). */
function useToolCounts() {
  const [counts, setCounts] = useState<Partial<Record<ToolId, number>>>({});
  const load = useCallback(() => {
    const count = (id: ToolId, ask: () => Promise<number>) =>
      ask()
        .then((value) => setCounts((all) => ({ ...all, [id]: value })))
        .catch(() => {});
    void count('relationships', () => reviewApi.list('UNREVIEWED').then((items) => items.length));
    void count('events', () => newsReviewApi.list('EVENTS').then((items) => items.length));
    void count('recommendations', () => recommendationApi.list().then((items) => items.length));
    // Quality: how often analysts agreed with the AI's relationships, in the latest week that had reviews.
    void count('quality', () =>
      qualityApi.get().then((quality) => {
        const week = [...(quality?.relationships ?? [])].reverse().find((found) => found.reviewed > 0);
        if (!week) throw new Error('No reviews yet.');
        return Math.round((week.approved / week.reviewed) * 100);
      }),
    );
    void count('ai-calls', () =>
      aiCallApi.list({ purpose: '', outcome: '', from: today(), to: '' }, 0).then((page) => {
        if (typeof page?.total !== 'number') throw new Error('No count.');
        return page.total;
      }),
    );
  }, []);
  useEffect(load, [load]);
  return { counts, reload: load };
}

export function AnalystDeskPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);
  const index = Math.max(0, TOOLS.findIndex((tool) => tool.id === searchParams.get('tab')));
  const open = TOOLS[index]!;
  const before = useRef(index);
  const direction = index >= before.current ? 1 : -1;
  useEffect(() => {
    before.current = index;
  }, [index]);
  const plate = useSlidingPlate<HTMLDivElement>(open.id);
  const { counts, reload } = useToolCounts();

  function choose(next: number) {
    setSearchParams({ tab: TOOLS[next]!.id });
    buttons.current[next]?.focus();
  }

  function onKeyDown(event: KeyboardEvent, at: number) {
    const last = TOOLS.length - 1;
    const next: Record<string, number> = {
      ArrowRight: at === last ? 0 : at + 1,
      ArrowLeft: at === 0 ? last : at - 1,
      Home: 0,
      End: last,
    };
    if (event.key in next) {
      event.preventDefault();
      choose(next[event.key]!);
    }
  }

  return (
    <div className="desk" style={{ '--accent': open.colour } as CSSProperties}>
      <header className="island left glass ticks">
        <Brand />
        <span className="island-divider" />
        <BackToPortfolio />
      </header>
      <div className="island right glass ticks">
        <span className="chip lime desk-role"><ShieldCheck size={13} /> Analyst</span>
        <AccountMenu />
      </div>

      <main className="desk-main">
        <div className="desk-inner">
          <div className="desk-title">
            <h1>Analyst Desk</h1>
          </div>

          <div ref={plate.container} className="tools" role="tablist" aria-label="Analyst Desk">
            <span ref={plate.plate} className="tool-plate" aria-hidden="true" />
            {(['Review', 'Record'] as const).map((group) => (
              <div key={group} className={`tool-group ${group.toLowerCase()}`}>
                <span className="tool-group-label">{group === 'Review' ? 'Review Queues' : 'Record'}</span>
                <div className="tool-row">
                  {TOOLS.map((tool, at) => {
                    if (tool.group !== group) return null;
                    const selected = tool.id === open.id;
                    const Icon = tool.icon;
                    const count = counts[tool.id];
                    return (
                      <button
                        key={tool.id}
                        ref={(button) => {
                          buttons.current[at] = button;
                        }}
                        id={`desk-tab-${tool.id}`}
                        type="button"
                        role="tab"
                        aria-label={tool.label}
                        aria-selected={selected}
                        aria-controls={`desk-panel-${tool.id}`}
                        tabIndex={selected ? 0 : -1}
                        data-chosen={selected}
                        className="tool"
                        style={{ '--tool': tool.colour } as CSSProperties}
                        onClick={() => choose(at)}
                        onKeyDown={(event) => onKeyDown(event, at)}
                      >
                        <span className="tool-icon"><Icon size={18} /></span>
                        <span className="tool-name">{tool.label}</span>
                        <span className="tool-figure">
                          <strong className="num">{count === undefined ? '—' : <Count value={count} suffix={tool.suffix} />}</strong>
                          <small>{tool.unit}</small>
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>

          <AnimatePresence mode="wait" initial={false} custom={direction}>
            <motion.section
              key={open.id}
              id={`desk-panel-${open.id}`}
              role="tabpanel"
              aria-labelledby={`desk-tab-${open.id}`}
              className="desk-panel"
              custom={direction}
              variants={SLIDE}
              initial="enter"
              animate="shown"
              exit="leave"
            >
              {open.content(reload)}
            </motion.section>
          </AnimatePresence>
        </div>
      </main>
    </div>
  );
}

/**
 * Back to the portfolio the analyst came from (the workspace's Analyst Desk button says which), or their first one.
 * With no portfolio at all, to /portfolios, which asks for a first.
 */
function BackToPortfolio() {
  const navigate = useNavigate();
  const from = (useLocation().state as { fromPortfolio?: string } | null)?.fromPortfolio;
  const [portfolios, setPortfolios] = useState<Portfolio[] | null>(null);
  useEffect(() => {
    let active = true;
    portfolioApi
      .list()
      .then((all) => {
        if (active) setPortfolios(all);
      })
      .catch(() => {
        if (active) setPortfolios([]);
      });
    return () => {
      active = false;
    };
  }, []);
  const target = portfolios?.find((item) => String(item.id) === from) ?? portfolios?.[0];
  return (
    <button
      type="button"
      className="btn quiet"
      onClick={() => navigate(target ? paths.portfolio(target.id) : paths.portfolios)}
    >
      <ArrowLeft size={16} />
      {target?.name ?? 'My Portfolios'}
    </button>
  );
}
