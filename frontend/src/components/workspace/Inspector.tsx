// The Inspector: everything about the one thing chosen, laid out so the eye finds what matters first. A company, a
// relationship or an alert each has its own view (InspectorCompany, InspectorRelationship, InspectorAlert); changing
// what is chosen slides the new view in.
import { AnimatePresence, motion } from 'motion/react';
import { FileText, Link2, Waypoints } from 'lucide-react';
import type { AlertEvent } from '../../api/alerts';
import type { History } from '../../api/history';
import type { Holding } from '../../api/portfolios';
import { AlertView } from './InspectorAlert';
import { CompanyView } from './InspectorCompany';
import { RelationshipView } from './InspectorRelationship';
import type { Chosen, WorkspaceView } from './view';

type Props = {
  chosen: Exclude<Chosen, null>;
  view: WorkspaceView;
  // The alert whose change is being saved.
  busyKey: string | null;
  onChoose: (chosen: Chosen) => void;
  onWeight: (holding: Holding, weight: string) => Promise<string | null>;
  onRemove: (holding: Holding) => void;
  onClose: (alert: AlertEvent) => void;
  onRestore: (alert: AlertEvent) => void;
  // What the SEC Filing Timeline has read, for the years a company was named in.
  history: History | null;
};

export function InspectorBody({ chosen, view, busyKey, history, onChoose, onWeight, onRemove, onClose, onRestore }: Props) {
  const key = chosen.kind === 'company' ? chosen.nodeId : chosen.kind === 'relationship' ? `r${chosen.relationshipId}` : chosen.key;
  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div
        key={key}
        initial={{ opacity: 0, x: 18 }}
        animate={{ opacity: 1, x: 0 }}
        exit={{ opacity: 0, x: -12 }}
        transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
      >
        {chosen.kind === 'company' && (
          <CompanyView view={view} nodeId={chosen.nodeId} history={history} onChoose={onChoose} onWeight={onWeight} onRemove={onRemove} />
        )}
        {chosen.kind === 'relationship' && <RelationshipView view={view} relationshipId={chosen.relationshipId} onChoose={onChoose} />}
        {chosen.kind === 'alert' && (
          <AlertView
            view={view}
            wanted={chosen.key}
            busy={busyKey === chosen.key}
            onChoose={onChoose}
            onClose={onClose}
            onRestore={onRestore}
          />
        )}
      </motion.div>
    </AnimatePresence>
  );
}

const KIND = {
  company: { icon: FileText, label: 'Company' },
  relationship: { icon: Link2, label: 'Relationship' },
  alert: { icon: Waypoints, label: 'Alert' },
} as const;

/** What kind of thing the Inspector shows, for its head and its reopening tab. */
export function InspectorKind({ chosen }: { chosen: Exclude<Chosen, null> }) {
  const { icon: Icon, label } = KIND[chosen.kind];
  return (
    <span className="insp-kind">
      <Icon size={14} />
      {label}
    </span>
  );
}
