// The workspace: a whole portfolio on one screen, at /portfolios/:portfolioId. The living graph is the ground, edge to
// edge; glass panels float over it:
//
// - the top bar's islands (TopBar.tsx): the portfolio menu with its analysis ring and the Open Alerts pill on the left,
//   the middle kept for the Sprint 4 timeline, and Upload CSV, Check News, Analyse, Analyst Desk and the account on the
//   right;
// - the left drawer, with the Holdings and Alerts tabs (HoldingsTab.tsx, AlertsTab.tsx), which folds into a slim rail
//   of the holdings' tiles. The open tab is in the address (?tab=alerts), as the open portfolio is;
// - the Inspector on the right (Inspector.tsx), open only while something is chosen: a company, a relationship or an
//   alert. Closed, it leaves a tab at the right edge that opens it again on what it last showed.
//
// Choosing an alert spotlights it on the graph: the rest dims, and the event's company, the holdings it reaches and the
// links between them stay lit, while the camera frames them. The bottom middle is kept for the Sprint 4 chat.
import { AnimatePresence, animate, motion, useMotionValue, useTransform } from 'motion/react';
import { Bell, Check, ChevronsLeft, ChevronsRight, LoaderCircle, Network, Plus, ScanSearch, TriangleAlert, Upload, X } from 'lucide-react';
import { type ChangeEvent, type DragEvent, type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { type AlertEvent, type AlertStatus, type AlertsResponse, alertApi } from '../api/alerts';
import { ApiError, api } from '../api/client';
import { type Holding, type Portfolio, holdingApi, portfolioApi, portfolioChanges } from '../api/portfolios';
import { type Relationship, relationshipApi } from '../api/relationships';
import { AccountMenu } from '../components/AccountMenu';
import { useConfirm } from '../components/ConfirmDialog';
import { PANEL_MOVE, PANEL_SPRING, plural } from '../components/motion';
import { Segmented } from '../components/Segmented';
import { LogoSettingsContext, Tile } from '../components/Tile';
import { useLogoSettings } from '../components/useLogoSettings';
import { type AlertFilter, AlertsTab } from '../components/workspace/AlertsTab';
import { HoldingsTab, LoadingRows } from '../components/workspace/HoldingsTab';
import { InspectorBody, InspectorKind } from '../components/workspace/Inspector';
import { ActionsIsland, OpenAlertsBar, PortfolioIsland } from '../components/workspace/TopBar';
import { type Chosen, alertKey, byUrgency, spotlightOf, useWorkspaceView } from '../components/workspace/view';
import { LivingGraph } from '../graph/LivingGraph';
import { type WorkspaceTab, paths } from '../paths';

// The layout's measures, in pixels: the gap around every panel, the top bar's height, the drawer open and folded, and
// the Inspector until the right island has been measured (it takes that island's width). The left island is as wide as
// the drawer (styles.css). The graph is told how much of each side the panels cover (its insets).
const GAP = 12;
const BAR = 52;
const DRAWER = 380;
const RAIL = 60;
const INSPECTOR = 420;
// How often a running analysis and a news check are looked at again.
const ANALYSIS_POLL_MS = 1500;
const NEWS_POLL_MS = 2000;
const WEIGHT_MESSAGE = 'The weight must be a number between 0 and 100.';

type Toast = { id: number; text: string; error?: boolean; undo?: () => void };

function messageOf(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Something went wrong.';
}

/**
 * What a weight box holds: blank means no weight (null), "12.5" and "12.5%" are numbers, and anything else is
 * undefined. The range, 0 to 100, is checked by the backend, which says so if it is broken.
 */
function parseWeight(text: string): number | null | undefined {
  const cleaned = text.trim().replace(/%$/, '').trim();
  if (cleaned === '') return null;
  const value = Number(cleaned);
  return Number.isFinite(value) ? value : undefined;
}

const byTicker = (a: Holding, b: Holding) => a.ticker.localeCompare(b.ticker);

export function WorkspacePage() {
  const { portfolioId = '' } = useParams();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab: WorkspaceTab = searchParams.get('tab') === 'alerts' ? 'alerts' : 'holdings';
  const setTab = useCallback(
    (next: WorkspaceTab) => setSearchParams(next === 'alerts' ? { tab: 'alerts' } : {}, { replace: true }),
    [setSearchParams],
  );

  // undefined while loading.
  const [portfolios, setPortfolios] = useState<Portfolio[] | undefined>(undefined);
  const [holdings, setHoldings] = useState<Holding[] | undefined>(undefined);
  const [relationships, setRelationships] = useState<Relationship[] | undefined>(undefined);
  const [alertsResponse, setAlertsResponse] = useState<AlertsResponse | undefined>(undefined);
  const [alertsError, setAlertsError] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  const [leftOpen, setLeftOpen] = useState(true);
  const [chosen, setChosen] = useState<Chosen>(null);
  // The last thing the Inspector showed, so a closed Inspector can be opened again where it was.
  const [lastChosen, setLastChosen] = useState<Chosen>(null);
  const [peek, setPeek] = useState<string | null>(null);
  const [filter, setFilter] = useState<AlertFilter>('open');
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [starting, setStarting] = useState(false);
  const [startingCheck, setStartingCheck] = useState(false);
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const uploadInput = useRef<HTMLInputElement>(null);
  // The right island's width, which the Inspector takes, so the two line up.
  const [inspectorWidth, setInspectorWidth] = useState(INSPECTOR);
  const actionsIsland = useRef<HTMLDivElement | null>(null);
  const inspectorBody = useRef<HTMLDivElement | null>(null);
  const { confirm, dialog } = useConfirm();

  const portfolio = portfolios === undefined ? undefined : (portfolios.find((item) => String(item.id) === portfolioId) ?? null);

  const toast = useCallback((text: string, options: { error?: boolean; undo?: () => void } = {}) => {
    const id = Date.now() + Math.random();
    setToasts((all) => [...all.slice(-2), { id, text, ...options }]);
    window.setTimeout(() => setToasts((all) => all.filter((item) => item.id !== id)), options.undo || options.error ? 6000 : 3200);
  }, []);

  // ---------- Loading ----------

  // The portfolio now in the address. An answer that arrives after a switch belongs to the portfolio left behind, so the
  // loaders below drop it rather than show it under the new one.
  const shownPortfolio = useRef(portfolioId);
  shownPortfolio.current = portfolioId;

  useEffect(() => {
    let active = true;
    setPortfolios(undefined);
    setHoldings(undefined);
    setRelationships(undefined);
    setAlertsResponse(undefined);
    setAlertsError(null);
    setLoadError(null);
    setChosen(null);
    setLastChosen(null);
    portfolioApi
      .list()
      .then(async (all) => {
        const found = all.find((item) => String(item.id) === portfolioId);
        if (found) {
          const [foundHoldings, foundRelationships] = await Promise.all([
            holdingApi.list(portfolioId),
            relationshipApi.list(portfolioId),
          ]);
          if (!active) return;
          setHoldings(foundHoldings);
          setRelationships(foundRelationships);
        }
        if (active) setPortfolios(all);
      })
      .catch((e: unknown) => {
        if (active) setLoadError(messageOf(e));
      });
    return () => {
      active = false;
    };
  }, [portfolioId, attempt]);

  // The alerts, closed ones included (the Closed and All filters need them). Not essential to the rest: if they cannot
  // be loaded, the Alerts tab says so and offers Retry.
  const loadAlerts = useCallback(async () => {
    try {
      const answer = await alertApi.list(portfolioId, true);
      if (shownPortfolio.current !== portfolioId) return;
      setAlertsResponse(answer);
      setAlertsError(null);
    } catch (e) {
      if (shownPortfolio.current === portfolioId) setAlertsError(messageOf(e));
    }
  }, [portfolioId]);
  useEffect(() => {
    void loadAlerts();
  }, [loadAlerts, attempt]);

  // After a change in the Manage Portfolios window, show the new name at once (the window passes the renamed portfolio),
  // then the list as it now is; if that cannot be loaded, the new name still shows.
  useEffect(
    () =>
      portfolioChanges.listen((changed) => {
        if (changed) setPortfolios((all) => all?.map((item) => (item.id === changed.id ? { ...item, ...changed } : item)));
        portfolioApi
          .list()
          .then(setPortfolios)
          .catch(() => {});
      }),
    [],
  );

  const loadHoldings = useCallback(async () => {
    try {
      const answer = await holdingApi.list(portfolioId);
      if (shownPortfolio.current === portfolioId) setHoldings(answer);
    } catch (e) {
      if (shownPortfolio.current === portfolioId) toast(messageOf(e), { error: true });
    }
  }, [portfolioId, toast]);

  const loadRelationships = useCallback(async () => {
    try {
      const answer = await relationshipApi.list(portfolioId);
      if (shownPortfolio.current === portfolioId) setRelationships(answer);
    } catch (e) {
      if (shownPortfolio.current === portfolioId) toast(messageOf(e), { error: true });
    }
  }, [portfolioId, toast]);

  // ---------- What the graph, the drawer and the Inspector show ----------

  const view = useWorkspaceView(holdings ?? [], relationships ?? [], alertsResponse?.alerts ?? [], alertsResponse?.weighted ?? true);
  const logoSettings = useLogoSettings(useMemo(() => view.nodes.filter((node) => !node.ticker).map((node) => node.name), [view.nodes]));
  const loaded = holdings !== undefined && relationships !== undefined && !!portfolio;
  const worst = useMemo(() => [...view.open].sort(byUrgency)[0] ?? null, [view.open]);
  const analysed = view.holdings.filter((holding) => holding.status === 'DONE').length;
  const analysing = view.holdings.some((holding) => holding.status === 'ANALYSING');
  const checking = portfolio?.newsStatus === 'CHECKING';

  useEffect(() => {
    if (chosen) setLastChosen(chosen);
  }, [chosen]);
  // Something new chosen is read from its top.
  useEffect(() => {
    inspectorBody.current?.scrollTo?.({ top: 0 });
  }, [chosen]);
  // Changing tab, what is chosen or the drawer lets go of any preview from a list.
  useEffect(() => setPeek(null), [tab, chosen, leftOpen]);

  // Escape closes the Inspector, unless a menu or a window is open (Escape is theirs then).
  useEffect(() => {
    if (!chosen) return;
    const key = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || document.querySelector('.pop, .window-backdrop, .scrim')) return;
      setChosen(null);
    };
    window.addEventListener('keydown', key);
    return () => window.removeEventListener('keydown', key);
  }, [chosen]);

  // A chosen alert lights the company it is about, the holdings it reaches, and every link on the way.
  const focus = useMemo(() => {
    if (chosen?.kind !== 'alert') return null;
    const alert = view.alerts.find((item) => alertKey(item) === chosen.key);
    return alert ? spotlightOf(alert, view) : null;
  }, [chosen, view]);

  // ---------- Analysis and news, followed until they finish ----------

  // While a holding is analysed, look again every moment; when the last one finishes, its suppliers and customers join
  // the graph.
  const wasAnalysing = useRef(false);
  useEffect(() => {
    if (analysing) {
      wasAnalysing.current = true;
      const timer = window.setInterval(() => void loadHoldings(), ANALYSIS_POLL_MS);
      return () => window.clearInterval(timer);
    }
    if (wasAnalysing.current) {
      wasAnalysing.current = false;
      void loadRelationships();
      toast(`${plural(analysed, 'Holding', 'Holdings')} Analysed`);
    }
  }, [analysing, loadHoldings, loadRelationships, toast, analysed]);

  // While news is checked, look at the portfolio again every moment; when it finishes, load its alerts.
  const wasChecking = useRef(false);
  useEffect(() => {
    if (checking) {
      wasChecking.current = true;
      const timer = window.setInterval(() => {
        portfolioApi
          .list()
          .then(setPortfolios)
          .catch((e: unknown) => toast(messageOf(e), { error: true }));
      }, NEWS_POLL_MS);
      return () => window.clearInterval(timer);
    }
    if (wasChecking.current && portfolio) {
      wasChecking.current = false;
      if (portfolio.newsStatus === 'FAILED') {
        toast(portfolio.newsStatusReason ?? 'News could not be checked.', { error: true });
        return;
      }
      void alertApi
        .list(portfolioId, true)
        .then((found) => {
          setAlertsResponse(found);
          setAlertsError(null);
          const open = found.alerts.filter((alert) => alert.status !== 'DISMISSED').length;
          toast(`News Checked · ${plural(open, 'Open Alert', 'Open Alerts')}`);
        })
        .catch((e: unknown) => setAlertsError(messageOf(e)));
    }
  }, [checking, portfolio, portfolioId, toast]);

  // ---------- What the buttons do ----------

  async function startAnalysis() {
    setStarting(true);
    try {
      await api<void>(`/portfolios/${portfolioId}/analysis`, { method: 'POST' });
      await loadHoldings();
    } catch (e) {
      toast(messageOf(e), { error: true });
    } finally {
      setStarting(false);
    }
  }

  async function checkNews() {
    if (!portfolio) return;
    setStartingCheck(true);
    try {
      await portfolioApi.newsCheck(portfolio.id);
      setPortfolios((all) =>
        all?.map((item) => (item.id === portfolio.id ? { ...item, newsStatus: 'CHECKING', newsStatusReason: null } : item)),
      );
    } catch (e) {
      toast(messageOf(e), { error: true });
    } finally {
      setStartingCheck(false);
    }
  }

  function openUpload() {
    setUploadError(null);
    setUploadOpen(true);
  }

  async function upload(file: File) {
    if (!portfolio) return;
    if (view.holdings.length > 0) {
      const confirmed = await confirm({
        title: 'Replace Holdings',
        message: `This replaces the ${plural(view.holdings.length, 'holding', 'holdings')} in ${portfolio.name}.`,
        confirmLabel: 'Replace Holdings',
        danger: true,
      });
      if (!confirmed) return;
    }
    setUploading(true);
    setUploadError(null);
    try {
      const uploaded = await holdingApi.upload(portfolioId, file);
      setHoldings(uploaded);
      setUploadOpen(false);
      setChosen(null);
      setLastChosen(null);
      toast(`Uploaded ${plural(uploaded.length, 'Holding', 'Holdings')}`);
      void loadRelationships();
      void loadAlerts();
    } catch (e) {
      setUploadError(messageOf(e));
    } finally {
      setUploading(false);
    }
  }

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // Cleared straight away, so choosing the same file again still counts as a new choice.
    event.target.value = '';
    if (file) void upload(file);
  }

  function dropFile(event: DragEvent<HTMLElement>) {
    event.preventDefault();
    const file = event.dataTransfer.files?.[0];
    if (file && !uploading) void upload(file);
  }

  async function addHolding(ticker: string, weightText: string): Promise<string | null> {
    const weight = parseWeight(weightText);
    if (weight === undefined) return WEIGHT_MESSAGE;
    try {
      const added = await holdingApi.add(portfolioId, ticker.trim(), weight);
      // It shows as Waiting at once. The backend starts its analysis straight away, so look again: the list then shows
      // it Analysing, and keeps looking until it has a result.
      setHoldings((current) => [...(current ?? []), added].sort(byTicker));
      await loadHoldings();
      return null;
    } catch (e) {
      return messageOf(e);
    }
  }

  async function saveWeight(holding: Holding, weightText: string): Promise<string | null> {
    const weight = parseWeight(weightText);
    if (weight === undefined) return WEIGHT_MESSAGE;
    try {
      const updated = await holdingApi.updateWeight(portfolioId, holding.id, weight);
      setHoldings((current) => current?.map((row) => (row.id === updated.id ? updated : row)));
      toast(`${holding.ticker} Weight Saved`, {
        undo: () => {
          void holdingApi
            .updateWeight(portfolioId, holding.id, holding.weight)
            .then((restored) => setHoldings((current) => current?.map((row) => (row.id === restored.id ? restored : row))))
            .catch((e: unknown) => toast(messageOf(e), { error: true }));
        },
      });
      return null;
    } catch (e) {
      return messageOf(e);
    }
  }

  async function removeHolding(holding: Holding) {
    const confirmed = await confirm({
      title: 'Remove Holding',
      message: `This removes ${holding.ticker} from ${portfolio?.name ?? 'this portfolio'}, and from its alerts.`,
      confirmLabel: 'Remove Holding',
      danger: true,
    });
    if (!confirmed) return;
    try {
      await holdingApi.remove(portfolioId, holding.id);
      setHoldings((current) => current?.filter((row) => row.id !== holding.id));
      setChosen(null);
      setLastChosen(null);
      toast(`${holding.ticker} Removed`);
      void loadRelationships();
      void loadAlerts();
    } catch (e) {
      toast(messageOf(e), { error: true });
    }
  }

  async function setAlertStatus(alert: AlertEvent, status: AlertStatus, withUndo = true) {
    const key = alertKey(alert);
    setBusyKey(key);
    try {
      const updated = await alertApi.setEventStatus(portfolioId, alert.alertIds, status);
      setAlertsResponse((current) =>
        current && {
          ...current,
          alerts: current.alerts.map((item) => (item.alertIds.some((id) => updated.alertIds.includes(id)) ? updated : item)),
        },
      );
      if (status === 'DISMISSED' && chosen?.kind === 'alert' && chosen.key === key && filter === 'open') setChosen(null);
      if (withUndo) {
        toast(status === 'DISMISSED' ? 'Alert Closed' : 'Alert Restored', {
          undo: () => void setAlertStatus(updated, status === 'DISMISSED' ? 'NEW' : 'DISMISSED', false),
        });
      }
    } catch (e) {
      toast(messageOf(e), { error: true });
    } finally {
      setBusyKey(null);
    }
  }

  function openAlertsTab() {
    setTab('alerts');
    setLeftOpen(true);
  }

  // ---------- The drawer, folding into its rail as one piece ----------

  // The drawer's width, folding and unfolding: what it shows is read from it, so the holdings fade out, and the rail of
  // their tiles fades in, exactly as the drawer narrows, never ahead of it.
  const drawerWidth = useMotionValue(DRAWER);
  useEffect(() => {
    const glide = animate(drawerWidth, leftOpen ? DRAWER : RAIL, PANEL_MOVE);
    return () => glide.stop();
  }, [leftOpen, drawerWidth]);
  const innerOpacity = useTransform(drawerWidth, [RAIL + 110, DRAWER - 30], [0, 1]);
  const innerX = useTransform(drawerWidth, [RAIL, DRAWER], [-44, 0]);
  const railOpacity = useTransform(drawerWidth, [RAIL + 20, RAIL + 140], [1, 0]);
  const railX = useTransform(drawerWidth, [RAIL, RAIL + 140], [0, 14]);

  useEffect(() => {
    const island = actionsIsland.current;
    if (!island || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(() => setInspectorWidth(Math.round(island.offsetWidth) || INSPECTOR));
    observer.observe(island);
    return () => observer.disconnect();
  }, [loaded]);

  const inspectorOpen = chosen !== null && loaded;
  // Each side keeps the page's 12px gap from what covers it (or from the screen's edge), so the graph's own controls sit
  // on these lines and every gap on the screen is the same.
  const insets = {
    top: GAP * 2 + BAR,
    right: inspectorOpen ? GAP * 2 + inspectorWidth : GAP,
    bottom: GAP,
    left: loaded ? GAP * 2 + (leftOpen ? DRAWER : RAIL) : GAP,
  };
  const chooseCompany = (nodeId: string) => setChosen({ kind: 'company', nodeId });
  const openCount = view.open.length;
  const tabDirection = tab === 'alerts' ? 1 : -1;

  const drawerContent = !loaded || !portfolio ? (
    <LoadingRows />
  ) : tab === 'holdings' ? (
    <HoldingsTab
      view={view}
      chosenNode={chosen?.kind === 'company' ? chosen.nodeId : null}
      worst={worst}
      onWorst={() => {
        if (!worst) return;
        openAlertsTab();
        setChosen({ kind: 'alert', key: alertKey(worst) });
      }}
      onPeek={setPeek}
      onChoose={chooseCompany}
      onAdd={addHolding}
      onUpload={openUpload}
      uploading={uploading}
    />
  ) : alertsError && !alertsResponse ? (
    <div className="empty">
      <TriangleAlert size={26} />
      <span role="alert" className="error">{alertsError}</span>
      <button type="button" className="btn small" onClick={() => void loadAlerts()}>Retry</button>
    </div>
  ) : !alertsResponse ? (
    <LoadingRows />
  ) : (
    <AlertsTab
      view={view}
      portfolio={portfolio}
      filter={filter}
      chosenKey={chosen?.kind === 'alert' ? chosen.key : null}
      busyKey={busyKey}
      checking={checking}
      onFilter={setFilter}
      onChoose={(alert) => setChosen({ kind: 'alert', key: alertKey(alert) })}
      onClose={(alert) => void setAlertStatus(alert, 'DISMISSED')}
      onRestore={(alert) => void setAlertStatus(alert, 'NEW')}
      onCheck={() => void checkNews()}
    />
  );

  return (
    <LogoSettingsContext.Provider value={logoSettings}>
      <div className="ws">
        <div className="ws-graph">
          {loaded && view.nodes.length > 0 && (
            <LivingGraph
              nodes={view.nodes}
              edges={view.edges}
              bare
              insets={insets}
              // The Open Alerts bar, the title and search follow the left island (as wide as the drawer), and stop short
              // of the right one (as wide as the Inspector).
              topBar={{ left: GAP * 2 + DRAWER, right: GAP * 2 + inspectorWidth }}
              barStart={
                <OpenAlertsBar
                  count={openCount}
                  severity={worst?.headline.assessment.severity ?? null}
                  checkedAt={portfolio?.newsCheckedAt ?? null}
                  onOpen={openAlertsTab}
                />
              }
              focus={focus}
              peekNodeId={peek}
              onNodeSelect={(nodeId) => setChosen(nodeId ? { kind: 'company', nodeId } : null)}
              onEdgeSelect={(relationshipId) => setChosen({ kind: 'relationship', relationshipId })}
              selectedNodeId={chosen?.kind === 'company' ? chosen.nodeId : null}
              selectedRelationshipId={chosen?.kind === 'relationship' ? chosen.relationshipId : null}
              logoToken={logoSettings.token}
              logoDomains={logoSettings.domains}
              layoutKey={`truesight.graph.${portfolioId}`}
              risks={view.risks}
              alertDots={view.alertDots}
            />
          )}
          {loadError ? (
            <GroundCard icon={<TriangleAlert size={28} />} title="Portfolio Could Not Load" insets={insets}>
              <p role="alert" className="error">{loadError}</p>
              <button type="button" className="btn primary" onClick={() => setAttempt((count) => count + 1)}>Retry</button>
            </GroundCard>
          ) : portfolio === null ? (
            <GroundCard icon={<Network size={28} />} title="Portfolio Not Found" insets={insets}>
              <button type="button" className="btn primary" onClick={() => navigate(paths.portfolios)}>Open My Portfolios</button>
            </GroundCard>
          ) : loaded && view.holdings.length === 0 ? (
            <GroundCard icon={<Network size={28} />} title="No Supply Chain Yet" insets={insets}>
              <button type="button" className="btn primary" disabled={uploading} onClick={openUpload}>
                <Upload size={16} /> Upload CSV
              </button>
              <button
                type="button"
                className="btn"
                onClick={() => {
                  setTab('holdings');
                  setLeftOpen(true);
                  window.setTimeout(() => document.getElementById('new-ticker')?.focus(), 0);
                }}
              >
                <Plus size={16} /> Add Holding
              </button>
            </GroundCard>
          ) : (
            loaded &&
            view.nodes.length === 0 && (
              <GroundCard icon={<Network size={28} />} title="No Supply Chain Yet" insets={insets}>
                <button type="button" className={`btn primary${analysing || starting ? ' busy' : ''}`} disabled={analysing || starting} onClick={() => void startAnalysis()}>
                  {analysing || starting ? <LoaderCircle size={16} className="spin" /> : <ScanSearch size={16} />}
                  {analysing || starting ? 'Analysing' : 'Analyse'}
                </button>
              </GroundCard>
            )
          )}
        </div>

        {/* ---------- Top islands ---------- */}
        <PortfolioIsland
          portfolioId={portfolioId}
          portfolios={portfolios}
          tab={tab}
          analysed={analysed}
          total={view.holdings.length}
          analysing={analysing}
        />
        {loaded ? (
          <ActionsIsland
            portfolioId={portfolioId}
            analysing={analysing}
            starting={starting}
            checking={checking || startingCheck}
            uploading={uploading}
            hasHoldings={view.holdings.length > 0}
            onUpload={openUpload}
            onCheck={() => void checkNews()}
            onAnalyse={() => void startAnalysis()}
            islandRef={actionsIsland}
          />
        ) : (
          <div className="island right glass ticks">
            <AccountMenu />
          </div>
        )}
        <input
          ref={uploadInput}
          id="upload-csv"
          aria-label="CSV File"
          type="file"
          accept=".csv,text/csv"
          className="visually-hidden"
          disabled={uploading}
          onChange={chooseFile}
        />

        {/* ---------- The drawer, and the Inspector ---------- */}
        {!loadError && portfolio !== null && (
          <motion.aside className={`drawer glass ticks${leftOpen ? '' : ' folded'}`} aria-label="Portfolio" style={{ width: drawerWidth }}>
            <motion.div
              className="drawer-rail"
              aria-hidden={leftOpen}
              inert={leftOpen}
              style={{ opacity: railOpacity, x: railX, pointerEvents: leftOpen ? 'none' : 'auto' }}
            >
              <button type="button" className="rail-btn" aria-label="Open the Drawer" title="Open the Drawer" onClick={() => setLeftOpen(true)}>
                <ChevronsRight size={18} />
              </button>
              <button type="button" className="rail-btn" aria-label={`Alerts (${openCount} Open)`} title="Alerts" onClick={openAlertsTab}>
                <Bell size={18} />
                {openCount > 0 && <span className="badge">{openCount}</span>}
              </button>
              <span className="rail-rule" />
              <div className="rail-holdings">
                {view.holdings.map((holding) => {
                  const nodeId = view.nodeIdOf(holding);
                  const onGraph = view.nodeIdByHolding.has(holding.id);
                  return (
                    <button
                      key={holding.id}
                      type="button"
                      className="rail-holding"
                      title={holding.companyDisplayName ?? holding.ticker}
                      aria-label={holding.companyDisplayName ?? holding.ticker}
                      aria-current={chosen?.kind === 'company' && chosen.nodeId === nodeId}
                      onPointerEnter={() => setPeek(onGraph ? nodeId : null)}
                      onPointerLeave={() => setPeek(null)}
                      onClick={() => chooseCompany(nodeId)}
                    >
                      <Tile
                        name={holding.companyName ?? holding.ticker}
                        ticker={holding.ticker}
                        size={30}
                        holding
                        colour={view.colourOf.get(nodeId)}
                        beacon={view.alertDots.get(nodeId) ?? null}
                      />
                    </button>
                  );
                })}
              </div>
            </motion.div>

            <motion.div
              className="drawer-inner"
              style={{ width: DRAWER - 2, opacity: innerOpacity, x: innerX, pointerEvents: leftOpen ? 'auto' : 'none' }}
              aria-hidden={!leftOpen}
              inert={!leftOpen}
            >
              <div className="drawer-head">
                <Segmented
                  label="Portfolio"
                  value={tab}
                  onChange={setTab}
                  options={[
                    { value: 'holdings', name: 'Holdings', label: <>Holdings <span className="count">{view.holdings.length}</span></> },
                    {
                      value: 'alerts',
                      name: 'Alerts',
                      label: <>Alerts <span className={`count${openCount ? ' alarm' : ''}`}>{openCount}</span></>,
                    },
                  ]}
                />
                <button type="button" className="icon-btn" aria-label="Fold the Drawer" title="Fold the Drawer" onClick={() => setLeftOpen(false)}>
                  <ChevronsLeft size={17} />
                </button>
              </div>
              <div className="drawer-body">
                <AnimatePresence mode="wait" initial={false} custom={tabDirection}>
                  <motion.div
                    key={`${tab}-${loaded}`}
                    custom={tabDirection}
                    variants={{
                      enter: (direction: number) => ({ opacity: 0, x: direction * 14 }),
                      shown: { opacity: 1, x: 0, transition: { duration: 0.26, ease: [0.22, 1, 0.36, 1] } },
                      leave: (direction: number) => ({ opacity: 0, x: direction * -10, transition: { duration: 0.12, ease: [0.4, 0, 1, 1] } }),
                    }}
                    initial="enter"
                    animate="shown"
                    exit="leave"
                  >
                    {drawerContent}
                  </motion.div>
                </AnimatePresence>
              </div>
            </motion.div>
          </motion.aside>
        )}

        <AnimatePresence>
          {inspectorOpen && chosen && (
            <motion.aside
              key="inspector"
              className="inspector glass ticks"
              aria-label="Inspector"
              style={{ width: inspectorWidth }}
              initial={{ x: inspectorWidth + GAP + 24 }}
              animate={{ x: 0 }}
              exit={{ x: inspectorWidth + GAP + 24 }}
              transition={PANEL_MOVE}
            >
              <div className="insp-head">
                <InspectorKind chosen={chosen} />
                <button type="button" className="icon-btn" aria-label="Close the Inspector" title="Close (Esc)" onClick={() => setChosen(null)}>
                  <X size={17} />
                </button>
              </div>
              <div ref={inspectorBody} className="insp-body">
                <InspectorBody
                  chosen={chosen}
                  view={view}
                  busyKey={busyKey}
                  onChoose={setChosen}
                  onWeight={saveWeight}
                  onRemove={(holding) => void removeHolding(holding)}
                  onClose={(alert) => void setAlertStatus(alert, 'DISMISSED')}
                  onRestore={(alert) => void setAlertStatus(alert, 'NEW')}
                />
              </div>
            </motion.aside>
          )}
        </AnimatePresence>

        {/* A closed Inspector leaves a tab at the edge, to open it again where it was. */}
        <AnimatePresence>
          {loaded && !chosen && lastChosen && (
            <motion.button
              key="reopen"
              type="button"
              className="insp-reopen glass ticks"
              aria-label="Open the Inspector Again"
              title="Open the Inspector Again"
              // On the panels' own curve and pace, both ways, so it moves with the Inspector rather than after it.
              initial={{ x: 120 }}
              animate={{ x: 0 }}
              exit={{ x: 120 }}
              transition={PANEL_MOVE}
              onClick={() => setChosen(lastChosen)}
            >
              <ChevronsLeft size={17} />
              <InspectorKind chosen={lastChosen} />
            </motion.button>
          )}
        </AnimatePresence>

        {/* ---------- Toasts and the upload window ---------- */}
        <div className="toasts" style={{ left: `calc(50% + ${(insets.left - insets.right) / 2}px)` }} aria-live="polite">
          <AnimatePresence>
            {toasts.map((item) => (
              <motion.div
                key={item.id}
                layout
                role={item.error ? 'alert' : 'status'}
                className={`toast glass ticks${item.error ? ' error' : ''}`}
                initial={{ opacity: 0, y: 16, scale: 0.96 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 8, scale: 0.97 }}
                transition={PANEL_SPRING}
              >
                <span className="toast-check">{item.error ? <TriangleAlert size={13} strokeWidth={2.4} /> : <Check size={14} strokeWidth={2.6} />}</span>
                {item.text}
                {item.undo ? (
                  <button
                    type="button"
                    className="btn small quiet"
                    onClick={() => {
                      item.undo?.();
                      setToasts((all) => all.filter((other) => other.id !== item.id));
                    }}
                  >
                    Undo
                  </button>
                ) : (
                  <span className="toast-end" />
                )}
              </motion.div>
            ))}
          </AnimatePresence>
        </div>

        <AnimatePresence>
          {uploadOpen && (
            <motion.div
              className="scrim"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onPointerDown={(event) => event.target === event.currentTarget && !uploading && setUploadOpen(false)}
              onKeyDown={(event) => event.key === 'Escape' && !uploading && setUploadOpen(false)}
            >
              <motion.div
                className="dialog glass ticks"
                role="dialog"
                aria-modal="true"
                aria-labelledby="upload-title"
                initial={{ opacity: 0, y: 18, scale: 0.96 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 10, scale: 0.97 }}
                transition={PANEL_SPRING}
              >
                <h2 id="upload-title">Upload a Portfolio CSV</h2>
                <p>A CSV with a ticker or symbol column, and an optional weight column. Up to 5 MB. It replaces this portfolio’s holdings.</p>
                <button
                  type="button"
                  className="drop"
                  autoFocus
                  disabled={uploading}
                  onClick={() => uploadInput.current?.click()}
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={dropFile}
                >
                  {uploading ? <LoaderCircle size={24} className="spin" /> : <Upload size={24} />}
                  <span>
                    {uploading ? <strong>Uploading</strong> : <><strong>Choose a File</strong> or Drop It Here</>}
                  </span>
                </button>
                {uploadError && <p role="alert" className="error">{uploadError}</p>}
                <div className="actions">
                  <button type="button" className="btn quiet" disabled={uploading} onClick={() => setUploadOpen(false)}>
                    Cancel
                  </button>
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>
        {dialog}
      </div>
    </LogoSettingsContext.Provider>
  );
}

/** A card in the middle of the graph's place, for a state with no graph to show: empty, not found, or failed. */
function GroundCard({
  icon,
  title,
  insets,
  children,
}: {
  icon: ReactNode;
  title: string;
  insets: { top: number; right: number; bottom: number; left: number };
  children: ReactNode;
}) {
  return (
    <div className="empty-ground" style={{ inset: `${insets.top}px ${insets.right}px ${insets.bottom}px ${insets.left}px` }}>
      <motion.div className="glass ticks empty-card" initial={{ opacity: 0, y: 12, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} transition={PANEL_SPRING}>
        <span className="empty-mark">{icon}</span>
        <h2>{title}</h2>
        <div className="actions centred">{children}</div>
      </motion.div>
    </div>
  );
}
