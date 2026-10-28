// The living graph's camera: where the view looks and how close, and how it moves. A point in the graph's own units
// (world) appears on screen at world × k + (x, y). Every move eases: a wheel zoom glides in while keeping the point
// under the pointer still, a pan let go keeps drifting and slows, and the view flies to a chosen company.
export type Camera = { x: number; y: number; k: number };
export type Point = { x: number; y: number };
export type Bounds = { x1: number; y1: number; x2: number; y2: number };

export const MIN_ZOOM = 0.12;
export const MAX_ZOOM = 3.2;
// The closest a fit goes, so a small graph is not blown up.
const FIT_MAX_ZOOM = 1.35;
// How quickly an eased zoom settles, and a let-go pan slows (ms: about two-thirds of the way in this time).
const ZOOM_EASE_MS = 110;
const DRIFT_EASE_MS = 260;
// How long a flight to a company or a fit takes.
export const FLY_MS = 720;

export const clampZoom = (k: number) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, k));

export function toScreen(camera: Camera, point: Point): Point {
  return { x: point.x * camera.k + camera.x, y: point.y * camera.k + camera.y };
}

export function toWorld(camera: Camera, point: Point): Point {
  return { x: (point.x - camera.x) / camera.k, y: (point.y - camera.y) / camera.k };
}

/** Room kept clear on each side of the screen, for the controls floating there. */
export type Insets = { top: number; right: number; bottom: number; left: number };

/**
 * The camera that shows the whole box, centred in what the screen leaves clear: padding on every side, or room of its
 * own on each (so the controls along the top and bottom cover nothing).
 */
export function fitCamera(bounds: Bounds, width: number, height: number, padding: number | Insets = 64): Camera {
  const room = typeof padding === 'number' ? { top: padding, right: padding, bottom: padding, left: padding } : padding;
  const boxWidth = Math.max(1, bounds.x2 - bounds.x1);
  const boxHeight = Math.max(1, bounds.y2 - bounds.y1);
  const clearWidth = Math.max(1, width - room.left - room.right);
  const clearHeight = Math.max(1, height - room.top - room.bottom);
  const k = clampZoom(Math.min(FIT_MAX_ZOOM, clearWidth / boxWidth, clearHeight / boxHeight));
  const middle = { x: (bounds.x1 + bounds.x2) / 2, y: (bounds.y1 + bounds.y2) / 2 };
  return { k, x: room.left + clearWidth / 2 - middle.x * k, y: room.top + clearHeight / 2 - middle.y * k };
}

/**
 * The camera with a world point at the middle of the screen, at zoom k; with insets, at the middle of what they leave
 * clear (the part of the graph not under the page's panels).
 */
export function centredOn(point: Point, k: number, width: number, height: number, insets?: Insets): Camera {
  const room = insets ?? { top: 0, right: 0, bottom: 0, left: 0 };
  const middleX = room.left + (width - room.left - room.right) / 2;
  const middleY = room.top + (height - room.top - room.bottom) / 2;
  return { k, x: middleX - point.x * k, y: middleY - point.y * k };
}

/** Smooth step for flights: slow start, quick middle, gentle landing. */
export function easeInOut(t: number): number {
  const clamped = Math.min(1, Math.max(0, t));
  return clamped < 0.5 ? 4 * clamped ** 3 : 1 - (-2 * clamped + 2) ** 3 / 2;
}

/**
 * Between two cameras, part of the way (0 to 1). It travels through the middle of the screen and zooms by ratio, not
 * by difference, so a flight from far out to close in feels even all the way.
 */
export function between(from: Camera, to: Camera, t: number, width: number, height: number): Camera {
  const centreFrom = toWorld(from, { x: width / 2, y: height / 2 });
  const centreTo = toWorld(to, { x: width / 2, y: height / 2 });
  const k = Math.exp(Math.log(from.k) + (Math.log(to.k) - Math.log(from.k)) * t);
  const centre = {
    x: centreFrom.x + (centreTo.x - centreFrom.x) * t,
    y: centreFrom.y + (centreTo.y - centreFrom.y) * t,
  };
  return centredOn(centre, k, width, height);
}

/**
 * Moves the camera over time: the graph tells it about wheel zooms, pans, flights and fits, and calls step once a frame
 * for where the camera now is. With reduced motion, every move lands at once.
 */
