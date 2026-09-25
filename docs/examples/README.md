# Sample Data

Real data to build and test against, so every story can start on day 1 and tests never need the internet.

| File | What it is | Used by |
|---|---|---|
| `holdings.csv` | The sample portfolio: 10 companies, each weighted 10% | Upload a Portfolio CSV, and the demo |
| `relationships.json` | 17 relationships, in exactly the shape `GET /api/portfolios/{id}/relationships` returns | See the Supply Chain as a Graph, until the real endpoint exists |
| `nvidia-10k.txt` | The plain text of NVIDIA's latest 10-K, one paragraph per line | Find Suppliers and Customers with AI, in tests |
| `gemini-nvidia.json` | One real, unchanged Gemini answer for that 10-K: 7 suppliers, each with a quote | Find Suppliers and Customers with AI, in tests, so tests never call Gemini |

## Where It Came From

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

## The Shape of a Relationship

```json
{
  "id": 1,
  "company": { "id": 1, "name": "NVIDIA Corporation", "ticker": "NVDA" },
  "counterparty": { "id": 11, "name": "Taiwan Semiconductor Manufacturing Company Limited", "ticker": "TSM" },
  "type": "SUPPLIER",
  "provides": "semiconductor wafers",
  "evidence": "We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited, or TSMC, ...",
  "report": { "form": "10-K", "filingDate": "2026-02-25", "url": "https://www.sec.gov/Archives/edgar/data/..." }
}
```

`company` is the company whose report it came from. `type` says which way round: `SUPPLIER` means the counterparty
supplies the company; `CUSTOMER` means the counterparty buys from it. On the graph, the arrow always points from the
supplier to the customer. `ticker` is `null` for companies without a US stock listing.
