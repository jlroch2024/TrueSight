# TS-180 Name Companies the Way People Know Them

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-3 Reading Annual Reports](TS-3-Reading-Annual-Reports.md) |
| Sprint | Sprint 3 |
| Points | 8 |
| Labels | s2-fix, sprint-3 |
| Assignee | isaiah |

## User Story

As a portfolio manager, I want suppliers and customers named the way people know them, and each company shown once, so that I recognise them at a glance and the graph does not split one company in two.

## What We Agreed

- **Common names (item 58).** AMD's 10-K names a customer "OpenAI OpCo", and the graph shows that. The relationship prompt asks for the name "as written in the text", plus an `alsoKnownAs` only when the text itself gives a short name, so Gemini's knowledge that it is OpenAI is never asked for.
- Gemini now also gives each company's **common name** ("OpenAI" for "OpenAI OpCo, LLC") and its **ticker** if it is listed, from its own knowledge. The common name is always this company's own, never its parent's or a sister company's: "Samsung Display" stays Samsung Display, which is not Samsung Electronics.
- The company is saved under its common name; the name as written in the report, and `alsoKnownAs`, are kept as other names, so matching still finds it either way. The evidence stays the report's own sentence, so the report's name is still there to read (item 58 proposed an extra "Named in the report as …" line; the evidence already shows it).
- **The same company once (item 59).** The holding SONY ("Sony Group", from the SEC) and AMD's customer "Sony" are two companies today. A company Gemini finds is matched to an existing company with the same ticker, but only when their names also agree: after dropping legal endings, one name begins with the other's first word ("Sony" and "Sony Group"). A ticker alone never joins two companies, in case Gemini gives a wrong one.
- **Names already stored.** A company already analysed is never sent to Gemini again, so a one-off pass fixes the companies found so far: an analyst starts it, Gemini gives the common name and ticker of every company found in a report, each is renamed where its common name differs (the old name kept as another name), and duplicates are joined by the rule above.
- The common name and ticker come from Gemini's knowledge, not the report, so they cannot be quote-checked. An analyst can correct one in Sprint 3 (TS-148 Fix or Add a Relationship).

## Acceptance Criteria

1. Analysing a report that names "OpenAI OpCo, LLC" saves the company as "OpenAI", with "OpenAI OpCo, LLC" kept as another name; the relationship's evidence is unchanged.
1. A customer "Sony" with ticker SONY, in a portfolio holding SONY ("SONY GROUP CORP"), is the holding's company: the graph shows one Sony.
1. A found company whose ticker matches an existing company with an unrelated name stays a separate company.
1. The prompt tells Gemini to give the company's own common name, never its parent's or a sister company's.
1. The one-off pass renames stored companies to their common names and joins duplicates by the same rule; running it a second time changes nothing.
1. Only an analyst can start the pass; anyone else gets 403.

## Technical Notes

- **Prompt:** `backend/src/main/java/com/truesight/relationship/RelationshipFinder.java`, `PROMPT` (lines 38–52). Add `commonName` ("the name this company is commonly known by, from your own knowledge; this company's own name, never its parent's or another company's in its group; the name as written if you are not sure") and `ticker` ("its main stock ticker if it is listed, from your own knowledge, or an empty string"). `GeminiAnswer.Found` gains both, and so does the response schema, if one is passed.
- **Saving:** `RelationshipFinder.java` line 91 becomes `companies.findOrCreate(commonName (or the written name if empty), null, ticker, List.of(writtenName, alsoKnownAs))`.
- **Matching:** `CompanyDirectory.findOrCreate` (a shared piece; this story may change it) gains a third step after the SEC number and the names: the same ticker, when the names agree as above. Keep the name rule in one place, beside the existing name keys (`CompanyNameKeys`).
- **The one-off pass:** `POST /api/companies/common-names`, analysts only (`currentUser.requireAnalyst()`), answers 202 and runs in the background. A new `CompanyController` (`@Tag(name = "Companies")`, `@Operation(summary = "Give the companies found in reports their common names, and join duplicates")`) calls a `CommonNameService`. It sends the names of every company without an SEC number to Gemini in batches of 50 (purpose `COMMON_NAMES`), asking for each one's common name and ticker. Renaming keeps the old name in `company_names`. Joining moves the relationships that point at the duplicate (`company_id` and `counterparty_id`) to the kept company, then deletes the duplicate, whose news is collected again at the next Check News. All in one transaction per pair. No table change.
- **AI answers in tests must be real.** Run the new prompt once on AMD's latest 10-K, and the pass's prompt once on the stored names, by hand with your own key; save both real answers in `docs/examples/` with the date and model in the README, as `gemini-nvidia.json` is. Never write or edit an AI answer by hand.
- **Tests:** `GeminiAnswerTest` (the new fields from the saved answer), a `CompanyDirectory` integration test (ticker and names agree: joined; ticker alone: not joined), a `CommonNameService` integration test with a fake `GeminiClient` returning the saved answer (rename, join, second run changes nothing), and the 403.
