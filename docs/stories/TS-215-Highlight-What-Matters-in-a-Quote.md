# TS-215 Highlight What Matters in a Quote

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 29 October 2026) |
| Epic | [TS-5 Trust and Review](TS-5-Trust-and-Review.md) |
| Sprint | Sprint 4 |
| Points | 5 |
| Labels | s4-new, sprint-4 |

## User Story
As an analyst or portfolio manager reading a relationship's quote, I want the words that matter picked out (the companies, what is supplied, how much one relies on the other, and the figures), so that I can check a claim at a glance instead of reading every quote in full.

## What We Agreed
- **Asked for by the product owner on 27 October 2026,** while reviewing the One Workspace design. The design's first highlights were found by hand-written rules (a fixed list of reliance words, a pattern for figures). Those were removed: nothing about what to highlight is written into the code. The AI reads each quote and says what matters.
- **The AI never invents a highlight.** Every phrase it gives must appear in the quote word for word (capitals and spacing aside), or it is dropped, the same idea as the quote check that turns the AI's quote into the report's own evidence. Only the report's own words are ever marked; the AI's phrases are never shown.
- **Four kinds, nothing else:** Company (a company the quote names, under any name it uses, such as "TSMC"), Supplied (what is supplied), Reliance (how much one company relies on the other, such as "sole source" or "substantially all"), and Figure (an amount, a share or a date).
- **One small call per batch of quotes, after the evidence exists,** not inside the call that finds relationships. That way every relationship gets highlights the same way: those found from now on, the ones already analysed, and ones an analyst added (Fix or Add a Relationship). An analyst can change only what is supplied, never the evidence, so a quote's highlights never go out of date. The call sends the quotes only, never the report, so it is cheap; a company already analysed is still never sent to the AI again for relationships.
- **Highlights belong to the relationship,** so they are shared by every portfolio, like the relationship itself.
- **Missing highlights never block anything.** A quote with none (not read yet, or the AI's answer unreadable) is shown plain, exactly as today, and is read again on the next pass.
- **Where they show:** the quote in the Inspector's relationship view (One Workspace) and in the Analyst Desk's Relationships tab, each with a one-line key of the four kinds, shown only when the quote has a highlight.

## Acceptance Criteria
1. After analysis saves a holding's relationships, each one's quote is read for highlights, and the Inspector shows them, each kind in its own style, with the key below the quote.
1. A phrase the AI gives that is not in the quote word for word is not stored and not shown.
1. Relationships analysed before this story get highlights when an analyst starts the highlights pass.
1. A relationship an analyst adds is read for highlights like any other.
1. When the AI's answer cannot be read, the call is recorded as unreadable, the quotes stay plain, and the next pass reads them again.
1. The Analyst Desk shows the same highlights on every claim in the review queue.

## Technical Notes
- **Tables:** `relationship_highlights` and `relationships.highlighted_at`, both in `V4__sprint_4_tables.sql` (Set Up Sprint 4), with the `HIGHLIGHT` purpose. This story adds no migration.
- **`HighlightFinder`** (in `relationship`): takes up to 25 relationships whose `highlighted_at` is null, and asks Gemini once through `GeminiClient.generate(AiRequest)` with `AiPurpose.HIGHLIGHT`. The prompt numbers each quote and asks, for each, for a JSON list of `\{ index, highlights: [\{ text, kind \}] \}`, `text` copied exactly from that quote, at most 8 per quote, kinds as above. Use a response schema, as the relationship prompt does. The recorded prompt replaces each quote with `[quote of relationship <id>]`, so no report text goes into `ai_calls`.
- **Checking a phrase:** find it in the evidence as a run of whole words in order, ignoring capitals, spacing and the punctuation `QuoteChecker` ignores, with every word present (no 85% allowance: a highlight is exact). Store the character range it covers in the evidence (`start_at` inclusive, `end_at` exclusive, as Java `String` indexes, which are the same as JavaScript's). Where two found phrases overlap, keep the longer. Then set `highlighted_at`, even when nothing was found, so the quote is not read again.
- **Unreadable answers:** `AiCallLog.unreadable(callId)`; leave `highlighted_at` null for that batch.
- **When it runs:** `HighlightJob.run()` (`@Async`, like `CriticalityJob`) works through every relationship with a null `highlighted_at`, batch by batch. Start it after analysis saves relationships, after Fix or Add a Relationship saves one, and from `POST /api/relationships/highlights` (analysts only, 202, `@Operation(summary = "Highlight what matters in every quote not yet read")`). That address is started from the Swagger page, as the criticality pass (`POST /api/reports/criticality`) and the second check (`POST /api/relationships/second-check`) are today; no button is added.
- **API:** `RelationshipResponse` and the review queue's item gain `highlights`: a list of `\{ kind, start, end \}`, ordered by `start`, empty when there are none.
- **Website:** one `HighlightedQuote` component (in `components/`) renders the evidence as text with each range in a `<mark>` of its kind, and the key under it. Company in the company's chain colour (teal when it has none), Supplied in lime, Reliance in amber, Figure in white with a dotted underline, as in the One Workspace design. It never searches the text itself: the ranges come from the API.
- **Tests:** `HighlightFinderTest` with a saved, real Gemini answer for a few saved quotes in `docs/examples/` (say where it came from), including a phrase that is not in its quote (criterion 2) and an unreadable answer (5); an integration test (`HighlightsIT`) for the API, the analyst-only pass (3) and an analyst-added relationship (4); `HighlightedQuote.test.tsx` for the marks, the key, and a quote with no highlights.
