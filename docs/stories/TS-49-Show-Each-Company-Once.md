# TS-49 Show Each Company Once

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-3 Reading Annual Reports](TS-3-Reading-Annual-Reports.md) |
| Sprint | Sprint 1 |
| Points | 3 |
| Labels | sprint-1 |
| Assignee | Ben Aw Yong |

## User Story
As a portfolio manager, I want a company to appear once however its name is written, so that a supplier shared by several of my holdings shows as one company.

## What We Agreed

- Names are compared after tidying: capital letters, punctuation, and endings such as Inc., Ltd., Co., Corporation, Limited, N.V. and plc are ignored.
- A short name the report gives, such as "(TSMC)" or ", or TSMC", counts as another name for the same company.
- A company that files with the SEC is matched by its SEC company number, which is the most reliable way.
- When unsure, two names are kept apart. Merging two different companies is worse than showing one company twice.

## Acceptance Criteria

- "Taiwan Semiconductor Manufacturing Company Limited", "Taiwan Semiconductor Manufacturing Company" and "TSMC" appear as one company.
- "Samsung Electronics Co., Ltd." and "Samsung Electronics" appear as one company.
- A holding that another holding names as a supplier appears once, as the holding.
- "Apple" and "Applied Materials" stay separate.

## Technical Notes

- Keep one companies table. Every relationship points at a company id, never at a raw name.
- Store every name a company has been seen under, so a name seen again is matched straight away.
- Match in this order: the SEC company number; then an exact match on the tidied name or a stored alternative name; otherwise create a new company.
- Remove an ending only from the end of a name, so "Group Dynamics" keeps "Group".
- The AI's `alsoKnownAs` field gives the short name when a report has one. Store it as an alternative name.
- Improve CompanyDirectory: replace its simple nameKey() with proper tidying. Keep findOrCreate's inputs and outputs the same, so nothing that calls it has to change.

**Needs First:** Find Suppliers and Customers with AI
