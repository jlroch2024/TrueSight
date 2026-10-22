# TS-185 Fix or Add a Relationship

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 23 October 2026) |
| Epic | [TS-5 Trust and Review](TS-5-Trust-and-Review.md) |
| Sprint | Sprint 3 |
| Points | 5 |
| Labels | s3-new, sprint-3 |
| Assignee | Ben Aw Yong |

## User Story

As an analyst, I want to correct a relationship or add one the AI missed, so that the supply chain is complete.

## What We Agreed

- **Correct what is supplied.** On the Analyst Desk's Relationships tab, an analyst can edit a relationship's "provides" text. The evidence is never editable: it is the report's own words.
- **Add a missing relationship** from the **Add Relationship** button: choose the company whose report names it (any company with a stored report), type the other company's name, choose Supplier or Customer, say what is provided, and paste the sentence from that company's latest annual report.
- **The sentence is checked like the AI's quotes.** If it is not in the report, the form says "That sentence is not in {Company}'s annual report." and nothing is saved. If it is, the report's own words become the evidence.
- An added relationship is saved as **added by an analyst** and **Approved** (an analyst wrote it), and appears on every graph that includes the company, with "Added by an Analyst" in the Inspector. The other company is found or created through `CompanyDirectory`, like every company.

## Acceptance Criteria

1. Editing "provides" changes it everywhere it is shown, and records who edited it and when.
1. Adding a relationship with a sentence from the report saves it with the report's own words as evidence, Approved, added by that analyst.
1. A sentence not in the report is refused with 400 and the message above.
1. Adding the same relationship twice (same companies, type and evidence) is refused with 409.
1. The new relationship appears on the graph of a portfolio holding that company, and the Inspector says "Added by an Analyst".
1. Portfolio managers get 403.

## Technical Notes

- **Endpoints (analysts only):** `PUT /api/relationships/{id}` with `{ "provides": "…" }` (200); `POST /api/relationships` with `{ "companyId", "counterpartyName", "type", "provides", "sentence" }` (201); `GET /api/companies?withReport=true` for the form's company list (`[{ "id", "name" }]`). `@Tag(name = "Relationships")`.
- **Checking:** `QuoteChecker` on the company's latest report (`ReportRepository.findFirstByCompanyIdOrderByFilingDateDesc`), exactly as `RelationshipFinder` does.
- **Website:** the edit and the Add Relationship form on the Relationships tab (with `Select` and `RadioGroup`, never native controls); the Inspector's line for `source = ANALYST` (`RelationshipResponse` gains `source`).
- **Tests:** `RelationshipEditIT` with `docs/examples/nvidia-10k.txt` as a stored report: each criterion, using real sentences from that report.
