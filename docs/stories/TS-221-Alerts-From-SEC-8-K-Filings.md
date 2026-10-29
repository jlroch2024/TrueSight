# TS-221 Alerts From SEC 8-K Filings

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 29 October 2026) |
| Epic | [TS-6 News and Alerts](TS-6-News-and-Alerts.md) |
| Sprint | Sprint 4 |
| Points | 8 |
| Labels | s4-new, sprint-4 |

## User Story
As a portfolio manager, I want TrueSight to watch the companies' own SEC 8-K filings as well as the news, so that I
hear about a cyber attack, a lost contract or a plant closing from the company itself, in its own words, often before
the press reports it.

## What We Agreed

- **Asked for by the product owner on 29 October 2026,** to give the news check a second, official source that
changes. US companies must file an 8-K (a "current report") within four business days of a major event.
- **8-Ks feed the alerts, not relationship discovery.** Suppliers and customers still come only from annual reports.
- **Every news check also reads the SEC's filing list** of each company it checks that has an SEC number (holdings,
their suppliers and customers, and suppliers' suppliers: the same companies as Google News). TrueSight already reads
this list to find annual reports, so this is no new service.
- **An 8-K is read when it reports one of these items:**
   - 1.02 Termination of a Material Definitive Agreement
   - 1.03 Bankruptcy or Receivership
   - 1.05 Material Cybersecurity Incidents
   - 2.04 Triggering Events That Accelerate or Increase a Direct Financial Obligation
   - 2.05 Costs Associated with Exit or Disposal Activities
   - 2.06 Material Impairments
   - 3.01 Notice of Delisting or Failure to Satisfy a Continued Listing Rule or Standard
   - 4.02 Non-Reliance on Previously Issued Financial Statements
   - 8.01 Other Events
- Any other 8-K (results, executive changes, shareholder votes, new agreements, Regulation FD notices) is never
downloaded, saved or sent to the AI.
- **TrueSight downloads the 8-K and reads what the company wrote,** as it downloads annual reports. The article's
headline is the company's name, the item, and the opening of that item's own text, such as "Clorox, Item 1.05: On
[date], the Company detected unauthorized activity on some of its information technology systems…": the first
sentences of the first kept item, up to 300 characters, cut at a word with "…". So the noise filter judges the
company's own words, and the alert says what happened.
- **When the item's text says nothing itself,** such as "The Company issued a press release, attached as Exhibit 99.1",
the headline falls back to the item's name ("Clorox Files an 8-K: Other Events"). Attachments are not read.
- **An 8-K becomes one article,** the same as a news headline: the last 7 days, newest first, at most 10 per company,
and never saved or downloaded twice. Its source is "SEC 8-K", its time is when the SEC accepted it, and opening it
opens the filing on the SEC website.
- **From there, nothing is new.** Filter Out Noise judges the headline like any other, so an 8.01 about a dividend is
noise and an 8.01 about a plant fire is an event. The alert reaches holdings in one or two steps, an analyst can
confirm or overrule it, and Group Alerts About One Event puts an 8-K and news articles about the same event on one
card ("Also Reported By").
- **Foreign companies** file 6-Ks, which name no items, so they are not read; a company without an SEC number is
checked on Google News only, as today.
- **The SEC failing never fails a news check:** that company's Google News is still saved, and its 8-Ks are read on
its next check. An 8-K that cannot be downloaded is saved with the item's name as its headline.

## Acceptance Criteria

1. A news check saves each 8-K filed in the last 7 days by a checked company with an SEC number, when it reports one of
the nine items, as an article with the source "SEC 8-K", the SEC's acceptance time and the filing's address.
1. Its headline is the company's name, the item, and the opening of that item's own text, at most 300 characters.
1. When the item's text only points to an attachment, or the 8-K cannot be downloaded, the headline is the item's name.
1. An 8-K that reports none of the nine items is not downloaded, saved or sent to the AI.
1. A filing already saved is not downloaded or saved again; a company checked in the last hour is not asked again.
1. A saved 8-K is judged by Filter Out Noise and becomes an alert like any article; the Inspector shows its headline
and the source "SEC 8-K", and opening it opens the filing on the SEC website.
1. A company without an SEC number is checked on Google News only.
1. When the SEC cannot be reached, the company's Google News is still saved and the news check finishes.

## Technical Notes

- **SEC:** in `report/SecClient.java`, add `recentCurrentReports(cik)`, reading the same submissions list as
`latestAnnualFiling` (`filings.recent`): for each `form` equal to `8-K`, its `items` (comma-separated, such as
`"1.05,9.01"`), `acceptanceDateTime`, `accessionNumber` and `primaryDocument`. The filing's address is built as
`latestAnnualFiling` builds it. Amendments (`8-K/A`) are skipped. Downloads use the existing `download`, so they keep
the shared pacing (10 requests a second) and the `User-Agent`.
- **Turning filings into articles:** a small `news/CurrentReports.java` holds the nine items and their official names,
keeps the filings that name at least one, and builds each one's `NewsItem` (headline, address, source "SEC 8-K",
acceptance time). For the headline it turns the download into text with `ReportTextExtractor.extract`, finds the
first kept item's heading ("Item 1.05"), and takes the text after it, without the heading itself. An item whose
text is under 40 characters, or whose first sentence names an exhibit (`Exhibit 99`), falls back to the item's name.
- **The check:** `NewsCheckService.NewsCompany` gains the company's `cik`. In `NewsCheckJob.run`, for a company with a
`cik`, its 8-K items are added to its Google News items before `service.save`, through the same
`newestRecentItems` rules (7 days, newest first, no repeated address). Before downloading, skip any 8-K whose address
is already saved for that company (`news_articles` has `UNIQUE (company_id, url)`). A
`SecClient.SecAccessException` for the list is caught for that company only, as a Google News failure is; one for a
download falls back to the item's name.
- **No table change:** 8-Ks are rows in `news_articles` (`source` "SEC 8-K"). Headlines stay within `title`'s 1,000
characters.
- **Website:** nothing new is needed; check that a long headline wraps in the Inspector's alert view and its "Also
Reported By" list, and that both open the filing.
- **Test data:** save, unchanged, one real `data.sec.gov/submissions/CIK….json` response for a company whose recent
filings include an 8-K naming one of the nine items and one naming none of them, trimmed only to its
`filings.recent` arrays, and the real 8-K documents those tests download, in `docs/examples/`, with README entries
(the company, its CIK, each filing's accession number, and when they were fetched). Find them with the SEC's
full-text search, filtered to 8-K. Run Filter Out Noise's prompt once by hand on the headlines built from them and
save Gemini's real answer beside them.
- **Tests:** `SecClientTest` (the 8-Ks and their items read from the saved response); `CurrentReportsTest` (which
items are kept, the headline from a saved 8-K, the fall-back for an exhibit-only item and for a failed download);
`NewsCheckIT` for criteria 1 to 8, with a fake `SecClient` and `NewsClient`, and the saved filings' times moved
relative to now, keeping their gaps, as `NewsCheckIT` already does for news.
