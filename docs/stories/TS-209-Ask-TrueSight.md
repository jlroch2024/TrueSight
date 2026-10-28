# TS-209 Ask TrueSight

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 29 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 4 |
| Points | 8 |
| Labels | s4-new, sprint-4 |

## User Story
As a portfolio manager, I want to ask questions about my portfolio in plain language, so that I get answers without searching every page.

## What We Agreed
- **The chat at the bottom centre** of One Workspace: a question box and the answers above it.
- **It answers only from TrueSight's own data for the open portfolio:** its holdings, their relationships with evidence, review status, confidence and criticality, its open alerts and their approved recommendations. Nothing from the web and nothing from Gemini's own knowledge.
- **Every answer cites its sources** as buttons that open them: a relationship in the Inspector, an alert, a holding. An answer that names no source is not shown.
- **It says when it does not know:** "TrueSight's data does not answer that." It prefers approved relationships and says when a fact rests on unreviewed ones.
- **No advice:** the same rule as recommendations (never buy, sell or hold).
- **No saved chats:** the conversation lives in the browser tab (the last 6 questions and answers are sent back for context); each question is recorded in the AI Audit Trail as purpose `ASK`.

## Acceptance Criteria
1. A question about the portfolio is answered with at least one source, and each source opens its item.
1. A question the data cannot answer gets "TrueSight's data does not answer that."
1. A source the answer names that is not in the portfolio's data is dropped; an answer left with no source is replaced by the sentence above.
1. An answer resting on unreviewed relationships says so.
1. A question asking what to buy or sell is answered with the no-advice sentence, never a recommendation to trade.
1. Another user's portfolio answers 404; each question appears in the AI Audit Trail.

## Technical Notes
- **Endpoint:** `POST /api/portfolios/\{id\}/questions` with `\{ "question": "…", "history": [\{ "question", "answer" \}] \}` (200) returning `\{ "answer": "…", "sources": [\{ "kind": "RELATIONSHIP" | "ALERT" | "HOLDING", "id": n, "label": "…" \}] \}`. `@Tag(name = "Ask TrueSight")`. Questions up to 500 characters; history up to 6 turns.
- **Prompt:** a constant in `ask/AskService.java`: the rules above, then the portfolio's data as numbered records (`[R12] TSMC supplies Nvidia: wafer production. Evidence: "…". Approved. High confidence.`), then the question. The response schema is `\{ answer: string, sourceIds: string[] \}`; the service keeps only ids that exist in the records it sent.
- **Website:** the chat in the workspace's bottom-middle slot (a `glass ticks` panel, kept clear of the drawer and the Inspector as the graph's `insets` are); answers in sentence case; sources as small `btn quiet` buttons. A source opens through the workspace's `Chosen` (`components/workspace/view.ts`): a relationship as `\{ kind: 'relationship' \}`, a holding as `\{ kind: 'company' \}` on its node, and an alert as the `\{ kind: 'alert' \}` event whose key holds that alert's id.
- **Test data:** run the prompt by hand on a real portfolio's records, with three real questions (one answerable, one not, one asking for advice), and save Gemini's real answers in `docs/examples/` with the README entry.
- **Tests:** `AskIT` with a fake `GeminiClient` serving the saved answers (sources kept and dropped, the unknown answer, the advice answer, 404, the AI call recorded); website tests for the chat and the source buttons.
