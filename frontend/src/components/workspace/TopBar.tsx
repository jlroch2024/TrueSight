// The workspace's top bar: islands floating over the graph. Left: the brand and the portfolio menu (its name, and under
// it a ring that fills as holdings are analysed), as wide as the drawer under it. After it, in the graph's own row: the
// Open Alerts bar, in the most severe open alert's colour, then the graph's title and search. Right: Upload CSV, Check
// News, Analyse, Download Briefing (an icon alone, to fit) and the account, as wide as the Inspector under it, whoever is logged in (styles.css); an analyst's
// Analyst Desk is a bar of its own, just before it. On a phone the left
// island is the whole bar: the brand's mark, the portfolio menu, the Open Alerts pill, Analyst Desk and the account. The
// actions move into the sheets.
import { AnimatePresence, motion } from 'motion/react';
import {
  BriefcaseBusiness,
  Check,
  ChevronDown,
  FileDown,
  FolderCog,
  LoaderCircle,
  Radar,
  ScanSearch,
  ShieldCheck,
  Upload,
} from 'lucide-react';
import { type ReactNode, useCallback, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import type { Severity } from '../../api/news';
import type { Portfolio } from '../../api/portfolios';
import { formatAge } from '../../format';
import { type WorkspaceTab, paths } from '../../paths';
import { useAccount } from '../account';
import { AccountMenu } from '../AccountMenu';
import { Count } from '../Count';
import { ManagePortfolios } from '../ManagePortfolios';
import { PANEL_MOVE, plural } from '../motion';
import { Brand } from '../PageFrame';
import { Popover, useDismiss } from '../Popover';
import { Ring } from './status';

export function PortfolioIsland({
  portfolioId,
  portfolios,
  tab,
  analysed,
  total,
  analysing,
  phone,
}: {
  portfolioId: string;
  // undefined while loading.
  portfolios: Portfolio[] | undefined;
  tab: WorkspaceTab;
  analysed: number;
  total: number;
  analysing: boolean;
  // On a phone: what the end of the bar shows.
  phone?: { openAlerts: number; severity: Severity | null; onAlerts: () => void };
}) {
  const [open, setOpen] = useState(false);
  const [managing, setManaging] = useState(false);
  const close = useCallback(() => setOpen(false), []);
  const ref = useDismiss<HTMLDivElement>(open, close);
  const picker = useRef<HTMLButtonElement>(null);
  const navigate = useNavigate();
  const portfolio = portfolios?.find((item) => String(item.id) === portfolioId);

  return (
    <header className="island left glass ticks">
      <Brand name={!phone} />
      {!phone && <span className="island-divider" />}
      <div ref={ref} className="picker-holder">
        <button
          ref={picker}
          type="button"
          className="picker"
          aria-label={portfolio ? `Portfolio: ${portfolio.name}` : 'Portfolio'}
          aria-haspopup="menu"
          aria-expanded={open}
          onClick={() => setOpen((value) => !value)}
        >
          {total > 0 && <Ring done={analysed} total={total} working={analysing} />}
          <span className="picker-text">
            <span className="picker-name">{portfolio?.name ?? (portfolios ? 'Portfolio' : 'Loading')}</span>
            <span className={`picker-sub${analysing ? ' working' : ''}`}>
              {total === 0 ? (
                'No Holdings Yet'
              ) : analysing ? (
                <>
                  Analysing · <Count value={analysed} /> of {total} Done
                </>
              ) : (
                <>
                  <Count value={analysed} /> of {total} Analysed
                </>
              )}
            </span>
          </span>
          <ChevronDown size={16} className="muted chevron" />
        </button>
        <Popover anchor={ref} open={open} align="left" label="Your Portfolios" reachBar={!phone} fitAnchor={!!phone}>
          <div className="pop-head">Your Portfolios</div>
          {(portfolios ?? []).map((item, index) => {
            const current = String(item.id) === portfolioId;
            return (
              <motion.button
                key={item.id}
                type="button"
                role="menuitem"
                className="pop-item"
                aria-current={current}
                initial={{ opacity: 0, x: -6 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.04 + index * 0.04 }}
                onClick={() => {
                  close();
                  if (!current) navigate(paths.portfolio(item.id, tab));
                }}
              >
                <span className="pop-icon"><BriefcaseBusiness size={16} /></span>
                <span className="grow">
                  {item.name}
                  {current && <span className="pop-sub">{plural(total, 'Holding', 'Holdings')}</span>}
                </span>
                {current && <Check size={16} className="teal-ink" />}
              </motion.button>
            );
          })}
          <div className="pop-rule" />
          <button
            type="button"
            role="menuitem"
            className="pop-item"
            onClick={() => {
              close();
              setManaging(true);
            }}
          >
            <span className="pop-icon"><FolderCog size={16} /></span>
            <span className="grow">Manage Portfolios</span>
          </button>
        </Popover>
      </div>

      {phone && (
        <span className="island-end">
          {phone.openAlerts > 0 && (
            <button
              type="button"
              className="alert-pill compact"
              data-severity={phone.severity ?? 'HIGH'}
              aria-label={plural(phone.openAlerts, 'Open Alert', 'Open Alerts')}
              onClick={phone.onAlerts}
            >
              <span className="pulse-dot" />
              {phone.openAlerts}
            </button>
          )}
          <AnalystDeskButton portfolioId={portfolioId} />
          <AccountMenu />
        </span>
      )}

      {/* Kept while it leaves, so it closes as smoothly as it opens. */}
      <AnimatePresence>
        {managing && (
          <ManagePortfolios
            openId={portfolioId}
            onClose={() => {
              setManaging(false);
              picker.current?.focus();
            }}
          />
        )}
      </AnimatePresence>
    </header>
  );
}

/**
 * The Open Alerts bar: how many open alerts the portfolio has, in the most severe one's colour; it opens the drawer's
 * Alerts tab. It opens and closes its own width, so the bars after it glide rather than jump.
 */
export function OpenAlertsBar({
  count,
  severity,
  checkedAt,
  onOpen,
}: {
  count: number;
  severity: Severity | null;
  checkedAt: string | null;
  onOpen: () => void;
}) {
  return (
    <AnimatePresence initial={false}>
      {count > 0 && (
        <motion.span
          key="alerts"
          className="alert-bar-holder"
          // The 12px gap after it closes with it (a negative margin cancels it), so nothing snaps when it goes. Short of
          // room, it gives way down to the least that still reads.
          initial={{ width: 0, minWidth: 0, marginRight: -12, opacity: 0 }}
          animate={{ width: 'auto', minWidth: 92, marginRight: 0, opacity: 1 }}
          exit={{ width: 0, minWidth: 0, marginRight: -12, opacity: 0 }}
          transition={PANEL_MOVE}
        >
          <button
            type="button"
            className="alert-bar glass ticks"
            data-severity={severity ?? 'HIGH'}
            title={[plural(count, 'Open Alert', 'Open Alerts'), checkedAt && `News Checked ${formatAge(checkedAt)}`].filter(Boolean).join(' · ')}
            onClick={onOpen}
          >
            <span className="pulse-dot" />
            <span className="alert-bar-text">{plural(count, 'Open Alert', 'Open Alerts')}</span>
          </button>
        </motion.span>
      )}
    </AnimatePresence>
  );
}

export function ActionsIsland({
  analysing,
  starting,
  checking,
  uploading,
  downloading,
  hasHoldings,
  onUpload,
  onCheck,
  onAnalyse,
  onBriefing,
}: {
  analysing: boolean;
  starting: boolean;
  checking: boolean;
  uploading: boolean;
  // The briefing is being made.
  downloading: boolean;
  hasHoldings: boolean;
  onUpload: () => void;
  onCheck: () => void;
  onAnalyse: () => void;
  onBriefing: () => void;
}) {
  const busy = analysing || starting;
  return (
    <div className="island right glass ticks">
      <button type="button" className="btn quiet" disabled={uploading} aria-label="Upload CSV" title="Upload CSV" onClick={onUpload}>
        <Swap
          on={uploading}
          busy={<><LoaderCircle size={16} className="spin" /><span className="btn-word">Upload CSV</span></>}
          idle={<><Upload size={16} /><span className="btn-word">Upload CSV</span></>}
        />
      </button>
      <button
        type="button"
        className="btn quiet"
        disabled={checking || !hasHoldings}
        aria-label={checking ? 'Checking News' : 'Check News'}
        title={checking ? 'Checking News' : 'Check News'}
        onClick={onCheck}
      >
        <Swap
          on={checking}
          busy={<><LoaderCircle size={16} className="spin" /><span className="btn-word">Checking News</span></>}
          idle={<><Radar size={16} /><span className="btn-word">Check News</span></>}
        />
      </button>
      <button type="button" className={`btn primary${busy ? ' busy' : ''}`} disabled={busy || !hasHoldings} onClick={onAnalyse}>
        <Swap
          on={busy}
          busy={<><LoaderCircle size={16} className="spin" />Analysing</>}
          idle={<><ScanSearch size={16} />Analyse</>}
        />
      </button>
      <button
        type="button"
        className="icon-btn"
        disabled={downloading || !hasHoldings}
        aria-label={downloading ? 'Making the Briefing' : 'Download Briefing'}
        title={downloading ? 'Making the Briefing' : 'Download Briefing'}
        onClick={onBriefing}
      >
        {downloading ? <LoaderCircle size={17} className="spin" /> : <FileDown size={17} />}
      </button>
      <span className="island-divider tight" />
      <AccountMenu />
    </div>
  );
}

/**
 * Analyst Desk, for analysts only. On a computer it is a bar of its own, just before the right island, so that island and
 * the Inspector under it are the same width for everyone; in the phone's one bar, an icon.
 */
export function AnalystDeskButton({ portfolioId, bar = false }: { portfolioId: string; bar?: boolean }) {
  const { me } = useAccount();
  const navigate = useNavigate();
  if (me?.role !== 'ANALYST') return null;
  const open = () => navigate(paths.review, { state: { fromPortfolio: portfolioId } });
  if (bar) {
    return (
      <div className="island desk-bar glass ticks">
        <button type="button" className="btn quiet lime-ink" aria-label="Analyst Desk" title="Analyst Desk" onClick={open}>
          <ShieldCheck size={16} />
          <span className="btn-word">Analyst Desk</span>
        </button>
      </div>
    );
  }
  return (
    <button type="button" className="icon-btn lime-ink" aria-label="Analyst Desk" title="Analyst Desk" onClick={open}>
      <ShieldCheck size={18} />
    </button>
  );
}

/**
 * A button's icon and words while it works and while it waits (Analysing, Analyse), laid in one place: the button keeps
 * the longer one's width, so the island beside it never jumps, and one fades into the other.
 */
export function Swap({ on, busy, idle }: { on: boolean; busy: ReactNode; idle: ReactNode }) {
  return (
    <span className="btn-swap" data-on={on}>
      <span className="btn-swap-face busy" aria-hidden={!on}>{busy}</span>
      <span className="btn-swap-face idle" aria-hidden={on}>{idle}</span>
    </span>
  );
}
