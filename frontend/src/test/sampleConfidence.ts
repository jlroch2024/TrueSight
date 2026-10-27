// The confidence the backend gives an unreviewed relationship with no recorded quote match and no second check yet, as
// every relationship in docs/examples/relationships.json has (RelationshipConfidenceTest checks that sample).
import type { Confidence } from '../api/relationships';

export const UNREVIEWED_CONFIDENCE: Confidence = {
  level: 'MEDIUM',
  reasons: [
    { text: 'Not Yet Reviewed', effect: 'NONE' },
    { text: 'Quote Match Not Recorded', effect: 'NONE' },
    { text: 'Not Yet Second-Checked', effect: 'NONE' },
  ],
};
