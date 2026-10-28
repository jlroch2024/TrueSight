# TS-220 The Right Logo for Every Company

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 29 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 4 |
| Points | 3 |
| Labels | s4-new, sprint-4 |

## User Story
As a portfolio manager, I want every company on the graph to show its own logo, so that I can recognise companies at a glance.

## What We Agreed
- **Found on 29 October 2026,** with an 18-holding test portfolio on the live site. Three causes:
   - **Foreign tickers.** Gemini gives companies outside the US their home tickers, such as Samsung's `005930` or LG's `066570`. Logo.dev's ticker address does not know them and answers "not found", so they show initials. The domain search is never tried, because it is only used for companies without a ticker.
   - **Black logos.** Logo.dev serves General Motors' logo in black on a clear background. Once the margin is trimmed, the graph reads the logo's black frame as a solid background and fills the tile with black, hiding the mark.
   - **Logo.dev's own image.** For Coca-Cola, Logo.dev serves a black square with a thin white ribbon, not the red logo. That is Logo.dev's data, not a TrueSight fault, and this story does not change it.
   - **Number tickers can name another company.** A home ticker without its exchange can match a different company's logo.
- **A ticker is used for the logo only when it is a US ticker** (letters, with an optional dot and class letter, such as `BRK.B`). Any other company is found by its name through the domain search, as companies without a ticker are today.
- **When the ticker address answers "not found",** the domain search is tried before showing initials.
- **A dark logo on a clear background sits on a white tile,** where it can be seen, just as a white logo already sits on a dark one.

## Acceptance Criteria
1. A company with the ticker `005930` asks for its logo by its website's domain, not by that ticker.
1. A company with a US ticker that Logo.dev does not know falls back to its domain before showing initials.
1. A black logo on a clear background is drawn on a white tile with its mark visible.
1. Logos already drawn correctly (Apple, AMD's black square, NVIDIA) look the same as before.

## Technical Notes
- `graph/logos.ts`: `logoSource` uses `ticker/` only for a US ticker (`/^[A-Z]\{1,5\}(\.[A-Z])?$/`); `treatmentOf` adds a `dark` case alongside `light`: a clear background with a nearly black mark goes on a white tile. AMD's logo, a solid black square with a white mark, must still bleed.
- **Fallback:** `LogoBook` tries the domain address when the ticker address fails, using the domain from `GET /api/logos/domains`. `useLogoSettings` asks for the domains of companies whose ticker is not a US ticker as well as those without one (`LogoDomainService` is unchanged).
- **Tests:** `logos.test.ts` for criteria 1 to 4, with small images made in the test (a clear square with a black mark, a black square with a white mark), not saved Logo.dev images; `camera.test.ts` must still pass.
