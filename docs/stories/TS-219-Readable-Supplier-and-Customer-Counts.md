# TS-219 Readable Supplier and Customer Counts

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 29 October 2026) |
| Epic | [TS-2 Portfolios](TS-2-Portfolios.md) |
| Sprint | Sprint 4 |
| Points | 2 |
| Labels | s4-new, sprint-4 |

## User Story
As a portfolio manager, I want to read at a glance how many suppliers and customers each holding has, so that I can see which holdings have a known supply chain and which have none.

## What We Agreed
- **Two short lines in place of the two chips.** Where each holding's row shows today's arrow chips, it shows two lines, one above the other: "3 Suppliers" and, below it, "1 Customer". They sit in line with the row's other text: the top line level with the holding's name, the bottom line level with its ticker, in the same sizes.
- **The number is bold, in the colour of its kind** (suppliers lime, as for what is supplied; customers teal); the word is in the row's quiet text colour, as the ticker is.
- **A zero is kept, but faint:** "0 Customers" in the faint text colour, number and word alike, so every row lines up.
- **A holding with neither** shows "Nothing Found" in faint text, on the top line only: its annual report names no supplier or customer. The app calls these relationships everywhere else, so the row never says "links".
- While a holding is not yet analysed, the place shows its status, as today.
- Singular and plural follow the count: "1 Supplier", "2 Suppliers".
- **Nothing is cut off:** the two lines fit the space the chips use today, up to "99 Suppliers" and "99 Customers", without shortening a holding's name further.

## Acceptance Criteria
1. A holding with 3 suppliers and 1 customer shows "3 Suppliers" above "1 Customer", the first level with its name and the second with its ticker.
1. A holding with 2 suppliers and 0 customers shows "2 Suppliers" above a faint "0 Customers".
1. A holding with neither shows "Nothing Found" in faint text.
1. A holding still being analysed shows its status instead.
1. "99 Suppliers" and "99 Customers" are shown whole, and the holding's name is not shortened more than today.

## Technical Notes
- `components/workspace/HoldingsTab.tsx` (the `link-count` spans, lines 287 to 296; their hidden `link-word` becomes the shown word) and their styles in `styles.css`. Reuse the row's text sizes and colour variables; add no new colours.
- **Tests:** `HoldingsTab.test.tsx` for criteria 1 to 4; criterion 5 is checked by hand on the workspace with the longest names in the test portfolio, as jsdom does not lay text out.
