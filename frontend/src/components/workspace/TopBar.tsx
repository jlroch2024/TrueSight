// The workspace's top bar: islands floating over the graph. Left: the brand and the portfolio menu (its name, and under
// it a ring that fills as holdings are analysed), as wide as the drawer under it. After it, in the graph's own row: the
// Open Alerts bar, in the most severe open alert's colour, then the graph's title and search. Right: Upload CSV, Check
// News, Analyse, Analyst Desk (analysts only) and the account; the Inspector takes this island's width.
import { AnimatePresence, motion } from 'motion/react';
import { BriefcaseBusiness, Check, ChevronDown, FolderCog, LoaderCircle, Radar, ScanSearch, ShieldCheck, Upload } from 'lucide-react';
import { type Ref, useCallback, useRef, useState } from 'react';
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
}: {
  portfolioId: string;
  // undefined while loading.
  portfolios: Portfolio[] | undefined;
  tab: WorkspaceTab;
  analysed: number;
  total: number;
  analysing: boolean;
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
      <Brand />
      <span className="island-divider" />
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
        <Popover anchor={ref} open={open} align="left" label="Your Portfolios" reachBar>
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
          // The 12px gap after it closes with it (a negative margin cancels it), so nothing snaps when it goes.
          initial={{ width: 0, marginRight: -12, opacity: 0 }}
          animate={{ width: 'auto', marginRight: 0, opacity: 1 }}
          exit={{ width: 0, marginRight: -12, opacity: 0 }}
          transition={PANEL_MOVE}
        >
          <button
            type="button"
            className="alert-bar glass ticks"
            data-severity={severity ?? 'HIGH'}
            title={checkedAt ? `News Checked ${formatAge(checkedAt)}` : undefined}
            onClick={onOpen}
          >
            <span className="pulse-dot" />
            {plural(count, 'Open Alert', 'Open Alerts')}
          </button>
        </motion.span>
      )}
    </AnimatePresence>
  );
}

export function ActionsIsland({
  portfolioId,
  analysing,
  starting,
  checking,
  uploading,
  hasHoldings,
  onUpload,
  onCheck,
  onAnalyse,
  islandRef,
}: {
  portfolioId: string;
  analysing: boolean;
  starting: boolean;
  checking: boolean;
  uploading: boolean;
  hasHoldings: boolean;
  onUpload: () => void;
  onCheck: () => void;
  onAnalyse: () => void;
  // The island itself, so the page can make the Inspector as wide.
  islandRef?: Ref<HTMLDivElement>;
}) {
  const { me } = useAccount();
  const navigate = useNavigate();
  const busy = analysing || starting;
  return (
    <div ref={islandRef} className="island right glass ticks">
      <button type="button" className="btn quiet" disabled={uploading} onClick={onUpload}>
        {uploading ? <LoaderCircle size={16} className="spin" /> : <Upload size={16} />}
        Upload CSV
      </button>
      <button type="button" className="btn quiet" disabled={checking || !hasHoldings} onClick={onCheck}>
        {checking ? <LoaderCircle size={16} className="spin" /> : <Radar size={16} />}
        {checking ? 'Checking News' : 'Check News'}
      </button>
      <button type="button" className={`btn primary${busy ? ' busy' : ''}`} disabled={busy || !hasHoldings} onClick={onAnalyse}>
        {busy ? <LoaderCircle size={16} className="spin" /> : <ScanSearch size={16} />}
        {busy ? 'Analysing' : 'Analyse'}
      </button>
      {me?.role === 'ANALYST' && (
        <>
          <span className="island-divider tight" />
          <button
            type="button"
            className="btn quiet lime-ink"
            onClick={() => navigate(paths.review, { state: { fromPortfolio: portfolioId } })}
          >
            <ShieldCheck size={16} />
            Analyst Desk
          </button>
        </>
      )}
      <span className="island-divider tight" />
      <AccountMenu />
    </div>
  );
}
