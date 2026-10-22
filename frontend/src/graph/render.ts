// Draws the living graph on a <canvas>, every frame, back to front:
// 1. the backdrop: a deep night gradient, two slow-moving lights (an aurora), and a dot grid that slides at half the
//    speed of a pan, so the graph seems to float above it;
// 2. the arrows, each in four layers: a soft bloom, a faint rail that tapers from supplier to customer, a conveyor of
//    small segments along it through which waves of light run towards the customer, and a chevron at the customer's
//    end that flashes as each wave arrives; the colour deepens from a dim shade of the holding's colour at the supplier
//    to its full colour at the customer. An arrow not yet reviewed is fainter, without the bloom;
// 3. the companies: a soft shadow, a holding's ring in its chain colour with a glow, the tile itself (its logo, or its
//    initials on a gradient of its own), a glassy sheen, a badge on a company several holdings depend on, and a
//    breathing ring on the chosen one;
// 4. the labels, drawn at the screen's own scale so they are sharp at every zoom; those without room fade out (see
//    Scene.placeLabels);
// 5. a vignette and a whisper of film grain over everything;
// 6. a faint crosshair following the pointer, as on a technical drawing.
// While the graph opens, a line of light sweeps across it from left to right, the way goods flow. Textures throughout:
// every fifth grid dot is a cross, faded companies are hatched, holdings have a second hairline inside their edge, and
// each arrow meets its tiles at small square ports, like pads on a circuit board.
import type { Camera } from './camera';
import { arrowEnds, leaveTile } from './geometry';
import { type Logo, logoSource, type LogoBook } from './logos';
import { type LiveNode, LABEL_GAP, TITLE_LINE } from './model';
import type { Scene } from './scene';
import { easeOutBack, easeOutCubic, labelScale } from './scene';
import { labelZoom } from './model';

export const FONT = '"Geist Variable", "Geist", "Inter", system-ui, sans-serif';

// The palette, for a canvas cannot read CSS custom properties.
export const INK = {
  night: '#05070d',
  nightLift: '#0b1220',
  label: '#e8edf5',
  muted: '#8b97ab',
  amber: '#f5b73b',
  selection: '#f1f5ff',
  auroraA: '20, 184, 166',
  auroraB: '99, 102, 241',
  grid: 'rgba(148, 163, 184, 0.11)',
  cross: 'rgba(148, 163, 184, 0.26)',
  live: '#5eead4',
  // A company the user placed.
  pin: '#c4f36a',
};

/** A disruption's colour, by how serious it is. */
export const RISK_COLOUR = { HIGH: '#ff4d5e', MEDIUM: '#ff8a3d', LOW: '#ffd166' } as const;

// How faint companies and arrows outside the focus become.
const DIMMED = 0.13;
// Arrows, on screen: the rail's width at the supplier's end and the customer's, the bloom's and the conveyor's width,
// and the chevron's size.
const RAIL_FROM = 2.6;
const RAIL_TO = 1.1;
const BLOOM_WIDTH = 9;
const TRACK_WIDTH = 2.2;
const CHEVRON = 8;
// The conveyor, in the graph's own units (so its segments keep their place on the arrow as the view zooms): how far
// apart the segments are and how long each is; how far apart the waves of light are, how long a wave's fading trail is,
// and how long the chevron's flash lasts after a wave arrives.
const SEGMENT_PITCH = 10;
const SEGMENT_LENGTH = 5;
const WAVE_GAP = 130;
const WAVE_TRAIL = 52;
const ARRIVAL = 34;
const GRID_STEP = 28;
// Every fifth grid dot is a small cross.
const GRID_MAJOR = 5;
// How far apart hatching's lines are, on screen.
const HATCH_STEP = 5;
const GRAIN_SIZE = 160;

export type RenderOptions = {
  scene: Scene;
  camera: Camera;
  width: number;
  height: number;
  dpr: number;
  now: number;
  logos: LogoBook;
  logoToken: string;
  logoDomains: Record<string, string>;
  still: boolean;
  // Where the pointer is over the graph, on screen; null when it is not, or is dragging something.
  pointer: { x: number; y: number } | null;
};

let gridPattern: { canvas: HTMLCanvasElement; dpr: number } | null = null;
let grain: HTMLCanvasElement | null = null;

/** One repeat of the grid: five by five dots, the first a small cross, so the field has structure. */
function gridTile(dpr: number): HTMLCanvasElement {
  if (gridPattern?.dpr === dpr) return gridPattern.canvas;
  const canvas = document.createElement('canvas');
  const step = GRID_STEP * dpr;
  canvas.width = Math.round(step * GRID_MAJOR);
  canvas.height = Math.round(step * GRID_MAJOR);
  const context = canvas.getContext('2d');
  if (context) {
    const dot = Math.max(1, Math.round(1.4 * dpr));
    for (let row = 0; row < GRID_MAJOR; row++) {
      for (let column = 0; column < GRID_MAJOR; column++) {
        const x = Math.round(step * column + step / 2);
        const y = Math.round(step * row + step / 2);
        if (row === 0 && column === 0) {
          const arm = Math.round(3.5 * dpr);
          context.fillStyle = INK.cross;
          context.fillRect(x - arm, y - dot / 2, arm * 2 + 1, dot);
          context.fillRect(x - dot / 2, y - arm, dot, arm * 2 + 1);
        } else {
          context.fillStyle = INK.grid;
          context.fillRect(x - dot / 2, y - dot / 2, dot, dot);
        }
      }
    }
  }
  gridPattern = { canvas, dpr };
  return canvas;
}

