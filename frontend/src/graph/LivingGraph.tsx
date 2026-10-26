// The living graph: the portfolio's supply chain as a network that breathes. Companies settle into a web (or, at the
// flip of a switch, into a left-to-right flow), float gently where they rest, and answer the pointer: point at one and
// it lifts while its suppliers and customers light up; drag it and its neighbours follow on springs; let go and the web
// settles around where it was dropped. Arrows are dashes travelling from supplier to customer in their holding's
// colour. The view zooms smoothly around the pointer, drifts after a pan, and flies to whatever is chosen.
//
// It takes the same information the page gave the Cytoscape graph it replaced, so the page barely changed: the
// companies and relationships, what is chosen, the Logo.dev token and domains, and where to remember the layout.
//
// Drawing is on one <canvas> (render.ts); the controls over it are HTML. A visitor whose computer is set to reduce
// motion gets a still graph: no entrance, float, travelling dashes or flights.
import '@fontsource-variable/geist';
import { AnimatePresence, motion } from 'motion/react';
import { Maximize2, Minimize2, Minus, Plus, RotateCcw, Scan, Search, X } from 'lucide-react';
import {
  type CSSProperties,
  type PointerEvent as ReactPointerEvent,
  type KeyboardEvent as ReactKeyboardEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { Severity } from '../api/news';
import { type GraphEdge, type GraphNode, forgetLayout, readSavedLayout, saveLayout } from '../pages/supplyChain';
import { type Bounds, CameraRig, centredOn, fitCamera, toWorld } from './camera';
import { edgeAt, nodeAt } from './geometry';
import { LogoBook } from './logos';
import {
  type LiveEdge,
  type LiveNode,
  type Risk,
  ALERT_COLOUR,
  boxOf,
  buildLiveGraph,
  estimateWidth,
  eventLabel,
} from './model';
import { restoreSettled, settleForView } from './physics';
import { FONT, type MapFrame, mapFrame, render, renderMinimap } from './render';
import { Scene } from './scene';
import './livingGraph.css';

type Props = {
  nodes: GraphNode[];
  edges: GraphEdge[];
  onEdgeSelect: (relationshipId: number) => void;
  // A company clicked on the graph, or null when the background is clicked.
  onNodeSelect?: (nodeId: string | null) => void;
  // The company chosen on the page: lit with its neighbours, and flown to.
  selectedNodeId?: string | null;
  // The relationship chosen on the page: its arrow is lit and flown to.
  selectedRelationshipId?: number | null;
  // The Logo.dev token; empty when none is set (every company then shows its initials).
  logoToken?: string;
  // Company name to website domain, for companies without a ticker.
  logoDomains?: Record<string, string>;
  // Where this portfolio's layout is remembered for the browser tab (sessionStorage).
  layoutKey?: string;
  // The disruptions running through the graph's links, by relationship id, from the portfolio's open alerts.
  risks?: Map<number, Risk>;
  // The most severe open alert of each company, by node id: each is marked with a dot in that severity's colour.
  alertDots?: Map<string, Severity>;
};

type Tip =
  | { kind: 'node'; node: LiveNode; x: number; y: number }
  | { kind: 'edge'; edge: LiveEdge; x: number; y: number }
  | null;

// How far the pointer must move before a press becomes a drag, in pixels.
const DRAG_START = 4;
// How close to an arrow the pointer must be to point at it, in pixels.
const EDGE_REACH = 7;
// How fast the physics runs while a company is dragged (d3-force's alpha).
const DRAG_HEAT = 0.22;
// The minimap's size on screen.
const MAP_WIDTH = 176;
const MAP_HEIGHT = 112;
// How warm the physics may still be when the view fits around companies on their way somewhere new.
const NEARLY_SETTLED = 0.12;
// How much room above the pointer the card needs; nearer the top, it goes below instead.
const TIP_ROOM = 96;
// One step of the zoom buttons and keys.
const ZOOM_STEP = 1.35;
// The room a fit leaves for the controls along the top and the key along the bottom.
const FIT_ROOM = { top: 70, right: 40, bottom: 104, left: 40 };
const EMPTY: Record<string, string> = {};
const NO_RISKS = new Map<number, Risk>();
const NO_DOTS = new Map<string, Severity>();

/** A holding's share of the portfolio, as shown: "18%", "12.5%". */
const share = (weight: number) => `${Number(weight.toFixed(1))}%`;

function reducesMotion(): boolean {
  return typeof window !== 'undefined' && (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false);
}

/** Measures label text with the browser, in the graph's font; an estimate where there is no canvas (tests). */
function measurer(): (text: string, fontSize: number) => number {
  const context = typeof document !== 'undefined' ? document.createElement('canvas').getContext?.('2d') : null;
  if (!context) return estimateWidth;
  return (text, fontSize) => {
    context.font = `600 ${fontSize}px ${FONT}`;
    return context.measureText(text).width;
  };
}

/** The box around where the given companies are drawn. */
function boundsAround(scene: Scene, nodes: LiveNode[]): Bounds {
  let x1 = Infinity;
  let y1 = Infinity;
  let x2 = -Infinity;
  let y2 = -Infinity;
  for (const node of nodes) {
    const at = scene.positionOf(node);
    const box = boxOf(node, at.x, at.y);
    x1 = Math.min(x1, box.x1);
    y1 = Math.min(y1, box.y1);
    x2 = Math.max(x2, box.x2);
    y2 = Math.max(y2, box.y2);
  }
  return Number.isFinite(x1) ? { x1, y1, x2, y2 } : { x1: -100, y1: -100, x2: 100, y2: 100 };
}

export function LivingGraph({
  nodes,
  edges,
  onEdgeSelect,
  onNodeSelect,
  selectedNodeId = null,
  selectedRelationshipId = null,
  logoToken = '',
  logoDomains = EMPTY,
  layoutKey,
  risks = NO_RISKS,
  alertDots = NO_DOTS,
}: Props) {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const minimapRef = useRef<HTMLCanvasElement | null>(null);
  // How the minimap last mapped the graph, so a click on it can be turned back into a place on the graph.
  const mapFrameRef = useRef<MapFrame | null>(null);
  const sceneRef = useRef<Scene | null>(null);
  const rigRef = useRef<CameraRig | null>(null);
  const viewChosenRef = useRef(false);
  // Set when the companies are on their way somewhere new (Reset Layout): the view fits around them once
  // they have nearly arrived.
  const fitWhenSettledRef = useRef(false);
  // Where the pointer is over the graph, for the crosshair; null when it is away, or dragging.
  const pointerRef = useRef<{ x: number; y: number } | null>(null);
  const searchRef = useRef<HTMLInputElement | null>(null);
  const still = useMemo(reducesMotion, []);
  const [full, setFull] = useState(false);
  const [zoom, setZoom] = useState(1);
  const [tip, setTip] = useState<Tip>(null);
  const [spotlight, setSpotlight] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [searching, setSearching] = useState(false);
  const [highlighted, setHighlighted] = useState(0);
  const [touched, setTouched] = useState(false);

  // Read by the long-lived handlers, so changing them does not rebuild the graph.
  const latest = useRef({ onEdgeSelect, onNodeSelect, logoToken, logoDomains, layoutKey });
  latest.current = { onEdgeSelect, onNodeSelect, logoToken, logoDomains, layoutKey };
  const redrawRef = useRef(() => {});
  const logos = useMemo(() => new LogoBook(() => redrawRef.current()), []);

  const graph = useMemo(() => buildLiveGraph(nodes, edges, measurer()), [nodes, edges]);

  const remember = useCallback(() => {
    const scene = sceneRef.current;
    const rig = rigRef.current;
    const key = latest.current.layoutKey;
    if (!scene || !rig || !key) return;
    // Every company's place, so coming back shows the graph exactly as it was; and which of them the user placed.
    const positions: Record<string, { x: number; y: number }> = {};
    const placed: string[] = [];
    for (const node of scene.graph.nodes) {
      if (node.anchorX != null && node.anchorY != null) {
        positions[node.id] = { x: node.anchorX, y: node.anchorY };
        placed.push(node.id);
      } else {
        positions[node.id] = { x: node.x, y: node.y };
      }
    }
    saveLayout(key, {
      positions,
      placed,
      labelRoom: scene.graph.nodes[0]?.labelRoom ?? 1,
      ...(viewChosenRef.current ? { zoom: rig.camera.k, pan: { x: rig.camera.x, y: rig.camera.y } } : {}),
    });
  }, []);

  const fitAll = useCallback((instant = false) => {
    const scene = sceneRef.current;
    const rig = rigRef.current;
    if (!scene || !rig) return;
    const target = fitCamera(boundsAround(scene, scene.graph.nodes), rig.width, rig.height, FIT_ROOM);
    if (instant) rig.jump(target);
    else rig.flyTo(target);
  }, []);

  const fitAllRef = useRef(fitAll);
  fitAllRef.current = fitAll;

  /** Flies to a company with its neighbours in view. */
  const flyToNode = useCallback((nodeId: string) => {
    const scene = sceneRef.current;
    const rig = rigRef.current;
    const node = scene?.nodeById(nodeId);
    if (!scene || !rig || !node) return;
    const around = scene.graph.edges
      .filter((edge) => edge.source.id === nodeId || edge.target.id === nodeId)
      .flatMap((edge) => [edge.source, edge.target]);
    const view = fitCamera(boundsAround(scene, [node, ...around]), rig.width, rig.height, 110);
    const at = scene.positionOf(node);
    // Centred on the company itself, no further out than needed to see its neighbours, nor closer than 1:1.
    rig.flyTo(centredOn(at, Math.min(1.1, Math.max(view.k, 0.55)), rig.width, rig.height));
    viewChosenRef.current = true;
  }, []);

  // The scene: built for each new graph, settled out of sight, and drawn every frame.
  useEffect(() => {
    const canvas = canvasRef.current;
    const root = rootRef.current;
    if (!canvas || !root) return;
    const rect = root.getBoundingClientRect();
    const width = rect.width || root.clientWidth || 800;
    const height = rect.height || root.clientHeight || 500;

    // What the user did to this portfolio's graph earlier in the tab: companies they placed, and the view.
    // When every company of this graph has a saved place, it is put back exactly; otherwise (a company has come or
    // gone since) it settles again, with the companies the user placed held where they were put.
    const saved = latest.current.layoutKey ? readSavedLayout(latest.current.layoutKey) : null;
    const placed = new Set(saved?.placed ?? Object.keys(saved?.positions ?? {}));
    const complete = saved?.placed !== undefined && graph.nodes.every((node) => saved.positions[node.id]);
    for (const node of graph.nodes) {
      const point = saved?.positions[node.id];
      if (!point || !(complete || placed.has(node.id))) continue;
      node.x = point.x;
      node.y = point.y;
      if (placed.has(node.id)) {
        node.anchorX = point.x;
        node.anchorY = point.y;
      }
    }
    const scene = new Scene(graph, still);
    if (complete) restoreSettled(scene.physics, saved.labelRoom ?? 1);
    // Settled for the view it opens in: every label with room at that zoom, every link clear of other companies.
    else settleForView(scene.physics, (bounds) => fitCamera(bounds, width, height, FIT_ROOM).k);
    sceneRef.current = scene;
    const rig = new CameraRig(still);
    rig.setSize(width, height);
    rigRef.current = rig;
    if (saved?.zoom !== undefined && saved.pan) {
      rig.jump({ k: saved.zoom, x: saved.pan.x, y: saved.pan.y });
      viewChosenRef.current = true;
    } else {
      rig.jump(fitCamera(boundsAround(scene, graph.nodes), width, height, FIT_ROOM));
      viewChosenRef.current = false;
    }

    const context = canvas.getContext?.('2d') ?? null;
    let size = { width, height, dpr: window.devicePixelRatio || 1 };
    const resize = () => {
      const box = root.getBoundingClientRect();
      const dpr = window.devicePixelRatio || 1;
      size = { width: Math.max(1, box.width), height: Math.max(1, box.height), dpr };
      canvas.width = Math.round(size.width * dpr);
      canvas.height = Math.round(size.height * dpr);
      canvas.style.width = `${size.width}px`;
      canvas.style.height = `${size.height}px`;
      rig.setSize(size.width, size.height);
      if (!viewChosenRef.current)
        rig.jump(fitCamera(boundsAround(scene, graph.nodes), size.width, size.height, FIT_ROOM));
    };
    resize();
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(resize);
    observer?.observe(root);

    let frame: number | undefined;
    let last: number | null = null;
    let shownZoom = rig.camera.k;
    setZoom(shownZoom);
    const draw = (now: number) => {
      frame = undefined;
      const dt = last === null ? 16 : Math.min(64, now - last);
      last = now;
      scene.update(dt, now);
      const camera = rig.step(dt, now);
      scene.zoom = camera.k;
      scene.placeLabels(camera, size.width, size.height);
      if (fitWhenSettledRef.current && scene.physics.alpha() < NEARLY_SETTLED) {
        fitWhenSettledRef.current = false;
        fitAllRef.current();
      }
      if (Math.abs(camera.k - shownZoom) > 0.005) {
        shownZoom = camera.k;
        setZoom(camera.k);
      }
      if (context) {
        render(context, {
          scene,
          camera,
          width: size.width,
          height: size.height,
          dpr: size.dpr,
          now,
          logos,
          logoToken: latest.current.logoToken,
          logoDomains: latest.current.logoDomains,
          still,
          pointer: pointerRef.current,
        });
      }
      const minimap = minimapRef.current;
      const mapContext = minimap?.getContext?.('2d') ?? null;
      if (minimap && mapContext) {
        const dpr = size.dpr;
        if (minimap.width !== Math.round(MAP_WIDTH * dpr)) {
          minimap.width = Math.round(MAP_WIDTH * dpr);
          minimap.height = Math.round(MAP_HEIGHT * dpr);
        }
        const frame = mapFrame(boundsAround(scene, scene.graph.nodes), MAP_WIDTH, MAP_HEIGHT);
        mapFrameRef.current = frame;
        renderMinimap(mapContext, scene, camera, frame, {
          width: MAP_WIDTH,
          height: MAP_HEIGHT,
          dpr,
          viewWidth: size.width,
          viewHeight: size.height,
        });
      }
      // Alive: the next frame always comes, unless motion is reduced and nothing is moving.
      if (!still || scene.warm || rig.moving || !scene.entered) frame = window.requestAnimationFrame(draw);
    };
    const wake = () => {
      if (frame === undefined) frame = window.requestAnimationFrame(draw);
    };
    redrawRef.current = wake;
    wake();

    return () => {
      if (frame !== undefined) window.cancelAnimationFrame(frame);
      observer?.disconnect();
      redrawRef.current = () => {};
      sceneRef.current = null;
      scene.physics.stop();
    };
    // Only a new graph rebuilds the scene: logos and still never change.
  }, [graph]);

  // The disruptions: marked on their links.
  useEffect(() => {
    const scene = sceneRef.current;
    if (!scene) return;
    scene.risks = risks;
    redrawRef.current();
  }, [risks, graph]);

  // The companies with open alerts: marked with a dot.
  useEffect(() => {
    const scene = sceneRef.current;
    if (!scene) return;
    scene.alertDots = alertDots;
    redrawRef.current();
  }, [alertDots, graph]);

  // What the page has chosen: lit, and flown to.
  useEffect(() => {
    const scene = sceneRef.current;
    if (!scene) return;
    scene.selectedNode = selectedNodeId;
    if (selectedNodeId) flyToNode(selectedNodeId);
    redrawRef.current();
  }, [selectedNodeId, flyToNode, graph]);

  useEffect(() => {
    const scene = sceneRef.current;
    const rig = rigRef.current;
    if (!scene || !rig) return;
    const edge = scene.graph.edges.find((item) => item.relationshipId === selectedRelationshipId);
    scene.selectedEdge = edge?.id ?? null;
    if (edge) {
      // Centred on the arrow's middle, close enough to see both ends.
      const view = fitCamera(boundsAround(scene, [edge.source, edge.target]), rig.width, rig.height, 140);
      const from = scene.positionOf(edge.source);
      const to = scene.positionOf(edge.target);
      const middle = { x: (from.x + to.x) / 2, y: (from.y + to.y) / 2 };
      rig.flyTo(centredOn(middle, Math.min(1.1, Math.max(0.6, view.k)), rig.width, rig.height));
      viewChosenRef.current = true;
    }
    redrawRef.current();
  }, [selectedRelationshipId, graph]);

  useEffect(() => {
    const scene = sceneRef.current;
    if (!scene) return;
    scene.spotlight = spotlight;
    redrawRef.current();
  }, [spotlight, graph]);

  // Pointer: point, press, drag a company, pan the background, click, double-click.
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const pointers = new Map<number, { x: number; y: number }>();
    let press: {
      id: number;
      start: { x: number; y: number };
      node: LiveNode | null;
      dragging: boolean;
      panning: boolean;
      offset: { x: number; y: number };
      last: { x: number; y: number; t: number };
      velocity: { x: number; y: number };
    } | null = null;
    let pinch: { distance: number } | null = null;

    const local = (event: PointerEvent | WheelEvent | MouseEvent) => {
      const box = canvas.getBoundingClientRect();
      return { x: event.clientX - box.left, y: event.clientY - box.top };
    };
    const hit = (screen: { x: number; y: number }) => {
      const scene = sceneRef.current;
      const rig = rigRef.current;
      if (!scene || !rig) return { node: null, edge: null };
      const world = toWorld(rig.camera, screen);
      const node = nodeAt(scene.graph.nodes, scene.positionOf, world, scene.sizeOf);
      const edge = node
        ? null
        : edgeAt(scene.graph.edges, scene.positionOf, world, EDGE_REACH / rig.camera.k, scene.sizeOf);
      return { node, edge };
    };
    const point = (screen: { x: number; y: number }) => {
      const scene = sceneRef.current;
      if (!scene) return;
      const { node, edge } = hit(screen);
      pointerRef.current = screen;
      scene.hoveredNode = node?.id ?? null;
      scene.hoveredEdge = edge?.id ?? null;
      canvas.style.cursor = node ? 'grab' : edge ? 'pointer' : 'default';
      setTip(
        node
          ? { kind: 'node', node, x: screen.x, y: screen.y }
          : edge && edge.id !== scene.selectedEdge
            ? { kind: 'edge', edge, x: screen.x, y: screen.y }
            : null,
      );
      redrawRef.current();
    };

    const down = (event: PointerEvent) => {
      const screen = local(event);
      pointers.set(event.pointerId, screen);
      // Keep the pointer while it drags, even off the graph; a pointer the browser no longer tracks cannot be kept.
      try {
        canvas.setPointerCapture?.(event.pointerId);
      } catch {
        // The drag still works while the pointer stays over the graph.
      }
      setTouched(true);
      if (pointers.size === 2) {
        const [a, b] = [...pointers.values()];
        pinch = { distance: Math.hypot(a!.x - b!.x, a!.y - b!.y) };
        press = null;
        return;
      }
      const { node } = hit(screen);
      const now = performance.now();
      press = {
        id: event.pointerId,
        start: screen,
        node,
        dragging: false,
        panning: false,
        offset: { x: 0, y: 0 },
        last: { ...screen, t: now },
        velocity: { x: 0, y: 0 },
      };
      rigRef.current?.stop();
    };

    const move = (event: PointerEvent) => {
      const screen = local(event);
      const scene = sceneRef.current;
      const rig = rigRef.current;
      if (!scene || !rig) return;
      if (pointers.has(event.pointerId)) pointers.set(event.pointerId, screen);
      if (pinch && pointers.size === 2) {
        const [a, b] = [...pointers.values()];
        const distance = Math.hypot(a!.x - b!.x, a!.y - b!.y);
        rig.zoomAt(distance / Math.max(1, pinch.distance), { x: (a!.x + b!.x) / 2, y: (a!.y + b!.y) / 2 });
        pinch.distance = distance;
        viewChosenRef.current = true;
        redrawRef.current();
        return;
      }
      if (!press || press.id !== event.pointerId) {
        if (event.buttons === 0) point(screen);
        return;
      }
      const moved = Math.hypot(screen.x - press.start.x, screen.y - press.start.y);
      if (!press.dragging && !press.panning && moved > DRAG_START) {
        pointerRef.current = null;
        if (press.node) {
          press.dragging = true;
          const at = scene.positionOf(press.node);
          const world = toWorld(rig.camera, press.start);
          press.offset = { x: world.x - at.x, y: world.y - at.y };
          press.node.fx = at.x;
          press.node.fy = at.y;
          scene.hold(press.node.id);
          scene.heat(DRAG_HEAT);
          canvas.style.cursor = 'grabbing';
          setTip(null);
        } else {
          press.panning = true;
          canvas.style.cursor = 'grabbing';
          setTip(null);
        }
      }
      const now = performance.now();
      if (press.dragging && press.node) {
        const world = toWorld(rig.camera, screen);
        press.node.fx = world.x - press.offset.x;
        press.node.fy = world.y - press.offset.y;
      } else if (press.panning) {
        const dx = screen.x - press.last.x;
        const dy = screen.y - press.last.y;
        rig.panBy(dx, dy);
        const elapsed = Math.max(1, now - press.last.t);
        // The pan's speed, smoothed, for the drift when it is let go.
        press.velocity = {
          x: press.velocity.x * 0.6 + (dx / elapsed) * 0.4,
          y: press.velocity.y * 0.6 + (dy / elapsed) * 0.4,
        };
        viewChosenRef.current = true;
      }
      press.last = { ...screen, t: now };
      redrawRef.current();
    };

    const up = (event: PointerEvent) => {
      pointers.delete(event.pointerId);
      if (pointers.size < 2) pinch = null;
      const scene = sceneRef.current;
      const rig = rigRef.current;
      if (!press || press.id !== event.pointerId || !scene || !rig) return;
      const done = press;
      press = null;
      if (done.dragging) {
        // Dropped: it stays where it was put, and the web settles around it.
        scene.hold(null);
        scene.heat(0);
        canvas.style.cursor = 'grab';
        remember();
      } else if (done.panning) {
        if (performance.now() - done.last.t < 80) rig.release(done.velocity);
        canvas.style.cursor = 'default';
        remember();
      } else {
        const { node, edge } = hit(local(event));
        if (node) latest.current.onNodeSelect?.(node.id);
        else if (edge) latest.current.onEdgeSelect(edge.relationshipId);
        else {
          latest.current.onNodeSelect?.(null);
          setSpotlight(null);
        }
      }
      redrawRef.current();
    };

    const leave = () => {
      const scene = sceneRef.current;
      if (!scene || press) return;
      pointerRef.current = null;
      scene.hoveredNode = null;
      scene.hoveredEdge = null;
      setTip(null);
      redrawRef.current();
    };

    const wheel = (event: WheelEvent) => {
      event.preventDefault();
      const rig = rigRef.current;
      if (!rig) return;
      // A trackpad pinch arrives as a wheel with the control key; it moves in small steps, so it counts for more.
      const factor = Math.exp(-event.deltaY * (event.ctrlKey ? 0.012 : 0.0016));
      rig.zoomAt(factor, local(event));
      viewChosenRef.current = true;
      setTouched(true);
      redrawRef.current();
      window.clearTimeout(wheelTimer);
      wheelTimer = window.setTimeout(remember, 400);
    };
    let wheelTimer: number | undefined;

    const doubleClick = (event: MouseEvent) => {
      const { node } = hit(local(event));
      if (node) flyToNode(node.id);
      else fitAll();
      redrawRef.current();
    };

    canvas.addEventListener('pointerdown', down);
    canvas.addEventListener('pointermove', move);
    canvas.addEventListener('pointerup', up);
    canvas.addEventListener('pointercancel', up);
    canvas.addEventListener('pointerleave', leave);
    canvas.addEventListener('wheel', wheel, { passive: false });
    canvas.addEventListener('dblclick', doubleClick);
    return () => {
      window.clearTimeout(wheelTimer);
      canvas.removeEventListener('pointerdown', down);
      canvas.removeEventListener('pointermove', move);
      canvas.removeEventListener('pointerup', up);
      canvas.removeEventListener('pointercancel', up);
      canvas.removeEventListener('pointerleave', leave);
      canvas.removeEventListener('wheel', wheel);
      canvas.removeEventListener('dblclick', doubleClick);
    };
  }, [remember, fitAll, flyToNode]);

  // The minimap: pressing it flies the view there; dragging across it moves the view along.
  const travel = (event: ReactPointerEvent<HTMLCanvasElement>, glide: boolean) => {
    const frame = mapFrameRef.current;
    const rig = rigRef.current;
    if (!frame || !rig) return;
    const box = event.currentTarget.getBoundingClientRect();
    const world = {
      x: (event.clientX - box.left - frame.x) / frame.scale,
      y: (event.clientY - box.top - frame.y) / frame.scale,
    };
    const target = centredOn(world, rig.camera.k, rig.width, rig.height);
    if (glide) rig.flyTo(target);
    else rig.jump(target);
    viewChosenRef.current = true;
    setTouched(true);
    redrawRef.current();
  };

  const zoomBy = (factor: number) => {
    const rig = rigRef.current;
    if (!rig) return;
    rig.zoomAt(factor, { x: rig.width / 2, y: rig.height / 2 });
    viewChosenRef.current = true;
    redrawRef.current();
  };

  const resetLayout = () => {
    const scene = sceneRef.current;
    if (!scene) return;
    // Every company the user placed is let go, and the web settles again by itself.
    for (const node of scene.graph.nodes) {
      node.anchorX = null;
      node.anchorY = null;
    }
    if (layoutKey) forgetLayout(layoutKey);
    scene.physics.alpha(0.8);
    viewChosenRef.current = false;
    fitWhenSettledRef.current = true;
    redrawRef.current();
  };

  // Search: companies whose name or ticker holds what was typed, holdings first.
  const matches = useMemo(() => {
    const wanted = query.trim().toLowerCase();
    if (!wanted) return [];
    return graph.nodes
      .filter((node) => node.name.toLowerCase().includes(wanted) || node.ticker?.toLowerCase().includes(wanted))
      .sort((a, b) => Number(b.isHolding) - Number(a.isHolding) || a.name.localeCompare(b.name))
      .slice(0, 6);
  }, [graph, query]);

  const choose = (node: LiveNode) => {
    onNodeSelect?.(node.id);
    flyToNode(node.id);
    setQuery('');
    setSearching(false);
    canvasRef.current?.focus();
  };

  const searchKeys = (event: ReactKeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setHighlighted((index) => Math.min(matches.length - 1, index + 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlighted((index) => Math.max(0, index - 1));
    } else if (event.key === 'Enter' && matches[highlighted]) {
      choose(matches[highlighted]!);
    } else if (event.key === 'Escape') {
      setQuery('');
      setSearching(false);
      canvasRef.current?.focus();
    }
  };

  // Keys anywhere on the graph: / or Ctrl+K to search, + and - to zoom, 0 to fit, F for full screen, Esc to let go.
  const graphKeys = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if ((event.target as HTMLElement).tagName === 'INPUT') return;
    if (event.key === '/' || (event.key.toLowerCase() === 'k' && (event.ctrlKey || event.metaKey))) {
      event.preventDefault();
      setSearching(true);
      window.setTimeout(() => searchRef.current?.focus(), 0);
    } else if (event.key === '+' || event.key === '=') zoomBy(ZOOM_STEP);
    else if (event.key === '-') zoomBy(1 / ZOOM_STEP);
    else if (event.key === '0') fitAll();
    else if (event.key.toLowerCase() === 'f') setFull((value) => !value);
    else if (event.key === 'Escape') {
      if (full) setFull(false);
      setSpotlight(null);
      onNodeSelect?.(null);
    }
  };

  useEffect(() => {
    // The graph changes size: fit again once it has, unless the user has chosen a view.
    const timer = window.setTimeout(() => {
      if (!viewChosenRef.current) fitAll();
    }, 260);
    return () => window.clearTimeout(timer);
  }, [full, fitAll]);

  const plural = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`;
  const companies = plural(graph.nodes.length, 'Company', 'Companies');
  const counts = `${companies} · ${plural(graph.edges.length, 'Link', 'Links')}`;
  const selectedEdge = graph.edges.find((edge) => edge.relationshipId === selectedRelationshipId) ?? null;
  const selectedNode = graph.nodes.find((node) => node.id === selectedNodeId) ?? null;
  const linksOf = (node: LiveNode) => {
    const suppliers = graph.edges.filter((edge) => edge.target.id === node.id).length;
    const customers = graph.edges.filter((edge) => edge.source.id === node.id).length;
    return `${plural(suppliers, 'Supplier', 'Suppliers')} · ${plural(customers, 'Customer', 'Customers')}`;
  };

  return (
    <>
      {full && <div className="lg-backdrop" aria-hidden="true" onClick={() => setFull(false)} />}
      <div ref={rootRef} className={`lg-root lg-cut lg-mark${full ? ' lg-full' : ''}`} onKeyDown={graphKeys}>
        <canvas ref={canvasRef} className="lg-canvas" role="application" aria-label="Supply chain graph" tabIndex={0} />

        <div className="lg-top">
          <div className="lg-glass lg-title lg-cut lg-mark">
            <span className="lg-pulse" aria-hidden="true" />
            <span className="lg-title-name">Supply Chain</span>
            <span className="lg-title-count">{counts}</span>
          </div>

          <div className="lg-glass lg-search lg-mark" data-open={searching || query ? 'true' : 'false'}>
            <Search size={15} aria-hidden="true" />
            <input
              ref={searchRef}
              aria-label="Find a company"
              placeholder="Find a Company"
              value={query}
              onFocus={() => setSearching(true)}
              onBlur={() => window.setTimeout(() => setSearching(false), 120)}
              onChange={(event) => {
                setQuery(event.target.value);
                setHighlighted(0);
              }}
              onKeyDown={searchKeys}
            />
            {!query && <kbd className="lg-kbd">/</kbd>}
            <AnimatePresence>
              {query && (
                <motion.ul
                  className="lg-glass lg-results"
                  role="listbox"
                  aria-label="Companies found"
                  initial={{ opacity: 0, y: -6, scale: 0.98 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: -6, scale: 0.98 }}
                  transition={{ duration: 0.16 }}
                >
                  {matches.length === 0 && (
                    <li className="lg-result-empty">No company by that name is on the graph.</li>
                  )}
                  {matches.map((node, index) => (
                    <li
                      key={node.id}
                      role="option"
                      aria-selected={index === highlighted}
                      className="lg-result"
                      onMouseDown={(event) => {
                        event.preventDefault();
                        choose(node);
                      }}
                      onMouseEnter={() => setHighlighted(index)}
                    >
                      <span
                        className="lg-result-dot"
                        style={{ background: node.colour ?? (node.sharedCount > 1 ? '#f5b73b' : '#475569') }}
                      />
                      <span className="lg-result-name">{node.name}</span>
                      {node.ticker && <span className="lg-result-ticker">{node.ticker}</span>}
                    </li>
                  ))}
                </motion.ul>
              )}
            </AnimatePresence>
          </div>

          <div className="lg-top-right">
            <button
              type="button"
              className="lg-glass lg-icon-button lg-cut"
              aria-label={full ? 'Leave Full Screen' : 'Full Screen'}
              title={full ? 'Leave Full Screen (F)' : 'Full Screen (F)'}
              onClick={() => setFull((value) => !value)}
            >
              {full ? <Minimize2 size={16} /> : <Maximize2 size={16} />}
            </button>
          </div>
        </div>

        <div className="lg-bottom">
          <div className="lg-glass lg-key lg-cut lg-mark">
            {graph.chains.length > 0 && (
              <ul className="lg-chains" aria-label="Each holding's chain">
                {graph.chains.map((chain) => (
                  <li key={chain.id}>
                    <button
                      type="button"
                      className="lg-chain"
                      aria-pressed={spotlight === chain.colour}
                      onClick={() => setSpotlight((current) => (current === chain.colour ? null : chain.colour))}
                      style={{ '--chain': chain.colour } as CSSProperties}
                    >
                      <span className="lg-chain-swatch" aria-hidden="true" />
                      {chain.label}
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {(graph.nodes.some((node) => node.sharedCount > 1) ||
              risks.size > 0 ||
              alertDots.size > 0 ||
              graph.edges.some((edge) => edge.critical)) && (
              <ul className="lg-legend" aria-label="Graph key">
                {graph.nodes.some((node) => node.sharedCount > 1) && (
                  <li>
                    <span className="lg-legend-badge" aria-hidden="true">
                      2
                    </span>
                    Holdings Sharing It
                  </li>
                )}
                {risks.size > 0 && (
                  <li>
                    <span className="lg-legend-risk" aria-hidden="true" />
                    Disruption
                  </li>
                )}
                {alertDots.size > 0 && (
                  <li>
                    {(['HIGH', 'MEDIUM', 'LOW'] as Severity[])
                      .filter((severity) => [...alertDots.values()].includes(severity))
                      .map((severity) => (
                        <span
                          key={severity}
                          className="lg-legend-dot"
                          data-severity={severity}
                          aria-hidden="true"
                          style={{ background: ALERT_COLOUR[severity] }}
                        />
                      ))}
                    Company With Open Alerts
                  </li>
                )}
                {graph.edges.some((edge) => edge.critical) && (
                  <li>
                    <span className="lg-legend-critical" aria-hidden="true" />
                    Sole or Single Source
                  </li>
                )}
              </ul>
            )}
          </div>

          <div className="lg-bottom-right">
            <div className="lg-glass lg-minimap lg-cut lg-mark">
              <canvas
                ref={minimapRef}
                aria-label="Overview of the whole graph"
                role="img"
                style={{ width: MAP_WIDTH, height: MAP_HEIGHT }}
                onPointerDown={(event) => {
                  try {
                    event.currentTarget.setPointerCapture(event.pointerId);
                  } catch {
                    // Dragging still works while the pointer stays over the minimap.
                  }
                  travel(event, true);
                }}
                onPointerMove={(event) => {
                  if (event.buttons) travel(event, false);
                }}
              />
            </div>
            <div className="lg-glass lg-zoom lg-cut lg-mark" role="group" aria-label="View">
              <button
                type="button"
                className="lg-zoom-button"
                aria-label="Zoom Out"
                onClick={() => zoomBy(1 / ZOOM_STEP)}
              >
                <Minus size={15} />
              </button>
              <span className="lg-zoom-value" aria-live="polite">
                {Math.round(zoom * 100)}%
              </span>
              <button type="button" className="lg-zoom-button" aria-label="Zoom In" onClick={() => zoomBy(ZOOM_STEP)}>
                <Plus size={15} />
              </button>
              <span className="lg-divider" aria-hidden="true" />
              <button
                type="button"
                className="lg-zoom-button"
                aria-label="Fit to Screen"
                title="Fit to Screen (0)"
                onClick={() => fitAll()}
              >
                <Scan size={15} />
              </button>
              <button
                type="button"
                className="lg-zoom-button"
                aria-label="Reset Layout"
                title="Reset Layout: Release Every Company You Placed"
                onClick={resetLayout}
              >
                <RotateCcw size={15} />
              </button>
            </div>
          </div>
        </div>

        <AnimatePresence>
          {!touched && graph.nodes.length > 0 && (
            <motion.p
              className="lg-hint"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0, transition: { delay: 1.6, duration: 0.5 } }}
              exit={{ opacity: 0, y: 8, transition: { duration: 0.25 } }}
            >
              Drag Companies · Scroll to Zoom · Click an Arrow for Its Evidence · Press / to Search
            </motion.p>
          )}
        </AnimatePresence>

        <AnimatePresence>
          {full && (selectedEdge || selectedNode) && (
            <motion.div
              key={selectedEdge?.id ?? selectedNode?.id}
              className="lg-glass lg-chosen lg-cut"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 12 }}
            >
              <span
                className="lg-chosen-swatch"
                style={{ background: selectedEdge?.colour ?? selectedNode?.colour ?? '#cbd5e1' }}
              />
              {selectedEdge ? (
                <p>{selectedEdge.summary}</p>
              ) : (
                <p>
                  <strong>{selectedNode!.name}</strong>
                  <span className="lg-chosen-line">{linksOf(selectedNode!)}</span>
                </p>
              )}
              <button type="button" className="lg-chosen-button lg-cut" onClick={() => setFull(false)}>
                {selectedEdge ? 'Show Evidence' : 'Show Details'}
              </button>
              <button
                type="button"
                className="lg-icon-button lg-chosen-close"
                aria-label="Let Go"
                onClick={() => onNodeSelect?.(null)}
              >
                <X size={14} />
              </button>
            </motion.div>
          )}
        </AnimatePresence>

        <AnimatePresence>
          {tip && (
            <motion.div
              key={tip.kind === 'node' ? tip.node.id : tip.edge.id}
              role="tooltip"
              className={`lg-glass lg-tip lg-cut${tip.y < TIP_ROOM ? ' lg-tip-below' : ''}`}
              // It appears where the pointer is (only fading and growing in), then follows it.
              initial={{ opacity: 0, scale: 0.96, x: tip.x, y: tip.y }}
              animate={{ opacity: 1, scale: 1, x: tip.x, y: tip.y }}
              exit={{ opacity: 0, scale: 0.96, transition: { duration: 0.1 } }}
              transition={{
                opacity: { duration: 0.12 },
                scale: { duration: 0.12 },
                x: { type: 'spring', stiffness: 700, damping: 45 },
                y: { type: 'spring', stiffness: 700, damping: 45 },
              }}
            >
              {tip.kind === 'node' ? (
                <>
                  <p className="lg-tip-title">{tip.node.name}</p>
                  <p className="lg-tip-line">
                    {tip.node.isHolding
                      ? `Holding${tip.node.weight !== null ? ` · ${share(tip.node.weight)} of the Portfolio` : ''}`
                      : (tip.node.sharedSummary ??
                        (tip.node.ticker ? `Listed as ${tip.node.ticker}` : 'Supplier or Customer'))}
                  </p>
                  {alertDots.get(tip.node.id) && (
                    <p className="lg-tip-line lg-tip-alert">
                      <span
                        className="lg-legend-dot"
                        aria-hidden="true"
                        data-severity={alertDots.get(tip.node.id)}
                        style={{ background: ALERT_COLOUR[alertDots.get(tip.node.id)!] }}
                      />
                      {`Most Severe Open Alert: ${eventLabel(alertDots.get(tip.node.id)!)}`}
                    </p>
                  )}
                  {tip.node.anchorX != null && (
                    <p className="lg-tip-line lg-tip-placed">
                      <span className="lg-pin" aria-hidden="true" />
                      Placed by You · Reset Layout Releases It
                    </p>
                  )}
                </>
              ) : (
                <>
                  <p className="lg-tip-title">{tip.edge.summary}</p>
                  {risks.get(tip.edge.relationshipId) && (
                    <p className="lg-tip-line lg-tip-risk">
                      <span
                        className="lg-legend-risk"
                        aria-hidden="true"
                        data-severity={risks.get(tip.edge.relationshipId)!.severity}
                      />
                      {`${eventLabel(risks.get(tip.edge.relationshipId)!.severity)} · ${eventLabel(
                        risks.get(tip.edge.relationshipId)!.eventType,
                      )}: ${risks.get(tip.edge.relationshipId)!.title}`}
                    </p>
                  )}
                  <p className="lg-tip-line">
                    <span className={`lg-status${tip.edge.reviewed ? ' lg-status-approved' : ''}`}>
                      {tip.edge.reviewed ? 'Approved' : 'Not Yet Reviewed'}
                    </span>
                    Click for Its Evidence
                  </p>
                </>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </>
  );
}
