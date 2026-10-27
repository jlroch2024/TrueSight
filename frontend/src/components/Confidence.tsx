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
    <span className={`chip confidence-tag confidence-${confidence.level.toLowerCase()}`}>
      {LEVEL_LABELS[confidence.level]}
    </span>
  );
}

/**
 * A reason as the portfolio manager reads it: its name alone. The backend writes a second check's doubt as "Second
 * Check Doubts: " and the AI's explanation, which is for the analyst deciding the relationship and is shown on the
 * Analyst Desk's review card.
 */
function headOf(text: string): string {
  const colon = text.indexOf(': ');
  return colon < 0 ? text : text.slice(0, colon);
}

export function ConfidenceReasons({ confidence }: { confidence: Confidence }) {
  return (
    <ul className="confidence-reasons" aria-label="Why">
      {confidence.reasons.map((reason) => (
        <li key={reason.text} className={`effect-${reason.effect.toLowerCase()}`}>
          <span>{headOf(reason.text)}</span>
          <span className="confidence-effect">{EFFECT_LABELS[reason.effect]}</span>
        </li>
      ))}
    </ul>
  );
}
