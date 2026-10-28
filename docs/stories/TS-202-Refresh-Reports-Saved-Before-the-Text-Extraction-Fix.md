# TS-202 Refresh Reports Saved Before the Text Extraction Fix

| | |
|---|---|
| Type | Bug |
| Status | Done (in Jira on 29 October 2026) |
| Sprint | Sprint 3 |
| Labels | analysis, bug |
| Assignee | Allan Kok Song Yeong |

## What Happens

A holding can be marked Done while its supply-chain relationship is missing from the graph. The affected local Demo Portfolio has ASML marked Done with no saved relationships.

## Why

The ASML report row was saved on 25 September 2026, before TS-88's report text extraction fix. Its saved text splits ASML's real sole-supplier sentence across line breaks:

> The number of lithography systems we are able to produce is limited by the production capacity of one of our key suppliers, Carl Zeiss SMT, our sole supplier of lenses, mirrors, illuminators, collectors and other critical optical components (which we refer to as optics).

The recorded FIND_RELATIONSHIPS call was ANSWERED with kept=0 and dropped=1. Gemini returned a different Carl Zeiss quote that is not in the saved report, so QuoteChecker correctly rejected it. Re-running Gemini against this same stale saved text does not repair the data.

This is a follow-up to TS-88, which fixed extraction but notes that reports saved before the fix retain their broken text and must be cleared once.

## What We Agreed

- Refresh affected stored report text with the current extractor before asking Gemini to find relationships again.
- Do not retry an answered report repeatedly against unchanged text.
- Keep the limited per-report retry for reports with no completed FIND_RELATIONSHIPS answer: an unreadable/failed call or a report predating the audit trail may be retried once; an answered call with kept=0 is complete.

## Acceptance Criteria

1. The affected ASML report is re-extracted from its saved SEC filing using the current extractor, preserving the real supplier sentence as a continuous passage.
1. Re-analysis verifies and saves the Carl Zeiss SMT supplier relationship with evidence from that sentence.
1. Re-running Analyse does not send an already answered report to Gemini again.
1. A Done holding is retried only when its latest report has no saved relationships and no completed answered relationship-finding call.
1. Tests use the real ASML excerpt and the saved NVIDIA Gemini answer; no invented AI response or report quote is added.