const hatches = new Map<string, HTMLCanvasElement>();

/** One repeat of hatching: a diagonal hairline in a colour, HATCH_STEP apart on screen (device pixels, dpr each). */
function hatchTile(colour: string, dpr: number): HTMLCanvasElement {
  const key = `${colour}@${dpr}`;
  const known = hatches.get(key);
  if (known) return known;
  const canvas = document.createElement('canvas');
  const size = Math.max(2, Math.round(HATCH_STEP * dpr));
  canvas.width = size;
  canvas.height = size;
  const context = canvas.getContext('2d');
  if (context) {
    context.strokeStyle = colour;
    context.lineWidth = Math.max(1, dpr * 0.8);
    context.beginPath();
    // From bottom left to top right, and the pieces that wrap round the corners, so the lines join across repeats.
    context.moveTo(-1, size + 1);
    context.lineTo(size + 1, -1);
    context.moveTo(-1, 1);
    context.lineTo(1, -1);
    context.moveTo(size - 1, size + 1);
    context.lineTo(size + 1, size - 1);
    context.stroke();
  }
  hatches.set(key, canvas);
  return canvas;
}

/**
 * A hatching fill that keeps its spacing on screen whatever the drawing's scale: scale is how many device pixels one
 * unit of the current drawing is (k × dpr for the graph, dpr for the screen).
 */
function hatching(context: CanvasRenderingContext2D, colour: string, dpr: number, scale: number): CanvasPattern | null {
  const pattern = context.createPattern(hatchTile(colour, dpr), 'repeat');
  if (pattern && typeof DOMMatrix !== 'undefined') pattern.setTransform(new DOMMatrix().scale(1 / scale));
  return pattern;
}

function grainTile(): HTMLCanvasElement {
  if (grain) return grain;
  const canvas = document.createElement('canvas');
  canvas.width = GRAIN_SIZE;
  canvas.height = GRAIN_SIZE;
  const context = canvas.getContext('2d');
  if (context) {
    const image = context.createImageData(GRAIN_SIZE, GRAIN_SIZE);
    // A fixed sequence, so the grain is the same every time (and stays still, like paper, not like noise).
    let seed = 7;
    for (let i = 0; i < image.data.length; i += 4) {
      seed = (seed * 16807) % 2147483647;
      const value = seed % 256;
      image.data[i] = value;
      image.data[i + 1] = value;
      image.data[i + 2] = value;
      image.data[i + 3] = 255;
    }
    context.putImageData(image, 0, 0);
  }
  grain = canvas;
  return canvas;
}

// How much of a holding's tile the chamfer cuts from its top-right corner.
const CHAMFER = 0.2;

/** A square path centred on (x, y), with its top-right corner cut off by cut (0 for a plain square). */
function boxPath(context: CanvasRenderingContext2D, x: number, y: number, size: number, cut = 0) {
  const half = size / 2;
  context.beginPath();
  context.moveTo(x - half, y - half);
  context.lineTo(x + half - cut, y - half);
  context.lineTo(x + half, y - half + cut);
  context.lineTo(x + half, y + half);
  context.lineTo(x - half, y + half);
  context.closePath();
}

/** How far a company's tile is chamfered: a holding's top-right corner is cut, every other tile is a plain square. */
function cutOf(node: Pick<LiveNode, 'isHolding'>, size: number): number {
  return node.isHolding ? size * CHAMFER : 0;
}

/**
 * Four corner brackets around a square centred on (x, y): the chosen company's mark, as on a technical drawing.
 * length is how long each arm is.
 */
function brackets(context: CanvasRenderingContext2D, x: number, y: number, size: number, length: number) {
  const half = size / 2;
  context.beginPath();
  for (const [sx, sy] of [
    [-1, -1],
    [1, -1],
    [-1, 1],
    [1, 1],
  ] as const) {
    const cx = x + sx * half;
    const cy = y + sy * half;
    context.moveTo(cx - sx * length, cy);
    context.lineTo(cx, cy);
    context.lineTo(cx, cy - sy * length);
  }
}

function withAlpha(colour: string, alpha: number): string {
  if (colour.startsWith('#') && colour.length === 7) {
    const value = parseInt(colour.slice(1), 16);
    return `rgba(${(value >> 16) & 255}, ${(value >> 8) & 255}, ${value & 255}, ${alpha})`;
  }
  return colour;
}

