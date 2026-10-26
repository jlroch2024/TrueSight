# Sample Data

Real data to build and test against, so every story can start on day 1 and tests never need the internet.

| File | What it is | Used by |
|---|---|---|
| `holdings.csv` | The sample portfolio: 10 companies, each weighted 10% | Upload a Portfolio CSV, and the demo |
| `relationships.json` | 17 relationships, in exactly the shape `GET /api/portfolios/{id}/relationships` returns. Every one was found by AI and is `UNREVIEWED`: `source` is `"AI"`, `reviewStatus` is `"UNREVIEWED"`, and `reviewedAt` and `reviewNote` are `null`. Each company's `displayName` is what the backend's `CompanyDisplayNames` makes of its `name` with no report to read, which `CompanyDisplayNamesTest` checks. Each relationship's `confidence` is what `RelationshipConfidence` gives an unreviewed relationship with no recorded quote match and no second check (Medium), which `RelationshipConfidenceTest` checks | See the Supply Chain as a Graph, until the real endpoint exists; Review AI Findings; Spell Each Company as Its Own Report Does; See How Confident TrueSight Is |
| `nvidia-10k.txt` | The plain text of NVIDIA's latest 10-K, one paragraph per line | Find Suppliers and Customers with AI, in tests |
| `gemini-nvidia.json` | One real, unchanged Gemini answer for that 10-K: 7 suppliers, each with a quote | Find Suppliers and Customers with AI, in tests, so tests never call Gemini |
| `gemini-nvidia-criticality.json` | One real, unchanged Gemini answer to the criticality-enabled prompt for that 10-K | Find Suppliers and Customers with AI, in tests, so tests never call Gemini |
| `google-news-tsmc.xml` | One unchanged Google News RSS response for the TSMC search URL below | Sprint 2 news checks and tests |
| `gemini-news-assessment.json` | Gemini's full, unchanged API response assessing the first 20 headlines in `google-news-tsmc.xml` | Filter Out Noise tests, so tests never call Gemini |
| `news-labels.csv` | The team's own event-or-noise label for each of those 20 headlines, made by hand before looking at Gemini's answer | Filter Out Noise, to measure how often Gemini is right |

## Where It Came From

`google-news-tsmc.xml` is the unmodified response retrieved from the Google News RSS URL below on 14 October 2026.

```text
https://news.google.com/rss/search?q=%22Taiwan%20Semiconductor%20Manufacturing%22+when:7d&hl=en-US&gl=US&ceid=US:en
```

## The Gemini News Assessment

`gemini-news-assessment.json` is the complete, unchanged Gemini API response saved on 14 October 2026. It contains
Gemini's assessment of headlines 1–20 from `google-news-tsmc.xml`, sent with the company name
`Taiwan Semiconductor Manufacturing` and temperature 0.

- **Model:** `gemini-3.8-flash`
- **Usage:** 887 input tokens, 1,319 output tokens
- **Result:** 20 indexed assessments; the API finished normally
- **Response schema:** one object per headline, with `index`, `isEvent`, `eventType`, `severity`, `confidence`, `summary`, and `reason`

