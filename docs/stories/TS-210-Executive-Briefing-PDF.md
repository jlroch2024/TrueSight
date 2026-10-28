# TS-210 Executive Briefing PDF

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 29 October 2026) |
| Epic | [TS-6 News and Alerts](TS-6-News-and-Alerts.md) |
| Sprint | Sprint 4 |
| Points | 5 |
| Labels | s4-new, sprint-4 |

## User Story
As a portfolio manager, I want to download a briefing of my portfolio's supply-chain risks, so that I can share it with my team.

## What We Agreed
- **Download Briefing** in the workspace's top bar makes a PDF of the open portfolio:
   1. a cover: the portfolio's name, the date, and "Prepared With TrueSight";
   1. the supply chain graph as it is on screen;
   1. the holdings: ticker, name, weight, report and filing date;
   1. the open alerts, most severe first, with their reach, exposure and approved next steps;
   1. the sole- and single-source dependencies, each with its sentence;
   1. a closing note on the sources: annual reports from the SEC, news from Google News, judgements by Gemini, reviewed by analysts where marked.
- **Built on the server with OpenPDF** (`com.github.librepdf:openpdf`, the latest 2.x release), which this story names, so no new library is added without one. IBM Plex Sans, the app's own typeface, is embedded, so the PDF looks like the app.
- **No new facts:** the PDF shows only what TrueSight already shows on screen.

## Acceptance Criteria
1. Download Briefing saves a PDF named after the portfolio and the date, with the six sections in that order.
1. The graph in the PDF is the graph as shown when the button was pressed.
1. Alerts, exposure, next steps and dependencies match what the app shows.
1. A portfolio with no alerts says "No open alerts." in that section.
1. Another user's portfolio answers 404.

## Technical Notes
- **Endpoint:** `POST /api/portfolios/\{id\}/briefing` with `\{ "graphPng": "<base64>" \}` (200, `application/pdf`, `Content-Disposition: attachment; filename="<Portfolio>-Briefing-2026-11-02.pdf"`). `@Tag(name = "Briefing")`.
- **The graph's picture:** the living graph is one canvas (`graph/LivingGraph.tsx`). Add a `snapshot()` to it, through a ref, that draws the current view once more, at twice the screen's resolution, and returns `canvas.toDataURL('image/png')`. Logos are already loaded with `crossOrigin = 'anonymous'` (`graph/logos.ts`), so the canvas can be read; a logo that failed to load shows its initials, as on screen.
- **Font:** IBM Plex Sans as TTF files (Regular and SemiBold) from IBM's own Plex release on GitHub, with its licence (SIL Open Font License), in `backend/src/main/resources/fonts/`. The website's `@fontsource-variable` package holds only web fonts, which OpenPDF cannot embed.
- **Downloading:** `apiDownload` in `api/client.ts` takes only a path today (a GET). Give it an optional `RequestInit` passed on to `send`, so the briefing can POST its picture; its callers are unchanged.
- **Tests:** `BriefingIT` reads the PDF back with OpenPDF's text extraction and checks each section's content against a portfolio with known data; website tests for the button.