function drawBackdrop(context: CanvasRenderingContext2D, o: RenderOptions) {
  const { width, height, dpr, camera, now, still } = o;
  context.setTransform(dpr, 0, 0, dpr, 0, 0);
  const base = context.createLinearGradient(0, 0, width * 0.4, height);
  base.addColorStop(0, INK.nightLift);
  base.addColorStop(1, INK.night);
  context.fillStyle = base;
  context.fillRect(0, 0, width, height);

  // Two broad lights drifting slowly across the dark, following the pan a little.
  const t = still ? 0 : now / 1000;
  const reach = Math.max(width, height) * 0.75;
  const lights: [number, number, string, number][] = [
    [
      width * (0.22 + 0.06 * Math.sin(t * 0.07)) + camera.x * 0.15,
      height * (0.18 + 0.05 * Math.cos(t * 0.05)) + camera.y * 0.15,
      INK.auroraA,
      0.13,
    ],
    [
      width * (0.82 + 0.05 * Math.cos(t * 0.06)) + camera.x * 0.15,
      height * (0.85 + 0.06 * Math.sin(t * 0.08)) + camera.y * 0.15,
      INK.auroraB,
      0.12,
    ],
  ];
  for (const [x, y, rgb, strength] of lights) {
    const light = context.createRadialGradient(x, y, 0, x, y, reach);
    light.addColorStop(0, `rgba(${rgb}, ${strength})`);
    light.addColorStop(1, `rgba(${rgb}, 0)`);
    context.fillStyle = light;
    context.fillRect(0, 0, width, height);
  }

  // The dot grid, sliding at half the pan's speed.
  const tile = gridTile(dpr);
  const pattern = context.createPattern(tile, 'repeat');
  if (pattern) {
    const repeat = GRID_STEP * GRID_MAJOR;
    const offsetX = ((camera.x * 0.5) % repeat) - repeat;
    const offsetY = ((camera.y * 0.5) % repeat) - repeat;
    context.setTransform(1, 0, 0, 1, offsetX * dpr, offsetY * dpr);
    context.fillStyle = pattern;
    context.fillRect(0, 0, (width + repeat * 2) * dpr, (height + repeat * 2) * dpr);
    context.setTransform(dpr, 0, 0, dpr, 0, 0);
  }
}

function drawEdges(context: CanvasRenderingContext2D, o: RenderOptions) {
  const { scene, camera, dpr, still, now } = o;
  const k = camera.k;
  // Zoomed out past 1:1, the chevrons shrink with the graph, as its tiles do.
  const shrink = Math.max(k, 1);
  context.setTransform(k * dpr, 0, 0, k * dpr, camera.x * dpr, camera.y * dpr);
  context.lineCap = 'butt';
  context.lineJoin = 'miter';
  // Lit arrows last, so they sit over dimmed ones.
  const edges = [...scene.graph.edges].sort(
    (a, b) =>
      scene.edgeLook.get(a.id)!.lit +
      scene.edgeLook.get(a.id)!.hover -
      (scene.edgeLook.get(b.id)!.lit + scene.edgeLook.get(b.id)!.hover),
  );
  for (const edge of edges) {
    const look = scene.edgeLook.get(edge.id)!;
    if (look.appear <= 0) continue;
    const ends = arrowEnds(
      scene.positionOf(edge.source),
      scene.positionOf(edge.target),
      scene.sizeOf(edge.source),
      scene.sizeOf(edge.target),
    );
    if (!ends) continue;
    const grow = easeOutCubic(look.appear);
    const { start } = ends;
    const full = Math.hypot(ends.end.x - start.x, ends.end.y - start.y);
    const along = { x: (ends.end.x - start.x) / full, y: (ends.end.y - start.y) / full };
    const across = { x: -along.y, y: along.x };
    const length = full * grow;
    const end = { x: start.x + along.x * length, y: start.y + along.y * length };
    const strength =
      (DIMMED + (1 - DIMMED) * look.lit) *
      (edge.reviewed ? 1 : 0.62) *
      (0.3 + 0.7 * Math.min(scene.presenceOf(edge.source), scene.presenceOf(edge.target)));
    const hover = look.hover;
    // The colour deepens along the arrow: a dim shade of the holding's colour at the supplier, its full colour (and,
    // under the pointer, brighter still) at the customer.
    // A disruption on this link (an open alert runs through it): its colour, a pulse, and a conveyor that has stalled.
    const risk = scene.risks.get(edge.relationshipId);
    const colour = risk ? RISK_COLOUR[risk.severity] : edge.colour;
    const alarm = risk ? (still ? 0.6 : 0.5 + 0.5 * Math.sin((now / 650) * Math.PI)) : 0;
    const deep = mix(colour, '#ffffff', hover * 0.35);
    const dim = mix(colour, INK.night, risk ? 0.45 : 0.6);
    const shade = (alphaFrom: number, alphaTo: number) => {
      const gradient = context.createLinearGradient(start.x, start.y, end.x, end.y);
      gradient.addColorStop(0, withAlpha(dim, alphaFrom * strength));
      gradient.addColorStop(1, withAlpha(deep, alphaTo * strength));
      return gradient;
    };

    // 1. The bloom: a soft halo, stronger towards the customer and under the pointer. Reviewed arrows only.
    if (edge.reviewed || hover > 0.01 || risk) {
      context.strokeStyle = shade(0.02 + alarm * 0.08, 0.12 + hover * 0.2 + alarm * 0.22);
      context.lineWidth = (BLOOM_WIDTH + hover * 6 + alarm * 4) / k;
      context.beginPath();
      context.moveTo(start.x, start.y);
      context.lineTo(end.x, end.y);
      context.stroke();
    }

    // 2. The rail: a ribbon tapering from the supplier's end to the customer's.
    const from = (RAIL_FROM + hover * 1) / k / 2;
    const to = (RAIL_TO + hover * 0.6) / k / 2;
    context.fillStyle = shade(0.16, 0.38 + hover * 0.3);
    context.beginPath();
    context.moveTo(start.x + across.x * from, start.y + across.y * from);
    context.lineTo(end.x + across.x * to, end.y + across.y * to);
    context.lineTo(end.x - across.x * to, end.y - across.y * to);
    context.lineTo(start.x - across.x * from, start.y - across.y * from);
    context.closePath();
    context.fill();

    // 3. The conveyor: small segments along the rail, each dim until a wave of light passes through it, when it lights
    // up and fades behind the wave. The waves run from supplier to customer at one steady pace (see Scene.flow); the
    // segments themselves stay still.
    const wave = still || risk ? null : ((scene.flow % WAVE_GAP) + WAVE_GAP) % WAVE_GAP;
    const lit = (distance: number) => {
      // Stalled: every segment pulses together, nothing flows.
      if (risk) return alarm;
      if (wave === null) return 0;
      const behind = (((wave - distance) % WAVE_GAP) + WAVE_GAP) % WAVE_GAP;
      return behind < WAVE_TRAIL ? (1 - behind / WAVE_TRAIL) ** 1.6 : 0;
    };
    const head = mix(colour, '#ffffff', (risk ? 0.2 : 0.5) + hover * 0.3);
    const thick = (TRACK_WIDTH + hover * 0.6) / k;
    context.save();
    context.translate(start.x, start.y);
    context.rotate(Math.atan2(along.y, along.x));
    for (let distance = SEGMENT_PITCH / 2; distance + SEGMENT_LENGTH < length - 4; distance += SEGMENT_PITCH) {
      const glow = lit(distance + SEGMENT_LENGTH);
      const ends = Math.min(1, distance / 14, (length - distance) / 14);
      context.fillStyle = withAlpha(mix(dim, head, 0.35 + glow * 0.65), (0.22 + 0.78 * glow) * strength * ends);
      context.fillRect(distance, -thick / 2, SEGMENT_LENGTH, thick);
    }
    context.restore();

    // 4. The chevron at the customer's end, once the arrow has nearly reached it, flashing as each wave arrives.
    if (grow > 0.85) {
      const since = wave === null ? ARRIVAL : (((wave - length) % WAVE_GAP) + WAVE_GAP) % WAVE_GAP;
      const flash = since < ARRIVAL ? 1 - since / ARRIVAL : 0;
      const size = (CHEVRON + hover * 2 + flash * 2) / shrink;
      const back = { x: end.x - along.x * size, y: end.y - along.y * size };
      const chevron = () => {
        context.beginPath();
        context.moveTo(back.x + across.x * size * 0.62, back.y + across.y * size * 0.62);
        context.lineTo(end.x, end.y);
        context.lineTo(back.x - across.x * size * 0.62, back.y - across.y * size * 0.62);
        context.stroke();
      };
      const shown = Math.min(1, strength * ((grow - 0.85) / 0.15));
      if (flash > 0.01) {
        context.strokeStyle = withAlpha(head, 0.35 * flash * shown);
        context.lineWidth = (6 + hover) / shrink;
        chevron();
      }
      context.strokeStyle = withAlpha(mix(deep, '#ffffff', 0.25 + flash * 0.5), shown);
      context.lineWidth = (1.8 + hover * 0.6 + flash * 0.6) / shrink;
      chevron();
    }
  }
}

