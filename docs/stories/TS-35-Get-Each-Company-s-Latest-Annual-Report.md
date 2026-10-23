# TS-35 Get Each Company's Latest Annual Report

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-3 Reading Annual Reports](TS-3-Reading-Annual-Reports.md) |
| Sprint | Sprint 1 |
| Points | 5 |
| Labels | sprint-1 |
| Assignee | Allan Kok Song Yeong |

## User Story
As a portfolio manager, I want the latest annual report found for each company I hold, so that the analysis is based on the company's own words.

## What We Agreed

- US companies file a 10-K and foreign companies file a 20-F. TrueSight uses the latest of either.
- Other SEC filings, such as quarterly reports, are not used, because they rarely name suppliers or customers.
- Analysis starts when the user presses Analyse, and covers every holding in the open portfolio.
- The report is turned into plain text, so the AI can read it and its quotes can be checked.
- Each report is downloaded once and kept. A company held in two portfolios is not downloaded twice.

## Acceptance Criteria

- Pressing Analyse finds NVIDIA's latest 10-K and ASML's latest 20-F.
- While analysis runs, each holding shows its status: Waiting, Analysing, Done, Failed, or No Report Found.
- Each holding shows its company name, its report's type and date, and a Show in Report link that opens the report on the SEC website.
- A ticker with no 10-K or 20-F shows "No Report Found".
- Pressing Analyse again does not download a report that is already saved.
- If the SEC website cannot be reached, the holding shows Failed with the reason.

## Technical Notes

- Endpoint: `POST /api/portfolios/{id}/analysis` starts the work and returns straight away. The work continues in the background, and `GET /api/portfolios/{id}/holdings` shows each holding's status and report.
- Ticker to SEC company number (CIK): `https://www.sec.gov/files/company_tickers.json`.
- A company's filings: `https://data.sec.gov/submissions/CIK##########.json`, with the CIK padded to 10 digits. Take the first filing whose form is `10-K` or `20-F`.
- The report's address: `https://www.sec.gov/Archives/edgar/data/{CIK}/{accession number without dashes}/{primary document}`.
- SEC rules: every request sends the `User-Agent` header from `SEC_USER_AGENT`, and no more than 10 requests a second are made.
- Convert the report's web page to plain text with jsoup, one paragraph per line. Save the text, link, type and date against the company, not the portfolio.
- A holding is Done once its report is saved. Relationship extraction is added to the same background job later, and reads the saved text.
- Tests never call the SEC website. They use saved copies of SEC's responses.
- Some reports, such as ASML's 20-F, break sentences across lines. Join lines that end mid-sentence, so each paragraph is one line and quotes can be checked.
Get each holding's company through CompanyDirectory.findOrCreate, with its SEC name, CIK and ticker.
The Analyse button goes on the portfolio page, at /portfolios/:portfolioId.

**Needs First:** Upload a Portfolio CSV
