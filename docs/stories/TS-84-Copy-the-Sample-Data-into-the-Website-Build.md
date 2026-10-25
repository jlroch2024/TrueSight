# TS-84 Copy the Sample Data into the Website Build

| | |
|---|---|
| Type | Bug |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 1 |
| Assignee | Jeremy Roch |

## What Happens
After TS-54 merged (pull requests #10 and #11), the Deploy workflow failed both times at "Build and deploy", even though CI passed. Google's build log shows:
`src/pages/supplyChain.test.ts: error TS2307: Cannot find module '../../../docs/examples/relationships.json'`.
The live site kept running the previous version, from TS-49, so the Supply Chain graph is not live.

## Why
TS-54's website test reads the shared sample data in docs/examples/relationships.json, as `AGENTS.md` asks tests to do. `npm run build` type-checks the test files too, so it needs that file. CI checks out the whole repository, so the file is there. The Dockerfile's website step copies only the `frontend/` folder, so in Google's build `docs/examples` is missing and the build fails. The gap has existed since TS-61; TS-54 is the first story whose website tests read `docs/examples`.

## What We Agreed

- Fix the Dockerfile, not the test: website tests reading `docs/examples` is correct.
- The sample data is only used while building. It never ends up in the live site.
- CI also builds the Docker image on every pull request, so a broken image is caught before merging instead of after.
- That check is required in the `main` ruleset, next to Backend and Frontend.

## Acceptance Criteria

- `docker build .` succeeds from the repository root.
- After merging, the Deploy workflow passes, and the Supply Chain page works on the live site.
- A pull request whose Docker image does not build cannot be merged.
- CI still passes on the pull request.

## Technical Notes

- In the Dockerfile's step 1, before `RUN npm run build`, add `COPY docs/examples /docs/examples`, with a comment saying why. The website builds in `/frontend`, so the tests' `../../../docs/examples` path lands on `/docs/examples`.
- Already tested on a laptop: with this line, the image builds, starts, and serves `/portfolios/1/supply-chain` with the graph library included.
- Found from the failed Deploy runs for TS-54's two merges (commits `10281c2` and `72bb138`).
- A new `Docker` job in .github/workflows/ci.yml runs `docker build .` without publishing the image. It takes about 3 minutes.