/** Two #rrggbb colours mixed, share 0 the first, 1 the second. */
export function mix(a: string, b: string, share: number): string {
  if (!a.startsWith('#') || !b.startsWith('#')) return a;
  const x = parseInt(a.slice(1), 16);
  const y = parseInt(b.slice(1), 16);
  const channel = (shift: number) =>
    Math.round(((x >> shift) & 255) + (((y >> shift) & 255) - ((x >> shift) & 255)) * share);
  return `#${[16, 8, 0].map((shift) => channel(shift).toString(16).padStart(2, '0')).join('')}`;
}

function drawTile(context: CanvasRenderingContext2D, node: LiveNode, logo: Logo, x: number, y: number, size: number) {
  const cut = cutOf(node, size);
  // The tile's face: the logo's own background, white, dark, or the company's own gradient.
  boxPath(context, x, y, size, cut);
  if (logo.state === 'ready') {
    context.fillStyle = logo.background;
  } else {
    const face = context.createLinearGradient(x - size / 2, y - size / 2, x + size / 2, y + size / 2);
    face.addColorStop(0, `hsl(${node.hue}, 62%, 52%)`);
    face.addColorStop(1, `hsl(${(node.hue + 38) % 360}, 66%, 30%)`);
    context.fillStyle = face;
  }
  context.fill();

  context.save();
  boxPath(context, x, y, size, cut);
  context.clip();
  if (logo.state === 'ready') {
    // The logo's own part of its image (its margin trimmed off), fitted without stretching: edge to edge when it fills
    // the tile, otherwise inside an even margin, so every mark looks the same size.
    const room = logo.treatment === 'bleed' ? size : size * 0.64;
    const image = logo.image as HTMLImageElement;
    const natural = { w: image.naturalWidth || image.width || 1, h: image.naturalHeight || image.height || 1 };
    const crop = {
      x: logo.crop.x * natural.w,
      y: logo.crop.y * natural.h,
      w: logo.crop.w * natural.w,
      h: logo.crop.h * natural.h,
    };
    const fit = Math.min(room / crop.w, room / crop.h);
    const w = crop.w * fit;
    const h = crop.h * fit;
    context.drawImage(logo.image, crop.x, crop.y, crop.w, crop.h, x - w / 2, y - h / 2, w, h);
  } else {
    context.fillStyle = 'rgba(255, 255, 255, 0.95)';
    context.font = `600 ${Math.round(size * 0.36)}px ${FONT}`;
    context.textAlign = 'center';
    context.textBaseline = 'middle';
    context.fillText(logo.state === 'loading' ? '' : node.initials, x, y + size * 0.02);
  }
  // A glassy sheen across the top.
  const sheen = context.createLinearGradient(x, y - size / 2, x, y + size * 0.1);
  sheen.addColorStop(0, 'rgba(255, 255, 255, 0.22)');
  sheen.addColorStop(1, 'rgba(255, 255, 255, 0)');
  context.fillStyle = sheen;
  context.fillRect(x - size / 2, y - size / 2, size, size * 0.6);
  context.restore();

  // A hairline edge: dark on a light face, light on a dark one.
  boxPath(context, x, y, size - 0.5, cut);
  const light = logo.state === 'ready' && logo.treatment === 'plain';
  context.strokeStyle = light ? 'rgba(15, 23, 42, 0.12)' : 'rgba(255, 255, 255, 0.16)';
  context.lineWidth = 1;
  context.stroke();

  // A holding's second hairline, a little inside the first: the tile reads as machined rather than flat.
  if (node.isHolding) {
    const inset = Math.max(2.5, size * 0.06);
    boxPath(context, x, y, size - inset * 2, Math.max(0, cut - inset * (2 - Math.SQRT2)));
    context.strokeStyle = light ? 'rgba(15, 23, 42, 0.14)' : 'rgba(255, 255, 255, 0.22)';
    context.lineWidth = 0.75;
    context.stroke();
  }
}

