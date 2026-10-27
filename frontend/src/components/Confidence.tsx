// How confident TrueSight is about a relationship, as the backend works it out: a tag ("High Confidence"), and the
// reasons behind it, each with its effect. The website only shows it; it never works a confidence out itself.
import type { Confidence, ConfidenceEffect, ConfidenceLevel } from '../api/relationships';

const LEVEL_LABELS: Record<ConfidenceLevel, string> = {
  HIGH: 'High Confidence',
  MEDIUM: 'Medium Confidence',
  LOW: 'Low Confidence',
};

const EFFECT_LABELS: Record<ConfidenceEffect, string> = {
  RAISES: 'Raises Confidence',
  LOWERS: 'Lowers Confidence',
  NONE: 'No Effect',
};

export function ConfidenceTag({ confidence }: { confidence: Confidence }) {
  return (
    <span className={`news-tag confidence-tag confidence-${confidence.level.toLowerCase()}`}>
      {LEVEL_LABELS[confidence.level]}
    </span>
  );
}

export function ConfidenceReasons({ confidence }: { confidence: Confidence }) {
  return (
    <ul className="confidence-reasons" aria-label="Why">
      {confidence.reasons.map((reason) => (
        <li key={reason.text} className={`effect-${reason.effect.toLowerCase()}`}>
          <span>{reason.text}</span>
          <span className="confidence-effect">{EFFECT_LABELS[reason.effect]}</span>
        </li>
      ))}
    </ul>
  );
}