export class CameraRig {
  camera: Camera = { x: 0, y: 0, k: 1 };
  // A wheel zoom gliding in: the zoom it is heading for, and the point that must stay under the pointer.
  private zoomTo: { k: number; screen: Point; world: Point } | null = null;
  private flight: { from: Camera; to: Camera; startedAt: number | null; ms: number } | null = null;
  private drift: Point = { x: 0, y: 0 };
  width = 1;
  height = 1;

  constructor(private readonly instant = false) {}

  /** Whether the camera is still moving by itself. */
  get moving(): boolean {
    return this.zoomTo !== null || this.flight !== null || Math.hypot(this.drift.x, this.drift.y) > 0.01;
  }

  setSize(width: number, height: number) {
    this.width = Math.max(1, width);
    this.height = Math.max(1, height);
  }

  /** Jumps, stopping whatever was moving. */
  jump(camera: Camera) {
    this.camera = { ...camera };
    this.stop();
  }

  stop() {
    this.zoomTo = null;
    this.flight = null;
    this.drift = { x: 0, y: 0 };
  }

  /** Zooms by a factor around a point on screen (where the pointer is), gliding in. */
  zoomAt(factor: number, screen: Point) {
    this.flight = null;
    const base = this.zoomTo?.k ?? this.camera.k;
    const k = clampZoom(base * factor);
    this.zoomTo = { k, screen, world: toWorld(this.camera, screen) };
    if (this.instant) this.step(0, 0);
  }

  /** Moves the view by a distance on screen, as the pointer drags the background. */
  panBy(dx: number, dy: number) {
    this.flight = null;
    this.zoomTo = null;
    this.camera = { ...this.camera, x: this.camera.x + dx, y: this.camera.y + dy };
  }

  /** Lets a pan go at a speed (pixels a millisecond): the view keeps drifting and slows to a stop. */
  release(velocity: Point) {
    if (this.instant) return;
    this.drift = { x: velocity.x, y: velocity.y };
  }

  /** Flies to another camera. */
  /** Whether a flight is under way (a wheel zoom or drift is not one). */
  get flying(): boolean {
    return this.flight !== null;
  }

  /** Flies to the camera, in FLY_MS unless told otherwise (a short glide, say). */
  flyTo(camera: Camera, ms = FLY_MS) {
    this.zoomTo = null;
    this.drift = { x: 0, y: 0 };
    if (this.instant) {
      this.camera = { ...camera };
      this.flight = null;
      return;
    }
    this.flight = { from: { ...this.camera }, to: camera, startedAt: null, ms };
  }

  /** One frame: dt is the milliseconds since the last, now the frame's time. Returns the camera. */
  step(dt: number, now: number): Camera {
    if (this.flight) {
      this.flight.startedAt ??= now;
      const t = (now - this.flight.startedAt) / this.flight.ms;
      this.camera = between(this.flight.from, this.flight.to, easeInOut(t), this.width, this.height);
      if (t >= 1 || this.instant) {
        this.camera = { ...this.flight.to };
        this.flight = null;
      }
    }
    if (this.zoomTo) {
      const share = this.instant ? 1 : 1 - Math.exp(-dt / ZOOM_EASE_MS);
      const k = Math.exp(Math.log(this.camera.k) + (Math.log(this.zoomTo.k) - Math.log(this.camera.k)) * share);
      const { screen, world } = this.zoomTo;
      this.camera = { k, x: screen.x - world.x * k, y: screen.y - world.y * k };
      if (Math.abs(Math.log(k / this.zoomTo.k)) < 0.001) {
        this.camera = {
          k: this.zoomTo.k,
          x: screen.x - world.x * this.zoomTo.k,
          y: screen.y - world.y * this.zoomTo.k,
        };
        this.zoomTo = null;
      }
    }
    if (this.drift.x || this.drift.y) {
      this.camera = { ...this.camera, x: this.camera.x + this.drift.x * dt, y: this.camera.y + this.drift.y * dt };
      const keep = Math.exp(-dt / DRIFT_EASE_MS);
      this.drift = { x: this.drift.x * keep, y: this.drift.y * keep };
      if (Math.hypot(this.drift.x, this.drift.y) < 0.005) this.drift = { x: 0, y: 0 };
    }
    return this.camera;
  }
}