function drawNodes(context: CanvasRenderingContext2D, o: RenderOptions) {
  const { scene, camera, dpr, now, still, logos, logoToken, logoDomains } = o;
  const k = camera.k;
  context.setTransform(k * dpr, 0, 0, k * dpr, camera.x * dpr, camera.y * dpr);
  // Suppliers and customers first, then holdings, then whatever is pointed at, so the important sit on top.
  const order = [...scene.graph.nodes].sort(
    (a, b) =>
      Number(a.isHolding) - Number(b.isHolding) || scene.nodeLook.get(a.id)!.hover - scene.nodeLook.get(b.id)!.hover,
  );
  for (const node of order) {
    const look = scene.nodeLook.get(node.id)!;
    if (look.appear <= 0) continue;
    const { x, y } = scene.positionOf(node);
    const grow = still ? 1 : easeOutBack(look.appear);
    const size = scene.sizeOf(node) * grow * (1 + look.hover * 0.08);
    if (size <= 0.5) continue;
    // Outside the focus, or receding as the view zooms out (see drawnShare): veiled more, the less it is there.
    const strength = (DIMMED + (1 - DIMMED) * look.lit) * (0.3 + 0.7 * scene.presenceOf(node));
    // Drawn whole, then veiled when outside the focus (below): fading each layer instead would let a logo's own white
    // background show through the tile's, as a brighter square.
    context.globalAlpha = Math.min(1, look.appear * 2);
    const logo = logos.get(logoSource(node, logoToken, logoDomains[node.sourceName]));

    // Lifted off the page: a soft shadow under the tile.
    context.save();
    context.shadowColor = 'rgba(0, 0, 0, 0.6)';
    context.shadowBlur = (14 + look.hover * 14) * k * dpr;
    context.shadowOffsetY = (6 + look.hover * 4) * k * dpr;
    boxPath(context, x, y, size, cutOf(node, size));
    context.fillStyle = '#0b1220';
    context.fill();
    context.restore();

    // A holding's ring, in its chain colour, glowing.
    if (node.isHolding) {
      const colour = node.colour ?? '#94a3b8';
      context.save();
      context.shadowColor = withAlpha(colour, 0.85);
      context.shadowBlur = (12 + look.hover * 10) * k * dpr;
      // 5 out from the tile all round, so its chamfer runs parallel to the tile's, 5 out too.
      boxPath(context, x, y, size + 10, cutOf(node, size) + 5 * (2 - Math.SQRT2));
      context.strokeStyle = withAlpha(colour, 0.95);
      context.lineWidth = 2;
      context.stroke();
      context.restore();
    }

    drawTile(context, node, logo, x, y, size);

    // A company the user placed: a lime wedge on its top-right corner, filling the chamfer's cut on a holding, and
    // folding the corner over on any other company.
    if (node.anchorX != null) {
      const half = size / 2;
      const wedge = node.isHolding ? cutOf(node, size) : size * 0.26;
      context.beginPath();
      context.moveTo(x + half - wedge, y - half);
      context.lineTo(x + half, y - half);
      context.lineTo(x + half, y - half + wedge);
      context.closePath();
      context.fillStyle = INK.pin;
      context.fill();
      context.strokeStyle = INK.night;
      context.lineWidth = 1.2;
      context.stroke();
    }

    // A company several holdings depend on: a small amber badge with how many, on its top-left corner.
    if (node.sharedCount > 1) {
      const bx = x - size / 2 + 2;
      const by = y - size / 2 + 2;
      context.fillStyle = INK.amber;
      context.strokeStyle = INK.night;
      context.lineWidth = 2;
      context.beginPath();
      context.rect(bx - 8, by - 8, 16, 16);
      context.fill();
      context.stroke();
      context.fillStyle = '#1a1205';
      context.font = `700 10px ${FONT}`;
      context.textAlign = 'center';
      context.textBaseline = 'middle';
      context.fillText(String(node.sharedCount), bx, by + 0.5);
    }

    // Outside the focus: veiled in the night's colour and hatched over, as a drawing marks what is set aside.
    if (strength < 0.99) {
      const reach = node.isHolding ? size + 14 : size + 2;
      const cut = node.isHolding ? cutOf(node, size) + 7 * (2 - Math.SQRT2) : 0;
      context.save();
      boxPath(context, x, y, reach, cut);
      context.fillStyle = withAlpha(INK.night, (1 - strength) * 0.9);
      context.fill();
      const pattern = look.lit < 0.99 ? hatching(context, 'rgba(5, 7, 13, 0.85)', dpr, k * dpr) : null;
      if (pattern) {
        context.globalAlpha *= 1 - look.lit;
        context.fillStyle = pattern;
        context.fill();
      }
      context.restore();
    }

    // The chosen company: corner brackets, breathing gently in and out, and a faint square edge between them.
    if (node.id === scene.selectedNode) {
      const breath = still ? 0 : (Math.sin((now / 1400) * Math.PI * 2) + 1) / 2;
      const frame = size + 20 + breath * 6;
      context.strokeStyle = withAlpha(INK.selection, 0.18);
      context.lineWidth = 1 / Math.max(0.6, k);
      boxPath(context, x, y, frame);
      context.stroke();
      context.strokeStyle = INK.selection;
      context.lineWidth = 2 / Math.max(0.6, k);
      brackets(context, x, y, frame, Math.max(7, size * 0.22));
      context.stroke();
    }
  }
  context.globalAlpha = 1;
}

