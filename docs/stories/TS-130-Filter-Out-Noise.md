# TS-130 Filter Out Noise

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-6 News and Alerts](TS-6-News-and-Alerts.md) |
| Sprint | Sprint 2 |
| Points | 5 |
| Labels | sprint-2 |
| Assignee | Lim Zhe An |

**Owner (suggested):** Zhe An · **Needs First:** Get News

## User Story
As a portfolio manager, I want TrueSight to tell real supply-chain events apart from ordinary news, so that I am only warned about things that could disrupt a company.

## What We Agreed
- This is our **advanced AI capability: signal versus noise**. Most news about a large company (earnings, share price, product launches, opinion) is not a supply-chain event, and keyword rules cannot tell the difference.
- Gemini judges each article **once**; the judgement is shared, like relationships.
- Each judgement says: **event or noise**; for an event, its **type** and **severity** (Low, Medium, High); the AI's **confidence** (Low, Medium, High); a one-sentence **summary**; and a **reason**. Noise also has a reason ("Quarterly earnings, not a disruption").
- **Guardrails:** an answer with an unknown event type, severity or confidence, or with no reason, is stored as noise with the reason "The AI's answer could not be used."; a headline missing from the answer stays unassessed and is tried again next time.
- The AI only sees the headline; the website says "Based on the headline only".
- **Evaluation:** the team labels the saved real headlines by hand **before** looking at Gemini's answer, and the README records how many Gemini got right.

## Acceptance Criteria
1. Each new article gets exactly one assessment; checking the news again does not ask Gemini about it again.
1. Using the saved real Gemini answer, every headline in the sample gets the event or noise judgement in that answer, with its type, severity, confidence, summary and reason.
1. An answer naming an unknown event type is stored as noise with "The AI's answer could not be used."
1. A headline missing from Gemini's answer stays unassessed, and is assessed by the next news check.
1. If Gemini fails, the articles stay unassessed, the portfolio shows "The AI could not assess the news. Please try again later.", and the next Check News retries them.
1. `docs/examples/news-labels.csv` holds the team's hand labels for the sample headlines, and the README states Gemini's accuracy against them.
1. Tests never call Gemini.

## Technical Notes
- **`NewsAssessor implements NewsCheckStep`**, `@Order(1)`, package `news`: finds this portfolio's companies' articles with no assessment, sends them in **batches of up to 20**, and saves one `NewsAssessment` per answered article.
- **Gemini call:** `geminiClient.generate(prompt, NEWS_SCHEMA)` (from the setup), temperature 0; if the answer cannot be read, ask once more (as `RelationshipFinder` does); then throw `GeminiException`, which the news job records on the portfolio as `FAILED` with the message in criterion 5.
- **Prompt** (the headlines are numbered from 1, each with the company it was found for):

```
You are screening news headlines for supply-chain risk. For each numbered headline, decide whether it reports an event
that could disrupt the named company's ability to make, buy, ship or sell goods.

Answer for every headline:
- index: the headline's number
- isEvent: true only for a real disruption; false for earnings, share prices, analyst opinions, product launches,
  marketing, or general commentary
- eventType: one of PRODUCTION_STOP, SUPPLY_SHORTAGE, LABOUR_ACTION, NATURAL_DISASTER, TRADE_RESTRICTION,
  FINANCIAL_DISTRESS, LEGAL_OR_REGULATORY, CYBER_ATTACK, OTHER_DISRUPTION; empty when isEvent is false
- severity: LOW, MEDIUM or HIGH; empty when isEvent is false
- confidence: LOW, MEDIUM or HIGH, how sure you are from the headline alone
- summary: one sentence saying what happened; empty when isEvent is false
- reason: one sentence explaining your decision

Use only the headline. Do not guess facts it does not state. Answer with JSON only.

HEADLINES:
1. [{company name}] {headline}
2. [{company name}] {headline}
```

- **Response schema** (`NEWS_SCHEMA`): an object with `assessments`, an array of objects with `index` (INTEGER), `isEvent` (BOOLEAN), `eventType`, `severity`, `confidence`, `summary`, `reason` (STRING). Match answers by `index`.
- **`NewsAssessmentReader`** (plain class, unit-tested): turns Gemini's text into validated assessments, applying the guardrails above.
- **Evaluation files:** `docs/examples/news-labels.csv` with columns `index,title,label,labelled_by` (`label` is `event` or `noise`), filled by two teammates who agree each label; the README states "Gemini agreed with N of 20".
- **Tests:** `NewsAssessmentReaderTest` with `Examples.geminiNewsAssessment()` and hand-made broken answers (criteria 3, 4); `NewsAssessorIT` with `@MockitoBean GeminiClient` returning the saved answer (criteria 1, 2, 5).

**Out of scope:** reading article text; an Analyst overruling an assessment (Sprint 3).
