# TS-42 Find Suppliers and Customers with AI

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-3 Reading Annual Reports](TS-3-Reading-Annual-Reports.md) |
| Sprint | Sprint 1 |
| Points | 5 |
| Labels | sprint-1 |
| Assignee | Lim Zhe An |

## User Story
As a portfolio manager, I want AI to read each annual report and list the suppliers and customers it names, so that I do not have to read hundreds of pages.

## What We Agreed

- Each relationship has four parts: the other company, whether it is a supplier or a customer, what it provides, and evidence from the report.
- A relationship is kept only if the AI's quote really appears in the report. What is shown is the report's own sentence, never the AI's wording of it.
- Only the business and risk sections of a report are sent to the AI, not the whole report.
- Reports are public, so a company that has already been analysed is not sent to the AI again, even for a different portfolio.

## Acceptance Criteria

- Analysing a portfolio that holds NVIDIA lists TSMC as a supplier, with NVIDIA's own sentence as the evidence.
- Every relationship shows the other company, whether it is a supplier or a customer, what it provides, and the evidence.
- A relationship whose quote is not in the report is not saved.
- Analysing a company a second time makes no new request to the AI.
- If the AI fails, the holding shows Failed and the reason.

## Technical Notes

- Input: the plain text of each holding's report, already saved in the database.
- For a 10-K, send from "Item 1. Business" up to "Item 2. Properties", which includes Item 1A Risk Factors. For a 20-F, send from "Item 3. Key Information" up to "Item 5. Operating and Financial Review".
- Each heading also appears in the table of contents, so use the longest match. If the headings are not found, use the whole report.
- If the result is over 200,000 characters, keep whole paragraphs only: first those with relationship words (supplier, sole source, single source, customer, foundry, contract manufacturer, purchased from), then other supply-chain paragraphs, in their original order.
- AI: Gemini, with the model from `GEMINI_MODEL` and the key from `GEMINI_API_KEY`. Ask for JSON only: a list of objects with `company`, `alsoKnownAs` (a short name the report gives, such as TSMC, or empty), `relationship` (`SUPPLIER` or `CUSTOMER`), `provides` and `quote`. Tell it to copy each quote exactly from the text.
- Checking a quote: ignore capitals, spacing and punctuation. It passes if at least 85% of its words appear in the same order, in one passage of the report not much longer than the quote, and every number and negative word (not, no, never, none, without, cannot) is among them. Save the report's matching passage as the evidence.
- Saved for each relationship: the company whose report it came from, the other company, supplier or customer, what it provides, the evidence, and the report's link, type and date.
- Endpoint: `GET /api/portfolios/{id}/relationships` returns the relationships for that portfolio's holdings, in the shape of `docs/examples/relationships.json`.
- If Gemini's answer is not valid JSON, ask once more. If it fails again, or Gemini returns an error, mark the holding Failed and save the reason.
- Tests never call Gemini. They use `docs/examples/nvidia-10k.txt` and `docs/examples/gemini-nvidia.json`.
- Get every supplier and customer through CompanyDirectory.findOrCreate, passing alsoKnownAs as another name.
docs/examples/README.md has the exact prompt used to record gemini-nvidia.json.

**Needs First:** Get Each Company's Latest Annual Report