The prompt, word for word:

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
1. [Taiwan Semiconductor Manufacturing] Taiwan Semiconductor Manufacturing Just Dropped a Major Bombshell Investors Must Be Aware Of - Yahoo Finance
2. [Taiwan Semiconductor Manufacturing] Should You Buy Taiwan Semiconductor Manufacturing Stock Before Oct. 15? History Is Crystal Clear on Where the Stock Is Headed Next. - The Motley Fool
3. [Taiwan Semiconductor Manufacturing] TSMC expected to report sixth straight quarter of record profit thanks to AI boom - Reuters
4. [Taiwan Semiconductor Manufacturing] Can Taiwan Semiconductor Manufacturing (NYSE:TSM) Justify A Premium On Cash Flow? - Simply Wall Street
5. [Taiwan Semiconductor Manufacturing] FILE PHOTO: The logo of Taiwan Semiconductor Manufacturing Company (TSMC) at the company's second-quarter earnings conference in Taipei - The Daily News | Texas' Oldest Newspaper
6. [Taiwan Semiconductor Manufacturing] Why October 15 Could Change Everything for Taiwan Semiconductor Stock - 24/7 Wall St.
7. [Taiwan Semiconductor Manufacturing] Can Taiwan Semiconductor Manufacturing (NYSE:TSM) Justify Its Price On Cash Flow? - Yahoo Finance
8. [Taiwan Semiconductor Manufacturing] Here's What a $1,400 Investment in Taiwan Semiconductor Manufacturing (TSMC) Stock Could Be Worth in 3 Years - The Motley Fool
9. [Taiwan Semiconductor Manufacturing] Taiwan Semiconductor Manufacturing (TSM) Nears Record Profit, Is The Stock Above Fair Value? - Simply Wall Street
10. [Taiwan Semiconductor Manufacturing] GlobalFoundries to make key AI chip component for TSMC in $2 billion deal - Reuters
11. [Taiwan Semiconductor Manufacturing] GlobalFoundries Signs $2 Billion Silicon Supply Deal With Taiwan Semiconductor Manufacturing - Yahoo Finance
12. [Taiwan Semiconductor Manufacturing] ASML vs. Taiwan Semiconductor Manufacturing: Which Technology Stock Is a Better Buy in 2026? - The Motley Fool
13. [Taiwan Semiconductor Manufacturing] Here's What a $1,400 Investment in Taiwan Semiconductor Manufacturing (TSMC) Stock Could Be Worth in 3 Years - Yahoo Finance
14. [Taiwan Semiconductor Manufacturing] SK Hynix vs. Taiwan Semiconductor Manufacturing: Which Computer Chip Stock Is a Better Buy in 2026? - The Motley Fool
15. [Taiwan Semiconductor Manufacturing] Taiwan Semiconductor Manufacturing (TSM) Posted Record Q3 2026 Revenue - Yahoo Finance Singapore
16. [Taiwan Semiconductor Manufacturing] Qualcomm vs. Taiwan Semiconductor Manufacturing: Which Technology Stock Is a Better Buy in 2026? - The Motley Fool
17. [Taiwan Semiconductor Manufacturing] Why Taiwan Semiconductor (TSM) Became Essential to the Chip Industry - Yahoo Finance
18. [Taiwan Semiconductor Manufacturing] Prediction: $5,000 Invested in Taiwan Semiconductor Could Be Worth This Much by 2030 - Yahoo Finance
19. [Taiwan Semiconductor Manufacturing] Taiwan Semiconductor Vs. ASML: If I Had to Choose One For the Next 5 Years, It’s This One - Yahoo Finance
20. [Taiwan Semiconductor Manufacturing] Taiwan Semiconductor Manufacturing Could Have Big News on Oct. 15 - The Globe and Mail
```

## How Often Gemini Is Right

`news-labels.csv` holds the team's hand labels for the same 20 headlines: `index` and `title` as sent to Gemini,
`label` (`event` or `noise`) and `labelled_by`. Two teammates, Zhe An and Ben, labelled every headline by hand
before looking at Gemini's answer, and agree on each label. They judged all 20 to be noise.

**Gemini agreed with 20 of 20.** It also judged every headline to be noise.

This shows Gemini does not raise false alarms on ordinary news, such as earnings, share prices, stock advice and
supply deals. It does not yet show that Gemini catches real disruptions, because none of these 20 headlines reports
one. A sample that includes real events is needed to measure that.

Every `evidence` sentence in `relationships.json` was copied from the company's real annual report, and checked word for
word against it. The reports, all downloaded from the SEC on 25 September 2026:

| Company | Report | Filed |
|---|---|---|
| NVIDIA | 10-K | 2026-02-25 |
| AMD | 10-K | 2026-02-04 |
| Qualcomm | 10-K | 2025-11-05 |
| Tesla | 10-K | 2026-01-29 |
| Broadcom | 10-K | 2025-12-18 |
| Lam Research | 10-K | 2026-08-07 |
| Cirrus Logic | 10-K | 2026-05-21 |
| ASML | 20-F | 2026-02-25 |
| STMicroelectronics | 20-F | 2026-02-26 |
| Sony | 20-F | 2026-06-18 |

The `provides` field is a short label written from the evidence, and is left empty where the report does not say what
is supplied. Company ids are made up for the sample, but consistent: TSMC has the same id wherever it appears, which is
what the graph should show once names are matched.

Sony has no relationships here on purpose. Its report names TSMC only as a planned partner under a non-binding
agreement, not as a current supplier or customer.

## The Gemini Answer

`gemini-nvidia.json` is Gemini's full response, saved exactly as it arrived, on 25 September 2026.

- **Model:** `gemini-3.8-flash`, at temperature 0.
- **Sent:** NVIDIA's 10-K from "Item 1. Business" up to "Item 2. Properties" (167,171 characters), taken from
  `nvidia-10k.txt`. That is the section Find Suppliers and Customers with AI sends.
- **Asked for:** JSON only, a list of `{ company, alsoKnownAs, relationship, provides, quote }`, with `relationship`
  either `SUPPLIER` or `CUSTOMER`. The answer's list is the text inside `candidates[0].content.parts[0].text`.
- **Found:** TSMC, Samsung, SK hynix, Micron, Hon Hai, Wistron and Fabrinet, all as suppliers. All 7 quotes appear word
  for word in the report.
- **Used:** 30,566 tokens in, 481 out.

The prompt, word for word:

```
Below is part of NVIDIA Corporation's annual report (10-K). List every company this text names as a supplier to NVIDIA
or as a customer of NVIDIA.

