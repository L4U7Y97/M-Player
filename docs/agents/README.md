# Agent board

Agents (planner, developer, tester, QA) talk to each other through the files in this folder instead of
carrying each other's context. Every agent reads this README, `docs/intent.md`, and the files it is
pointed to, does its job, and leaves a short written handoff for the next agent.

| File | Written by | Read by | Purpose |
|---|---|---|---|
| `docs/spec.md` | planner | everyone | Architecture, packages, data model, key decisions |
| `docs/tasks.md` | planner (developer ticks boxes) | developer, tester | Ordered task list with acceptance criteria |
| `handoff-dev.md` | developer | tester, QA | What was built, how to build/install, known gaps, UI labels / content descriptions QA can target |
| `handoff-test.md` | tester | developer, orchestrator | Unit test run result, coverage of logic, bugs found |
| `handoff-qa.md` | QA | developer, orchestrator | Emulator run steps, screenshots, logs, pass/fail per success criterion |
| `bugs.md` | tester, QA (developer marks fixed) | developer | One entry per bug: `## BUG-<n> [open|fixed] <title>` + repro, expected, actual, file hints |

Rules:
- Append, don't rewrite other agents' sections. Keep entries short and factual.
- A bug is closed only by the developer setting `[fixed]` and adding a one-line note of the fix; tester/QA re-verify.
- Build environment: JDK 21 at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`, Android SDK at
  `C:\Users\lpujol\AppData\Local\Android\Sdk` (platforms 34/35/36, build-tools 35.0.0), Gradle 8.14.3 cached,
  AGP 8.13.0 and Kotlin 2.2.20 cached. Build with `.\gradlew.bat` from the project root.
