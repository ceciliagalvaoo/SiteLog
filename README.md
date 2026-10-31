# SiteLog

[![CI](https://github.com/ceciliagalvaoo/SiteLog/actions/workflows/ci.yml/badge.svg)](https://github.com/ceciliagalvaoo/SiteLog/actions/workflows/ci.yml)

Web application for construction sites: site managers record **daily logs**, and **safety
incidents** follow a controlled workflow from report to sign-off.

Individual project P42 — *Construction site logs and safety* — for the course *Rapid Development
of Java Applications Using Programming Frameworks*, Algebra Bernays University, 2026/27.

- **Documentation site:** https://ceciliagalvaoo.github.io/SiteLog/ (built from `docs/` by
  Docusaurus; first publication pending, see [Documentation](#documentation))
- **Figma prototype:** https://www.figma.com/design/OdFchbgQXPWVLw115Wc3iz/SiteLog
- **Wireframes (low-fi, M1):** [`docs/prototype/wireframes.pdf`](docs/prototype/wireframes.pdf) ·
  review in [`docs/design/wireframes.md`](docs/design/wireframes.md)

## What it does

> Status: the project skeleton exists (M1). The features below are the planned scope and are
> built in M2–M4. The [project status](#project-status) table says what is implemented.

**Aggregate:** `Site → DailyLog → WorkEntry → Crew` (crews assigned to a work entry through
`CrewAssignment`, which carries hours, worker count and task) and `Site → Incident →
CorrectiveAction`. Full CRUD on screens and through the REST API.

**Advanced requirements**

1. **Lockable daily log.** A daily log locks after sign-off. Changing it afterwards needs a
   reopen request with a mandatory reason, approved by a safety supervisor; every change made while
   reopened is audited (Hibernate Envers, see the domain model).
2. **Incident severity workflow.** Severities `LOW` · `MEDIUM` · `HIGH` · `CRITICAL`. `HIGH` and
   `CRITICAL` require extra fields (root cause, witnesses, medical treatment, authority notified)
   and a safety supervisor sign-off.
3. **Compliance report** per site and period, aggregating incidents, corrective actions and
   their completion.

**Roles.** `ADMIN` (sites, crews, users), `SITE_MANAGER` (daily logs of own sites, sign-off,
reopen requests, shift notes), `SAFETY_SUPERVISOR` (incident review and sign-off, reopen approval,
corrective actions) and `INSPECTOR` (read-only, compliance report).

**AI feature.** A site manager enters a free-text shift note; the backend asks Anthropic Claude
(through Spring AI 2.x) for a structured incident draft — severity, category, affected crews,
immediate action. The draft is validated, shown for review, and becomes an incident only when
a safety supervisor edits/accepts and signs it. AI output is never written to the domain without
that review. A `stub` profile replaces the provider for tests, CI and offline use.

Domain model, roles and state machines: [`docs/architecture/domain-model.md`](docs/architecture/domain-model.md).

## Project status

| Milestone | Due | Scope | Status |
|---|---|---|---|
| M1 | week 4 (delivered week 5, 2026-11-01) | Domain model, ADR-0001, empty project running with PostgreSQL in Docker, CI, low-fi wireframes, hosting and AI provider chosen | Done |
| M2 | week 8 (2026-11-22) | Architecture and database design, migrations + seed, parent-child CRUD on screens, hi-fi prototype and component set | Planned |
| M3 | week 11 (2026-12-13) | Security with all roles, REST API + OpenAPI, audited reopen and severity workflow, AI feature behind a port with stub + evaluation cases, first deployment | Planned |
| M4 | week 14 (2027-01-03) | Compliance report, test coverage, i18n (en/hr), scheduled jobs, performance measurement, final deployment | Planned |
| Final | week 15 (2027-01-10) | Everything, including the 10-minute video | Planned |

What exists today (M1):

- Spring Boot application `hr.algebra.sitelog.SiteLogApplication`.
- Flyway migration `V1__baseline.sql` (baseline only, no domain tables yet);
  Hibernate runs with `ddl-auto=validate`.
- `/actuator/health` is public; every other URL requires a login. This is a temporary
  `SecurityConfig` (Spring Boot's generated default user); real users and roles arrive in M3.
- Three integration tests (Testcontainers PostgreSQL 17): context loads with the Flyway baseline,
  health endpoint is public and `UP`, other pages redirect to the login page.

Open items carried over from M1:

- Replace the temporary `SecurityConfig` with real users and roles (M3).
- Review the `.env` import in the `dev` profile and refine the ADR-0001 rationale (see the
  [AI usage log](docs/ai-usage.md#ai-drafted-material-not-yet-fully-reviewed)).

## Technology choices

| Technology | Why |
|---|---|
| Java 25 (Temurin) | Required by the assignment; current LTS. |
| Spring Boot 4.1 (4.1.2) | Required by the assignment; dependency management and auto-configuration for everything below. |
| Spring MVC + Thymeleaf (+ security dialect) | Server-rendered screens with form binding and validation; the dialect hides actions per role. |
| Spring Security 7 | Form login, roles, method-level authorization (`SecurityFilterChain` bean). |
| Spring Data JPA / Hibernate 7 | Aggregate mapping with conscious fetch strategies; repositories without boilerplate. |
| PostgreSQL 17 | Graded database; same engine locally (Docker), in tests (Testcontainers) and in production (Supabase). |
| Flyway | Schema and seed as versioned SQL; Hibernate only validates (see [ADR-0001](docs/adr/0001-persistence-model.md)). |
| HikariCP | Spring Boot's default pool; sized explicitly per profile (justification below, M4). |
| Bean Validation | Constraint annotations on form objects and DTOs, messages in en/hr. |
| Spring Boot Actuator | `/actuator/health` for Docker and the Render health check. |
| springdoc-openapi 3.x *(M3)* | OpenAPI document and Swagger UI for the REST API. |
| Spring AI 2.x + Anthropic Claude *(M3)* | Provider abstraction with structured output; see [AI provider](#ai-provider). |
| ShedLock *(M4, to be decided in ADR-0004)* | Prevents duplicate scheduled-job runs on more than one instance. |
| JUnit 6, Mockito, Testcontainers | Unit tests with mocks; integration tests against real PostgreSQL, not H2. |
| JaCoCo *(M4)* | Coverage of the service package (target ≥ 80 %). |
| Maven (wrapper) | Reproducible build without a local Maven install. |
| Docker Compose | Local PostgreSQL now; app + DB with one command from M3. |
| GitHub Actions | `ci.yml`: build and tests on every push; `docs.yml`: documentation site. |
| Docusaurus | Documentation site rendered from `docs/`, published to GitHub Pages. |
| Figma | Wireframes and prototype, made before the implementation. |

## Hosting and AI decisions

### Hosting

- **Application: Render** (web service built from a multi-stage `Dockerfile`, added in M3), `prod` profile
  reading all credentials from environment variables, health check on `/actuator/health`.
- **Database: Supabase** (managed PostgreSQL). Render connects through the Supabase connection
  pooler (session mode) with `sslmode=require`; Flyway migrates the schema on first boot.
- The documentation site on GitHub Pages is **not** the application deployment.

Reasons and rejected alternatives: [ADR-0001](docs/adr/0001-persistence-model.md).

### AI provider

- **Anthropic Claude via Spring AI 2.x** (`spring-ai-starter-model-*` artifacts), added in M3.
- Behind a port (interface) with a Claude adapter and a stub adapter; tests and CI always use
  the stub.
- API key only in the `ANTHROPIC_API_KEY` environment variable; request cap, configurable model
  and low `max-tokens` in the deployed instance.
- Decision record: ADR-0002 (M3).

## Getting started

### Prerequisites

- JDK 25 (Temurin recommended). Maven is not needed: use `./mvnw`.
- Docker with Docker Compose (local database and Testcontainers).
- Node.js 22 (only for the documentation site).

### Run locally (profile `dev`)

```bash
cp .env.example .env            # then set POSTGRES_PASSWORD in .env
docker compose up -d db         # PostgreSQL 17 on localhost:${DB_PORT:-5432}
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile must be activated explicitly. It reads the same `.env` file as Docker Compose
(`spring.config.import: optional:file:.env[.properties]`), so the app and the container always
use the same credentials. The app listens on http://localhost:8080; check
http://localhost:8080/actuator/health.

The `dev` pool size of 5 (`spring.datasource.hikari.maximum-pool-size`) is deliberate for a
local PostgreSQL; production pool sizing will be justified against the Supabase session-pooler
limits in M3/M4.

Profiles: `dev` exists now. `stub` (AI without provider) and `prod` (Render + Supabase) arrive
in M3.

### Tests

```bash
./mvnw verify
```

Starts a PostgreSQL 17 container through Testcontainers (Docker must be running). CI runs the same
command on every push.

### Documentation site

```bash
cd docs
npm ci
npm start          # dev server; `npm run build` fails on broken links
```

## Documentation

| Document | Path |
|---|---|
| Overview | [`docs/intro.md`](docs/intro.md) |
| Domain model (Mermaid ER + state diagrams) | [`docs/architecture/domain-model.md`](docs/architecture/domain-model.md) |
| ADR-0001 Persistence model | [`docs/adr/0001-persistence-model.md`](docs/adr/0001-persistence-model.md) |
| Wireframes and review | [`docs/design/wireframes.md`](docs/design/wireframes.md) |
| AI usage log | [`docs/ai-usage.md`](docs/ai-usage.md) |

The site is deployed by `.github/workflows/docs.yml` on pushes to `main` that touch `docs/`. One-time repository setting: *Settings → Pages → Source: GitHub Actions*.

## Project structure

```
.
├── src/main/java/hr/algebra/sitelog/   application code (config/SecurityConfig: temporary)
├── src/main/resources/
│   ├── application.yml                 shared settings (ddl-auto=validate, actuator)
│   ├── application-dev.yml             local PostgreSQL from .env
│   └── db/migration/                   Flyway migrations (V1__baseline.sql)
├── src/test/java/hr/algebra/sitelog/   integration tests (Testcontainers)
├── docs/                               Docusaurus site and documentation content
│   ├── intro.md                        overview (site home)
│   ├── architecture/                   domain model (architecture and database design in M2)
│   ├── adr/                            architecture decision records
│   ├── design/  prototype/             wireframes review and PDF export
│   ├── ai-usage.md                     AI usage log
│   └── docusaurus.config.ts, sidebars.ts, src/, static/   site configuration
├── .github/workflows/                  ci.yml, docs.yml
├── docker-compose.yml                  local PostgreSQL (service db)
└── .env.example                        template for the git-ignored .env
```

## Coming later

These sections are required for the final submission and are filled in when the work exists.

### Live URL — *coming in M3*

Not deployed yet. Target: Render web service + Supabase database (first deployment in M3,
verified again in M4).

### Cold start — *coming in M3*

The Render free web service sleeps when idle (first request takes longer) and a free Supabase
project pauses after inactivity. Exact behaviour and the wake-up procedure before a demo will be
documented with the deployment.

### Demo credentials per role — *coming in M3*

Roles planned: `ADMIN`, `SITE_MANAGER`, `SAFETY_SUPERVISOR`, `INSPECTOR`. Accounts are seeded by
Flyway; credentials will be listed here.

### HikariCP configuration — *coming in M4*

Today only `dev` sets `maximum-pool-size: 5`. The production sizing and its justification
(Supabase pooler limits, Render instance) are documented with the deployment.

### Scheduled jobs and locking — *coming in M4*

Two jobs planned (overdue corrective actions, unsigned daily log reminder) with a lock against
duplicate execution. Strategy recorded in ADR-0004.

### Figma frame → template mapping — *coming in M2*

Table of Figma frames, the Thymeleaf template that implements each one, and deviations with
reasons.

### Limitations — *coming in M4*

### Next steps — *coming in M4*

### Video (10 minutes) — *coming with the final submission*