For each one give:
- company: the company's name, as written in the text
- alsoKnownAs: a short name the text gives for it, such as an abbreviation, or an empty string
- relationship: SUPPLIER if it supplies NVIDIA, CUSTOMER if it buys from NVIDIA
- provides: what is supplied, in a few words
- quote: one sentence copied exactly from the text that shows the relationship

Only include companies the text names explicitly. Answer with JSON only.

TEXT:
<the section of the report>
```

The request also set `responseMimeType` to `application/json` with a matching `responseSchema`, which makes Gemini
answer in exactly that shape. Find Suppliers and Customers with AI can use the same prompt, or its own. If it changes
the prompt, the saved answer still works for testing how answers are read and checked.

## The NVIDIA Criticality Answer

`gemini-nvidia-criticality.json` is the complete, unchanged Gemini API response saved on 23 October 2026 using the
criticality-enabled relationship prompt and `gemini-3.8-flash`, at temperature 0. The report names no supplier as a
sole or single source, so all seven criticality values and quotes are empty. It used 30,670 input tokens and 579 output
tokens (35,887 total, including 4,638 thinking tokens).

## The Shape of a Relationship

```json
{
  "id": 1,
  "company": { "id": 1, "name": "NVIDIA Corporation", "ticker": "NVDA" },
  "counterparty": { "id": 11, "name": "Taiwan Semiconductor Manufacturing Company Limited", "ticker": "TSM" },
  "type": "SUPPLIER",
  "provides": "semiconductor wafers",
  "evidence": "We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited, or TSMC, ...",
  "criticality": null,
  "criticalityEvidence": null,
  "report": { "form": "10-K", "filingDate": "2026-02-25", "url": "https://www.sec.gov/Archives/edgar/data/..." }
}
```

`company` is the company whose report it came from. `type` says which way round: `SUPPLIER` means the counterparty
supplies the company; `CUSTOMER` means the counterparty buys from it. On the graph, the arrow always points from the
supplier to the customer. `ticker` is `null` for companies without a US stock listing.
