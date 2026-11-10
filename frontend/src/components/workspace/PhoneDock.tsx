// The workspace below 760px wide: the tab bar along the bottom, choosing the one sheet shown over the graph. The open tab
// sits on a sliding plate; pressed again, it goes back to the graph. Which sheet is open is the page's state.
import { AnimatePresence, motion } from 'motion/react';
import type { ReactNode, SVGProps } from 'react';
import type { WorkspaceTab } from '../../paths';
import { useSlidingPlate } from '../useSlidingPlate';

export type PhoneTab = WorkspaceTab | 'graph' | 'details';

/**
 * The order of the tabs, as on a computer: the drawer's two on the left, the graph in the middle, the Inspector on the
 * right. A sheet slides in from its tab's side of the one before it.
 */
export const PHONE_TABS: PhoneTab[] = ['holdings', 'alerts', 'graph', 'details'];

export function PhoneDock({
  open,
  openAlerts,
  hasDetails,
  onPress,
}: {
  open: PhoneTab;
  openAlerts: number;
  // Whether anything has been chosen yet, for Details to show.
  hasDetails: boolean;
  onPress: (tab: PhoneTab) => void;
}) {
  const plate = useSlidingPlate<HTMLElement>(open);
  const tabs: { id: PhoneTab; label: string; icon: ReactNode; badge?: number; disabled?: boolean }[] = [
    { id: 'holdings', label: 'Holdings', icon: <HoldingsIcon /> },
    { id: 'alerts', label: 'Alerts', icon: <AlertsIcon />, badge: openAlerts },
    { id: 'graph', label: 'Graph', icon: <GraphIcon /> },
    { id: 'details', label: 'Details', icon: <DetailsIcon />, disabled: !hasDetails },
  ];
  return (
    <nav ref={plate.container} className="dock glass ticks" role="tablist" aria-label="Workspace">
      <span ref={plate.plate} className="dock-plate" aria-hidden="true" />
      {tabs.map((item) => (
        <button
          key={item.id}
          type="button"
          role="tab"
          aria-label={item.badge ? `${item.label} (${item.badge} Open)` : item.label}
          aria-selected={open === item.id}
          data-chosen={open === item.id}
          disabled={item.disabled}
          onClick={() => onPress(item.id)}
        >
          <span className="dock-icon">{item.icon}</span>
          <span className="dock-label">{item.label}</span>
          <AnimatePresence>
            {item.badge ? (
              <motion.span className="badge" initial={{ scale: 0 }} animate={{ scale: 1 }} exit={{ scale: 0 }}>
                {item.badge}
              </motion.span>
            ) : null}
          </AnimatePresence>
        </button>
      ))}
    </nav>
  );
}

// The tabs' icons, drawn as the rest of the look is: squares and hairlines, square caps and mitred corners, a chamfer only
// on what stands for a company. The part marked "lit" fills with light while its sheet is open.
function Icon({ children, ...rest }: SVGProps<SVGSVGElement>) {
  return (
    <svg
      width={19}
      height={19}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="square"
      strokeLinejoin="miter"
      aria-hidden="true"
      {...rest}
    >
      {children}
    </svg>
  );
}

/** A list of companies, each a small square, with a bar as long as its weight. */
function HoldingsIcon() {
  return (
    <Icon>
      <rect className="lit" x="3.5" y="4.5" width="4" height="4" />
      <rect x="3.5" y="4.5" width="4" height="4" />
      <rect x="3.5" y="10.5" width="4" height="4" />
      <rect x="3.5" y="16.5" width="4" height="4" />
      <path d="M11 6.5h9.5M11 12.5h7M11 18.5h4.5" />
    </Icon>
  );
}

/** A holding (chamfered) and the two companies it is linked to. */
function GraphIcon() {
  return (
    <Icon>
      <path className="lit" d="M3 13h4.2l1.3 1.3V19H3z" />
      <path d="M3 13h4.2l1.3 1.3V19H3z" />
      <rect x="15.5" y="3.5" width="5.5" height="5.5" />
      <rect x="15.5" y="15.5" width="5.5" height="5.5" />
      <path d="M8.5 14.5l7-7.5M8.5 17.5h7" />
    </Icon>
  );
}

/** A beacon, sending its signal out to the four corners. */
function AlertsIcon() {
  return (
    <Icon>
      <path className="lit" d="M8 8h6.5L16 9.5V16H8z" />
      <path d="M8 8h6.5L16 9.5V16H8z" />
      <path d="M12 10.5v2.6M12 14.6v.1" />
      <path d="M3 6.5V3h3.5M17.5 3H21v3.5M21 17.5V21h-3.5M6.5 21H3v-3.5" />
    </Icon>
  );
}

/** A panel, its subject on the left and what is known about it on the right. */
function DetailsIcon() {
  return (
    <Icon>
      <rect x="3" y="4" width="18" height="16" />
      <path d="M10 4v16" />
      <rect className="lit" x="5" y="7" width="3" height="3" />
      <path d="M13 8.5h5M13 12h5M13 15.5h3" />
    </Icon>
  );
}