function drawLabels(context: CanvasRenderingContext2D, o: RenderOptions) {
  const { scene, camera, dpr } = o;
  const k = camera.k;
  context.setTransform(dpr, 0, 0, dpr, 0, 0);
  context.textAlign = 'center';
  context.textBaseline = 'alphabetic';
  // Text keeps a readable size: it grows a little when zoomed in, and shrinks a little when zoomed out.
  const scale = labelScale(k);
  for (const node of scene.graph.nodes) {
    const look = scene.nodeLook.get(node.id)!;
    // Shown when it has room (see Scene.placeLabels) and the zoom is close enough for it (labelZoom), and faint with
    // its company when outside the focus.
    const visible = look.label * labelZoom(k, node.isHolding) * (0.15 + 0.85 * look.lit);
    const alpha = visible * Math.min(1, Math.max(0, look.appear * 1.4 - 0.4));
    if (alpha <= 0.02) continue;
    const { x, y } = scene.positionOf(node);
    const grow = 1 + look.hover * 0.08;
    const sx = x * k + camera.x;
    const top = (y + (scene.sizeOf(node) * grow) / 2 + LABEL_GAP) * k + camera.y;
    context.globalAlpha = alpha;
    context.shadowColor = 'rgba(0, 0, 0, 0.85)';
    context.shadowBlur = 8 * dpr;
    context.font = `${node.isHolding ? 600 : 500} ${12 * scale}px ${FONT}`;
    context.fillStyle = INK.label;
    context.fillText(node.title, sx, top + (TITLE_LINE - 3) * scale);
    if (node.subtitle) {
      context.font = `500 ${10.5 * scale}px ${FONT}`;
      context.fillStyle = node.isHolding
        ? mix(node.colour ?? '#94a3b8', '#ffffff', 0.35)
        : node.sharedCount > 1
          ? INK.amber
          : INK.muted;
      context.fillText(node.subtitle, sx, top + (TITLE_LINE + 11) * scale);
    }
  }
  context.shadowBlur = 0;
  context.globalAlpha = 1;
}

function drawFinish(context: CanvasRenderingContext2D, o: RenderOptions) {
  const { width, height, dpr } = o;
  context.setTransform(dpr, 0, 0, dpr, 0, 0);
  const vignette = context.createRadialGradient(
    width / 2,
    height / 2,
    Math.min(width, height) * 0.35,
    width / 2,
    height / 2,
    Math.max(width, height) * 0.78,
  );
  vignette.addColorStop(0, 'rgba(0, 0, 0, 0)');
  vignette.addColorStop(1, 'rgba(0, 0, 0, 0.55)');
  context.fillStyle = vignette;
  context.fillRect(0, 0, width, height);

  const pattern = context.createPattern(grainTile(), 'repeat');
  if (pattern) {
    context.globalAlpha = 0.035;
    context.globalCompositeOperation = 'overlay';
    context.fillStyle = pattern;
    context.fillRect(0, 0, width, height);
    context.globalCompositeOperation = 'source-over';
    context.globalAlpha = 1;
  }
}

/**
 * Where each arrow meets its tiles: a small square port at the tile's edge, in the arrow's colour, as an arrow on a
 * circuit board meets its pads. A port keeps its size on screen, but never grows past a share of its tile.
 */
