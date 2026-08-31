# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project structure

Cinnamon is a multi-module Maven monorepo plus several standalone Python microservices, orchestrated via Docker Compose:

- `cinnamon-model/` — Java/Spring shared model classes (Maven submodule)
- `cinnamon-platform/` — Java 17 / Spring Boot 3.4.3 backend (Maven submodule), plus `cinnamon-frontend/` (Angular 19 + Angular Material) embedded as a static resource via `frontend-maven-plugin`
- `cinnamon-anonymization/` — Java/Spring wrapper around the ARX anonymization library (Maven submodule)
- `cinnamon-test/` — shared JUnit test suite for `cinnamon-model`, `cinnamon-platform`, `cinnamon-anonymization` (Maven submodule; kept separate to avoid circular deps)
- `cinnamon-evaluation/` — Python/Flask microservice (standalone, not a Maven module)
- `cinnamon-risk-assessment/` — Python/FastAPI microservice (standalone)
- `cinnamon-synthetization/` — Python/Flask microservice (standalone)

Root `pom.xml` only declares `cinnamon-model`, `cinnamon-platform`, `cinnamon-anonymization`, `cinnamon-test` as modules — the three Python services are independent projects with their own `requirements.txt`.

## Build & run

- Full stack: copy `.env.example` → `.env`, set `PG_PASSWORD`, then `docker-compose up -d`.
- Maven build (from repo root): `./mvnw clean install`.
- **GitHub Packages auth required**: `cinnamon-platform` depends on packages hosted on GitHub. Configure a PAT with `read:packages` scope as server id `github` in `~/.m2/settings.xml` (template at `.github/maven/settings.xml`).
- **ARX jar must be installed manually**: `cinnamon-anonymization` depends on `libarx-3.9.1.jar`, which isn't in a public repo. Install it locally first:
  `mvn install:install-file -Dfile=cinnamon-anonymization/src/main/resources/lib/libarx-3.9.1.jar ...` (see `.github/workflows/run-tests.yml` for exact coordinates).
- Backend dev mode: copy `cinnamon-platform/src/main/resources/application-dev.properties.example` → `application-dev.properties`, then run with `-Dspring.profiles.active=dev`.
- Frontend standalone: `cd cinnamon-platform/cinnamon-frontend && ng serve --open` (or `npm start`).
- **Node is not on `PATH`**: there's no global `node`/`npm`. `frontend-maven-plugin` provisions a local Node/npm into `cinnamon-frontend/node/` (binaries: `node`, `npm`, `npx`) the first time the Maven build runs. For any `npm`/`ng`/`npx` command in `cinnamon-frontend`, prepend that dir to `PATH`, e.g. `cd cinnamon-platform/cinnamon-frontend && PATH="$PWD/node:$PATH" npm run lint`. If `cinnamon-frontend/node/` doesn't exist yet, run `./mvnw -pl cinnamon-platform -am generate-resources` (or a full build) once to provision it, or install Node yourself.

## Testing

Test guidance differs by what changed:
- Changes in `cinnamon-model` or `cinnamon-platform` (excluding `cinnamon-frontend`): run the shared suite in `cinnamon-test` (`./mvnw -pl cinnamon-test -am test`), not just the module's own tests.
- Changes anywhere else (`cinnamon-anonymization`, `cinnamon-frontend`, or any of the Python services): running that module's own tests is enough.
  - `cinnamon-frontend`: `ng test` / `npm test` (Karma/Jasmine, `*.spec.ts` colocated with source)
  - `cinnamon-anonymization`: `./mvnw -pl cinnamon-anonymization -am test`
  - `cinnamon-risk-assessment`: `python -m xmlrunner discover -s Tests -p "*.py"` (unittest-style; note tests live in `Tests/`, capital T — stray `test_*.py` files at the module root are not part of the discovered suite)
  - `cinnamon-synthetization`: `pytest tests/` (split into `tests/unit/` and `tests/integration/`)
  - `cinnamon-evaluation`: pytest, tests in `tests/` (not currently wired into CI)
- `cinnamon-test` supports three DB backends for local runs (real Postgres, TestContainers, H2), configurable via `cinnamon.test.database` in `application-test.properties`. CI and production always use real Postgres, so prefer testing against Postgres too.

## Linting

- `cinnamon-frontend`: `npm run lint` (ESLint via `angular-eslint`, config at `cinnamon-frontend/eslint.config.js`). Rules that would require large-scale rewrites of existing code (control-flow syntax, `inject()`, standalone components, etc.) are downgraded/disabled; the rest run as warnings so the command exits 0 while still surfacing issues.
- Java modules (`cinnamon-model`, `cinnamon-platform`, `cinnamon-anonymization`, `cinnamon-test`): `./mvnw checkstyle:check` (or scoped to one module: `./mvnw -pl <module> -am checkstyle:check`). Ruleset is at root `checkstyle.xml` — deliberately narrow (unused imports, `equals(null)` safety, empty catch blocks, etc.), not bound to the default build lifecycle, so it never blocks `mvn install`. `org.bihmi.jal` / `org/bihmi/jal` in `cinnamon-anonymization` is vendored third-party source and excluded.
- No linter exists yet for the three Python microservices.

## Conventions

- Branch naming: `<issue-number>-<short-kebab-description>` (e.g. `368-admin-interface`).
- Java code is tab-indented; match existing style where no linter rule enforces it.
