# TS-87 Same Company Appears Twice Under SEC and Report Names

| | |
|---|---|
| Type | Bug |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-3 Reading Annual Reports](TS-3-Reading-Annual-Reports.md) |
| Sprint | Sprint 1 |
| Assignee | Jeremy Roch |

## What Happens
In a portfolio holding both NVDA and TSM, TSMC appears as two companies: "TAIWAN SEMICONDUCTOR MANUFACTURING CO LTD" (the holding) and "Taiwan Semiconductor Manufacturing Company Limited" (NVIDIA's supplier). NVIDIA's arrow from TSMC doesn't reach the TSM holding.

## Why
A holding's name comes from the SEC's list of companies, which writes "CORP", "CO LTD" and "INC/DE". Reports write "Corporation" and "Company Limited". The name tidy-up didn't ignore "Corp", "Company" or the SEC's "/DE", so the two spellings didn't match. The test passed only because it used "Broadcom Inc.", which both write the same way. Breaks Show Each Company Once, AC3, and AC1 whenever TSMC is itself a holding.

## What We Agreed

- Also ignore the endings the SEC and reports really use. Keep "Group" and "Holding", because they can tell companies apart: when unsure, keep apart.
- Names stored before the fix are brought up to date automatically.

## Acceptance Criteria

- The SEC's real names (NVIDIA CORP, TAIWAN SEMICONDUCTOR MANUFACTURING CO LTD, LAM RESEARCH CORP, QUALCOMM INC/DE, Sony Group Corp) match the names reports use.
- A holding and a supplier that are the same company are one company, whichever is found first.
- A real run with NVDA and TSM shows TSMC once.
- Apple and Applied Materials stay separate.

## Technical Notes

- `CompanyDirectory.nameKey` also ignores corp, company, incorporated, sa, ag, se, llc and lp, and drops what the SEC writes after a final slash. Chosen from all 10,428 SEC names.
- New `CompanyNameKeys` re-tidies stored names at startup.