function drawPorts(context: CanvasRenderingContext2D, o: RenderOptions) {
  const { scene, camera, dpr } = o;
  const k = camera.k;
  context.setTransform(k * dpr, 0, 0, k * dpr, camera.x * dpr, camera.y * dpr);
  for (const edge of scene.graph.edges) {
    const look = scene.edgeLook.get(edge.id)!;
    if (look.appear <= 0.05) continue;
    const strength =
      (DIMMED + (1 - DIMMED) * look.lit) *
      Math.min(1, look.appear * 3) *
      (0.3 + 0.7 * Math.min(scene.presenceOf(edge.source), scene.presenceOf(edge.target)));
    const from = scene.positionOf(edge.source);
    const to = scene.positionOf(edge.target);
    for (const [node, at, towards] of [
      [edge.source, from, to],
      [edge.target, to, from],
    ] as const) {
      const tile = scene.sizeOf(node) * (1 + scene.nodeLook.get(node.id)!.hover * 0.08);
      const point = leaveTile(at, towards, tile, 0);
      const port = Math.min(4.5 / k, tile * 0.14);
      context.fillStyle = withAlpha(INK.night, 0.9 * strength);
      context.fillRect(point.x - port / 2 - 1 / k, point.y - port / 2 - 1 / k, port + 2 / k, port + 2 / k);
      context.fillStyle = withAlpha(edge.colour, strength);
      context.fillRect(point.x - port / 2, point.y - port / 2, port, port);
    }
  }
}

/**
 * The opening sweep: while the companies bloom in, from left to right, a band of light crosses the whole graph with
 * them, fading in and out. Nothing once the graph has opened, or when motion is reduced.
 */
function drawSweep(context: CanvasRenderingContext2D, o: RenderOptions) {
  const { scene, dpr, height, width } = o;
  const progress = scene.entrance();
  if (progress === null || o.still) return;
  // Across the whole graph, edge to edge, at an even pace, fading in at the start and out at the end.
  const x = -40 + (width + 80) * progress;
  const fade = Math.sin(Math.PI * progress);
  context.setTransform(dpr, 0, 0, dpr, 0, 0);
  context.save();
  context.globalCompositeOperation = 'lighter';
  const band = context.createLinearGradient(x - 140, 0, x + 6, 0);
  band.addColorStop(0, `rgba(${INK.auroraA}, 0)`);
  band.addColorStop(1, `rgba(${INK.auroraA}, ${0.14 * fade})`);
  context.fillStyle = band;
  context.fillRect(x - 140, 0, 146, height);
  context.fillStyle = `rgba(167, 243, 208, ${0.45 * fade})`;
  context.fillRect(x, 0, 1, height);
  context.restore();
}

/** A faint crosshair through the pointer, as on a technical drawing; none while dragging, or off the graph. */
function drawCrosshair(context: CanvasRenderingContext2D, o: RenderOptions) {
  const { dpr, width, height, pointer } = o;
  if (!pointer) return;
  context.setTransform(dpr, 0, 0, dpr, 0, 0);
  context.save();
  context.setLineDash([3, 4]);
  context.strokeStyle = 'rgba(148, 163, 184, 0.14)';
  context.lineWidth = 1;
  context.beginPath();
  context.moveTo(Math.round(pointer.x) + 0.5, 0);
  context.lineTo(Math.round(pointer.x) + 0.5, height);
  context.moveTo(0, Math.round(pointer.y) + 0.5);
  context.lineTo(width, Math.round(pointer.y) + 0.5);
  context.stroke();
  context.restore();
}

/**
 * Marks the middle of each disrupted link with a small diamond and "!", in the disruption's colour, drawn at the
 * screen's own scale over everything else, so a disruption is seen even zoomed out.
 */
function drawRiskMarks(context: CanvasRenderingContext2D, o: RenderOptions) {
  const { scene, camera, dpr } = o;
  if (scene.risks.size === 0) return;
  context.setTransform(dpr, 0, 0, dpr, 0, 0);
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.font = `800 8px ${FONT}`;
  for (const edge of scene.graph.edges) {
    const risk = scene.risks.get(edge.relationshipId);
    const look = scene.edgeLook.get(edge.id)!;
    if (!risk || look.appear < 0.9) continue;
    const from = scene.positionOf(edge.source);
    const to = scene.positionOf(edge.target);
    const x = ((from.x + to.x) / 2) * camera.k + camera.x;
    const y = ((from.y + to.y) / 2) * camera.k + camera.y;
    const half = 7;
    context.globalAlpha = DIMMED + (1 - DIMMED) * look.lit;
    context.beginPath();
    context.moveTo(x, y - half);
    context.lineTo(x + half, y);
    context.lineTo(x, y + half);
    context.lineTo(x - half, y);
    context.closePath();
    context.fillStyle = RISK_COLOUR[risk.severity];
    context.fill();
    context.strokeStyle = INK.night;
    context.lineWidth = 1.5;
    context.stroke();
    context.fillStyle = INK.night;
    context.fillText('!', x, y + 0.5);
  }
  context.globalAlpha = 1;
}

