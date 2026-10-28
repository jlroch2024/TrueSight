# TS-213 Measure Test Coverage

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 29 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 4 |
| Points | 2 |
| Labels | s4-new, sprint-4 |

## User Story
As the team, we want to see how much of TrueSight's code our tests run, and the build to fail when that falls below 50%, so that we meet the course's coverage requirement and notice when new code arrives without tests.

## What We Agreed
- **The course requires at least 50% coverage.** TrueSight measures lines on each side, backend and website separately, and both must stay at 50% or more. Measured on 22 October 2026 (`main` with TS-182): the backend's tests run **87% of its lines** (75% of branches), the website's **96% of its lines** (90% of branches). So this task measures and guards the number; it does not need new tests.
- **Backend: JaCoCo,** a Maven plugin this task names. It counts the quick tests and the database tests together, so the number matches everything `./mvnw verify` runs, writes a report, and makes `verify` fail below 50% of lines.
- **Website: `@vitest/coverage-v8`,** a package this task names, at exactly Vitest's version (3.2.7). A new `npm run coverage` runs the tests once with coverage, writes a report, and fails below 50% of lines.
- **CI runs both,** and every run keeps both reports as downloadable files, so a reviewer can see what a pull request left untested. The website job runs `npm run coverage` in place of `npm test`, so its tests still run once.
- **What is counted:** all backend code in `com.truesight`, and every website file in `src/` except the tests themselves, the test setup (`src/test/`) and `main.tsx`.
- **`AGENTS.md` gains the rule:** coverage stays at 50% of lines or more on each side, with the commands to see it.

## Acceptance Criteria
1. `./mvnw verify` in `backend/` writes `backend/target/site/jacoco/index.html`, counting both the quick tests and the database tests (`IT` classes).
1. `npm run coverage` in `frontend/` runs every test once, prints a coverage summary and writes `frontend/coverage/index.html`.
1. Each command fails when line coverage is below the minimum. Shown by hand: set the minimum above today's number, run it and see it fail, then set it back to 50%.
1. Every CI run of a pull request has both reports attached as artifacts.
1. `AGENTS.md` says how to see coverage and that each side must stay at 50% of lines or more.
1. `frontend/coverage/` is in `.gitignore`; no report is committed.

## Technical Notes
- **JaCoCo version:** 0.8.14 or newer. The project targets Java 21 (CI uses 21), but laptops run newer JDKs (one runs JDK 26), and an older JaCoCo cannot read newer class files. 0.8.14 ran the whole suite on JDK 26 on 22 October 2026.
- **`backend/pom.xml`:** add the plugin below inside `<build><plugins>`. List `report` before `check` in the same phase, so the report is written even when the check fails. Do not set `argLine` anywhere else in the pom, or the agent is dropped. The Docker image builds with `package`, which stops before `verify`, so the check never runs there.

```
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.14</version>
    <executions>
        <!-- Sets the agent on argLine, which Surefire (quick tests) and Failsafe (database tests) both use; both
             append to the same target/jacoco.exec. -->
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <!-- After the database tests, so the report counts both. -->
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.50</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- **`frontend/package.json`:** `"@vitest/coverage-v8": "3.2.7"` in `devDependencies` (exact, like `vitest`), and the script `"coverage": "vitest run --coverage"`.
- **`frontend/vite.config.ts`:** add `coverage` inside `test`, as below.

```
coverage: {
  provider: 'v8',
  include: ['src/**/*.{ts,tsx}'],
  exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx'],
  reporter: ['text-summary', 'html'],
  thresholds: { lines: 50 },
},
```

- **`.github/workflows/ci.yml`:** in the backend job, after `./mvnw -B verify`, an `actions/upload-artifact@v4` step named "Backend coverage" with `path: backend/target/site/jacoco` and `if: always()`, so the report is kept even when the check fails. In the frontend job, `npm run coverage` replaces `npm test`, followed by the same kind of step for `frontend/coverage`.
- **`AGENTS.md`:** add `./mvnw verify` (coverage report in `target/site/jacoco/`) and `npm run coverage` to the How to Run table, and under Tests: "Coverage stays at 50% of lines or more on each side; `./mvnw verify` and `npm run coverage` fail below it. Open the HTML report to see which lines no test runs."
- **Tests:** none new; the thresholds are the check. Acceptance criterion 3 is checked by hand and described in the pull request.