/** Draws one frame of the living graph. */
export function render(context: CanvasRenderingContext2D, options: RenderOptions) {
  context.save();
  drawBackdrop(context, options);
  drawEdges(context, options);
  drawNodes(context, options);
  drawPorts(context, options);
  drawRiskMarks(context, options);
  drawSweep(context, options);
  drawLabels(context, options);
  drawFinish(context, options);
  drawCrosshair(context, options);
  context.restore();
}

// The minimap: the whole graph in a small frame, with the part on screen outlined.
const MAP_PADDING = 10;

/** How the minimap maps the graph's own units to its frame: the scale, and where the graph's (0, 0) lands. */
export type MapFrame = { scale: number; x: number; y: number };

/** The minimap's frame for a box of the graph, fitted inside a width and height, with a margin. */
export function mapFrame(
  bounds: { x1: number; y1: number; x2: number; y2: number },
  width: number,
  height: number,
): MapFrame {
  const scale = Math.min(
    (width - MAP_PADDING * 2) / Math.max(1, bounds.x2 - bounds.x1),
    (height - MAP_PADDING * 2) / Math.max(1, bounds.y2 - bounds.y1),
  );
  return {
    scale,
    x: width / 2 - ((bounds.x1 + bounds.x2) / 2) * scale,
    y: height / 2 - ((bounds.y1 + bounds.y2) / 2) * scale,
  };
}

/**
 * Draws the minimap: every arrow as a faint line, every company as a dot (a holding in its chain colour, a little
 * bigger), and the part of the graph on screen as a bright frame, with the rest shaded.
 */
export function renderMinimap(
  context: CanvasRenderingContext2D,
  scene: Scene,
  camera: Camera,
  frame: MapFrame,
  size: { width: number; height: number; dpr: number; viewWidth: number; viewHeight: number },
) {
  const { width, height, dpr, viewWidth, viewHeight } = size;
  context.setTransform(dpr, 0, 0, dpr, 0, 0);
  context.clearRect(0, 0, width, height);
  const map = (point: { x: number; y: number }) => ({
    x: point.x * frame.scale + frame.x,
    y: point.y * frame.scale + frame.y,
  });

  context.lineWidth = 1;
  for (const edge of scene.graph.edges) {
    const from = map(scene.positionOf(edge.source));
    const to = map(scene.positionOf(edge.target));
    context.strokeStyle = withAlpha(edge.colour, 0.35);
    context.beginPath();
    context.moveTo(from.x, from.y);
    context.lineTo(to.x, to.y);
    context.stroke();
  }
  for (const node of scene.graph.nodes) {
    const at = map(scene.positionOf(node));
    context.fillStyle = node.isHolding
      ? (node.colour ?? '#cbd5e1')
      : node.sharedCount > 1
        ? INK.amber
        : 'rgba(203, 213, 225, 0.55)';
    const dot = node.isHolding ? 4 : 2.5;
    context.fillRect(at.x - dot / 2, at.y - dot / 2, dot, dot);
  }

  // What is on screen: the view's corners, in the graph's own units, then on the map, kept within its frame (zoomed
  // out past the whole graph, the frame is the whole map).
  const inset = 3;
  const topLeft = map({ x: -camera.x / camera.k, y: -camera.y / camera.k });
  const bottomRight = map({ x: (viewWidth - camera.x) / camera.k, y: (viewHeight - camera.y) / camera.k });
  const x1 = Math.min(width - inset - 6, Math.max(inset, topLeft.x));
  const y1 = Math.min(height - inset - 6, Math.max(inset, topLeft.y));
  const x2 = Math.max(x1 + 6, Math.min(width - inset, bottomRight.x));
  const y2 = Math.max(y1 + 6, Math.min(height - inset, bottomRight.y));
  const view = { x: x1, y: y1, w: x2 - x1, h: y2 - y1 };

  // Outside the view, shaded and hatched; inside, a faint tint, a dashed teal edge, and bright corner brackets.
  context.beginPath();
  context.rect(0, 0, width, height);
  context.rect(view.x, view.y, view.w, view.h);
  context.fillStyle = 'rgba(2, 4, 9, 0.45)';
  context.fill('evenodd');
  const pattern = hatching(context, 'rgba(148, 163, 184, 0.12)', dpr, dpr);
  if (pattern) {
    context.fillStyle = pattern;
    context.fill('evenodd');
  }
  context.fillStyle = 'rgba(45, 212, 191, 0.06)';
  context.fillRect(view.x, view.y, view.w, view.h);
  context.setLineDash([2, 3]);
  context.strokeStyle = 'rgba(45, 212, 191, 0.45)';
  context.lineWidth = 1;
  context.strokeRect(view.x + 0.5, view.y + 0.5, view.w - 1, view.h - 1);
  context.setLineDash([]);
  const arm = Math.max(3, Math.min(8, view.w / 4, view.h / 4));
  context.strokeStyle = '#5eead4';
  context.lineWidth = 1.5;
  context.beginPath();
  for (const [cx, cy, sx, sy] of [
    [view.x, view.y, 1, 1],
    [view.x + view.w, view.y, -1, 1],
    [view.x, view.y + view.h, 1, -1],
    [view.x + view.w, view.y + view.h, -1, -1],
  ] as const) {
    context.moveTo(cx + sx * arm, cy);
    context.lineTo(cx, cy);
    context.lineTo(cx, cy + sy * arm);
  }
  context.stroke();
}
