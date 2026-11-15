# SiteLog

[![CI](https://github.com/ceciliagalvaoo/SiteLog/actions/workflows/ci.yml/badge.svg)](https://github.com/ceciliagalvaoo/SiteLog/actions/workflows/ci.yml)

Web application for construction sites: site managers record **daily logs**, and **safety
incidents** follow a controlled workflow from report to sign-off.

Individual project P42 — *Construction site logs and safety* — for the course *Rapid Development
of Java Applications Using Programming Frameworks*, Algebra Bernays University, 2026/27.

- **Documentation site:** https://ceciliagalvaoo.github.io/SiteLog/ (built from `docs/` by
  Docusaurus and published to GitHub Pages, see [Documentation](#documentation))
- **Figma prototype (clickable):** https://www.figma.com/proto/OdFchbgQXPWVLw115Wc3iz/SiteLog?node-id=20-3626&starting-point-node-id=20%3A3626 ·
  design file: https://www.figma.com/design/OdFchbgQXPWVLw115Wc3iz/SiteLog
- **Component set (Figma):** https://www.figma.com/design/OdFchbgQXPWVLw115Wc3iz/SiteLog?node-id=9-2 ·
  **Screens (Figma):** https://www.figma.com/design/OdFchbgQXPWVLw115Wc3iz/SiteLog?node-id=9-3 ·
  frame → template table: [Figma frame → template mapping](#figma-frame--template-mapping)
- **Hi-fi prototype (M2):** [`docs/prototype.pdf`](docs/prototype.pdf) · rationale in
  [`docs/design-rationale.md`](docs/design-rationale.md)
- **Wireframes (low-fi, M1):** [`docs/prototype/wireframes.pdf`](docs/prototype/wireframes.pdf) ·
  review in [`docs/design/wireframes.md`](docs/design/wireframes.md)

## What it does

> Status: M2. Sites, crews, daily logs (work entries, crew rows, sign-off and lock) and
> notifications work on screens; the rest of the scope below is built in M3–M4. The
> [project status](#project-status) table says what is implemented.

**Aggregate:** `Site → DailyLog → WorkEntry → Crew` (crews assigned to a work entry through
`CrewAssignment`, which carries hours, worker count and task) and `Site → Incident →
CorrectiveAction`. Full CRUD on screens and through the REST API.

**Advanced requirements**

1. **Lockable daily log.** A daily log locks after sign-off. Changing it afterwards needs a
   reopen request with a mandatory reason, approved by a safety supervisor; every change made while
   reopened is audited (Hibernate Envers, see [ADR-0003](docs/adr/0003-reopen-audit-trail.md)).
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
| M2 | week 8 (2026-11-22) | Architecture and database design, migrations + seed, parent-child CRUD on screens, hi-fi prototype and component set | Implemented; checkpoint 2026-11-22 |
| M3 | week 11 (2026-12-13) | Security with all roles, REST API + OpenAPI, audited reopen and severity workflow, AI feature behind a port with stub + evaluation cases, first deployment | Planned |
| M4 | week 14 (2027-01-03) | Compliance report, test coverage, i18n (en/hr), scheduled jobs, performance measurement, final deployment | Planned |
| Final | week 15 (2027-01-10) | Everything, including the 10-minute video | Planned |

What exists today (M1 + M2):

- Flyway migrations `V1`–`V6`: domain tables with constraints, lock triggers for signed-off
  daily logs, append-only Envers audit tables, notifications and demo data
  ([database schema](docs/architecture/database.md)). Hibernate runs with `ddl-auto=validate`.
- JPA entities for the whole model except the AI draft (M3); Hibernate Envers audits
  `DailyLog`, `WorkEntry` and `CrewAssignment` with the user in `revinfo`
  ([ADR-0003](docs/adr/0003-reopen-audit-trail.md)).
- Screens (Thymeleaf, English and Croatian, language switcher stored in a cookie):
  - **Sites** `/sites`: list with search, *All / Active / Finished* filter and paging; site
    overview with the daily logs tab; create, edit, delete (only without logs, incidents or AI drafts).
  - **Daily logs**: one form for the log with its work entries and crew rows (rows added and
    removed without JavaScript), detail page, separate pages to change one work entry or crew
    row, sign-off (at least one work entry) with confirmation, lock after sign-off, delete of a
    draft. A stale form version or concurrent change answers 409 with a reload link.
  - **Crews** `/crews`: list, create, edit, deactivate, delete only if never used.
  - **Notifications** `/notifications` and the bell in the top bar: unread counter, mark as read.
  - Error pages 403, 404, 409, 500 without stack traces.
- `/actuator/health` is public; every other URL requires a login. This is a temporary
  `SecurityConfig` (Spring Boot's generated default user); in M2 the services act as the seed
  user `manager@sitelog.test`. Real users and roles arrive in M3.
- Tests (`./mvnw clean verify`, Testcontainers PostgreSQL 17): schema constraints, lock and
  append-only triggers, demo seed, entity mappings, repositories (including an N+1 regression
  test, see [performance](docs/performance.md)), services, controllers and a test that renders
  every screen in English and Croatian.

Open items carried over to M3:

- Replace the temporary `SecurityConfig` with real users and roles; apply the user's language at
  login.
- Reopen workflow (AR1), incident screens and severity workflow (AR2), REST API, AI feature,
  first deployment.

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
| HikariCP | Spring Boot's default pool, configured explicitly for the Supabase pooler (see [HikariCP configuration](#hikaricp-configuration)). |
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
use the same credentials. Flyway creates the schema and the demo data on the first start.

| URL | What |
|---|---|
| http://localhost:8080/sites | Sites list (start here); a site opens its overview and daily logs |
| http://localhost:8080/crews | Crews |
| http://localhost:8080/notifications | Notifications |
| http://localhost:8080/actuator/health | Health check (public) |
| `?lang=hr` / `?lang=en` on any page | Switch the language (kept in a cookie) |

Until M3 the login is Spring Boot's generated default user: name `user`, password printed in the
console at startup (`Using generated security password: ...`).

Profiles: `dev` exists now. `stub` (AI without provider) and `prod` (Render + Supabase) arrive
in M3.

### Tests

```bash
./mvnw clean verify
```

Starts a PostgreSQL 17 container through Testcontainers (Docker must be running). CI runs
`./mvnw verify` on every push. Use `clean` locally: an IDE that compiles into `target/` can leave
class files there that do not match the sources.

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
| Architecture overview (C4, layers, profiles, request flows) | [`docs/architecture/overview.md`](docs/architecture/overview.md) |
| Database schema (tables, constraints, triggers, audit tables, demo data) | [`docs/architecture/database.md`](docs/architecture/database.md) |
| ADR-0001 Persistence model | [`docs/adr/0001-persistence-model.md`](docs/adr/0001-persistence-model.md) |
| ADR-0002 AI integration | Number reserved; written in M3 together with the AI feature. |
| ADR-0003 Reopen audit trail (Hibernate Envers) | [`docs/adr/0003-reopen-audit-trail.md`](docs/adr/0003-reopen-audit-trail.md) |
| Wireframes and review | [`docs/design/wireframes.md`](docs/design/wireframes.md) |
| Hi-fi prototype and design rationale | [`docs/prototype.pdf`](docs/prototype.pdf) · [`docs/design-rationale.md`](docs/design-rationale.md) |
| Performance (N+1 on the daily log list) | [`docs/performance.md`](docs/performance.md) |
| AI usage log | [`docs/ai-usage.md`](docs/ai-usage.md) |

The site is live at https://ceciliagalvaoo.github.io/SiteLog/. It is deployed by `.github/workflows/docs.yml` on pushes to `main` that touch `docs/` (repository setting: *Settings → Pages → Source: GitHub Actions*).

## Project structure

```
.
├── src/main/java/hr/algebra/sitelog/   application code by feature: site, crew, dailylog, incident,
│                                       notification, user; common/ and config/ (SecurityConfig: temporary)
├── src/main/resources/
│   ├── application.yml                 shared settings (HikariCP, ddl-auto=validate, Envers, actuator)
│   ├── application-dev.yml             local PostgreSQL from .env
│   ├── db/migration/                   Flyway migrations V1–V6 (schema, triggers, audit, seed, notifications)
│   ├── templates/                      Thymeleaf screens, fragments/, error/
│   ├── static/                         css/app.css, img/icons.svg, js/app.js
│   └── messages.properties, messages_hr.properties   UI texts (en, hr)
├── src/test/java/hr/algebra/sitelog/   unit, slice and Testcontainers tests (db/ = schema rules)
├── docs/                               Docusaurus site and documentation content
│   ├── intro.md                        overview (site home)
│   ├── architecture/                   domain model, architecture overview, database schema
│   ├── adr/                            architecture decision records (0001, 0003; 0002 reserved for M3)
│   ├── design/  prototype/             wireframes review and PDF export
│   ├── ai-usage.md                     AI usage log
│   └── docusaurus.config.ts, sidebars.ts, src/, static/   site configuration
├── .github/workflows/                  ci.yml, docs.yml
├── docker-compose.yml                  local PostgreSQL (service db)
└── .env.example                        template for the git-ignored .env
```

## Figma frame → template mapping

Each frame of the prototype ([`docs/prototype.pdf`](docs/prototype.pdf), page numbers below are
the frame numbers printed on each page) and the Thymeleaf template that implements it. Figma:
[Screens page](https://www.figma.com/design/OdFchbgQXPWVLw115Wc3iz/SiteLog?node-id=9-3) ·
[Component set](https://www.figma.com/design/OdFchbgQXPWVLw115Wc3iz/SiteLog?node-id=9-2).
Templates are in `src/main/resources/templates/`; every page uses `layout.html` and the fragments
in `fragments/` that mirror the Figma components (`button`, `input`, `chip`, `flash`, `table-row`,
`modal`, `toggle`, `navbar`, `notifications`, `crew-row`). One stylesheet, `static/css/app.css`,
holds the Figma tokens as CSS variables and the single 640 px breakpoint for the 390 px frames.

**Changes that apply to every screen** (details in
[From prototype to implementation](docs/design-rationale.md#from-prototype-to-implementation)):
secondary text darkened to pass WCAG AA (≥ 4.5:1); 14 px base / 12 px minimum type instead of
10–12 px; native `type="date"` / `type="time"` inputs instead of drawn text fields (the browser
shows the user's format and picker, the value is ISO); seed data (ZG-01 Lanište, ZG-02 Radnička,
ST-01 Hotel Žnjan) instead of the illustrative "Riverside Tower"; validation messages come only
from the server (`novalidate`), with the prototype's texts in `messages.properties` and their
Croatian translation in `messages_hr.properties`; the *Admin* sidebar group and role-specific
actions are hidden by role with `sec:authorize` once roles exist (M3).

| Frame (PDF page) | Template | Deviation from the frame and reason |
|---|---|---|
| 01 Access – Login | Spring Security login page until M3, then `auth/login.html` | Login with the seeded users arrives with Spring Security in M3. |
| 02 Daily logs – New daily log form | `dailylog/form.html` (+ `fragments/crew-row`, `fragments/input`) | One *Save daily log* button: every saved log is a draft until sign-off, so "Save and finish later" would do the same; *Cancel* added. Rows are added and removed with submit buttons (`action=addEntry`, `removeEntry-i`, `addCrew-i`, `removeCrew-i-j`), so the form works without JavaScript and nothing is saved until *Save*. |
| 03 Daily log form – validation errors | `dailylog/form.html` + `fragments/flash` | Same summary and field messages. Errors are also shown on work entry and crew row fields ("Hours worked must be between 0 and 24", "Select a crew"), which the frame does not draw. |
| 04 Site overview – "Daily log saved" flash | `site/detail.html` + `fragments/flash` | The flash is a message key stored before the redirect (POST-Redirect-GET) and rendered in the user's language; the detail line shows the log date. |
| 05 Daily log detail – Signed off, read-only | `dailylog/detail.html` | No editing control is rendered; a lock notice states the rule. *Request reopen* and the *Activity* timeline arrive with the audited reopen workflow (M3, Envers); "Created by" needs the audit trail and is not shown yet. |
| 06–07 Request reopen modal (+ error) | `fragments/modal` in `dailylog/detail.html` | M3 (reopen workflow, AR1). |
| 08 Daily log detail – reopen pending | `dailylog/detail.html` | M3 (reopen workflow). |
| 09 Daily log detail – Reopened | `dailylog/detail.html` | *Reopened* chip and an info message "Reopened for correction"; editing enabled as on a draft. Reason and approver are shown with the reopen workflow (M3). |
| 10 Daily log detail – Draft | `dailylog/detail.html` | Adds per-entry *Edit entry / Remove entry*, per-row *Edit / Remove* and *Add crew* (small changes without reopening the whole form), *Delete log* only when the draft has no incident or reopen history, and a confirmation before *Sign off* because sign-off locks the log. |
| 11 Mobile 390 – Login | `auth/login.html` (M3) | M3. |
| 12 Mobile 390 – Sites list | `site/list.html` | Same template; cards in one column below 640 px. |
| 13 Mobile 390 – Daily logs as cards | `site/detail.html` (`?tab=logs`) | The same table becomes cards (date block, date, weather · entries, status chip) through CSS. The status filter pills are not implemented: one log per day keeps the list short, it is paged and sorted by date, and each card carries its status chip. |
| 14 Mobile 390 – New daily log form, sticky Save | `dailylog/form.html` | One column; the *Log summary* card turns into the sticky bar with the work entry count and *Save daily log*. Crew rows stack Crew / Hours + Workers / Task as drawn. |
| 15–21 Incidents (list, form Low / High / errors, detail, corrective action modal) | `incident/*.html` | M3 (severity workflow, AR2). |
| 22–26 Assisted reporting (idle, loading, draft, manual input, unavailable) | `ai/shift-note.html` | M3 (AI feature). |
| 27 Compliance report | `report/compliance.html` | M3/M4 (AR3). |
| 28 Sites – Sites list | `site/list.html` | Card statistics (daily logs, open incidents, city) replaced by *Site manager*, *Started*, *End date*: counts per card would cost queries per site and are shown on the site overview; there is no city field. *Project status* offers All / Active / Finished (derived from the end date, there is no status column); search matches code or name. |
| 29 Sites – Site detail – overview | `site/detail.html` | KPI captions say what is counted ("Draft or reopened logs", "Open or overdue") instead of illustrative values; a *Site details* card shows code, manager, dates and description; *Delete site* added (see the modal row). |
| 30 Sites – Site settings | `site/form.html` | Same template as New site (`siteId` set); required fields are marked with `*`. |
| 31 Site detail – daily logs list | `site/detail.html` (`?tab=logs`) | Columns Date, Weather, Work entries, Crews, Crew hours, Status come from one aggregate query (no N+1). "Workers" and "Created by" are not columns of that query; search and month filter replaced by paging. |
| 31b Site detail – daily logs empty | `site/detail.html` + `fragments/table-row :: emptyState` | Same title and text, plus the *New daily log* button in the empty state. |
| 32 Site detail – incidents | `site/detail.html` (`?tab=incidents`) | Empty state with the number of open incidents; the incident list arrives with the incident screens (M3). |
| 33 Site detail – compliance | `site/detail.html` (`?tab=compliance`) | Empty state with the number of open corrective actions; the report arrives in M3/M4. |
| 34 Sites – New site form | `site/form.html` | Error summary (Flash component) when the form has errors; the end date error is shown on *End date*. |
| 35 Account – My profile | `account/profile.html` | M3 (with *Change password*). |
| 36–39 Administration – Users | `admin/user/*.html` | M3. |
| 40 Administration – Crews list | `crew/list.html` | Search, trade and status filters omitted: the list is short and Admin-only; *Edit* and *Delete* in each row. |
| 41 Administration – New crew form | `crew/form.html` | *Trade* is a text field, not a select: `crew.trade` is free text in the model. Same template for *Edit crew*. |
| 42 New crew form – name already used | `crew/form.html` | Same summary and "Name is already used" on the field (checked by the service, backed by the unique index). |
| 43–44 Notifications panel (unread, empty) | `fragments/notifications.html` (bell) + `notification/list.html` | The bell panel is in the layout; the full page lists all notifications with paging, *Open* and *Mark as read*. |
| 45 Components – component set | `fragments/*.html`, `static/css/app.css`, `static/img/icons.svg` | One fragment per component; tokens as CSS variables. |
| *no frame* – delete confirmation (site, daily log, crew) and sign-off confirmation | `fragments/modal :: confirm` in `site/detail.html`, `dailylog/detail.html`, `crew/list.html` | Built from the Modal component with a warning icon. A site with daily logs or incidents gets the explanation instead of the *Delete* button ("Set an end date to finish it"); a crew in use is refused and the message suggests deactivating it. |
| *no frame* – work entry and crew row pages | `workentry/form.html`, `crewassignment/form.html` | Built from the "Work completed" block and the Crew row component, to change one entry or one row from the log detail. |
| *no frame* – error pages 403, 404, 409, 500 and fallback | `error/403.html`, `error/404.html`, `error/409.html`, `error/500.html`, `error.html` + `fragments/error-page.html` | Built from the empty state and button components inside the layout; texts from `messages*.properties`, never a stack trace or exception message. 409 offers *Reload the page*. |
| *no frame* – pager | `fragments/pagination.html` | Lists are paged on the server (`?page=`); the frames fit on one page. |

## Coming later

These sections are required for the final submission and are filled in when the work exists.

### Live URL — *coming in M3*

Not deployed yet. Target: Render web service + Supabase database (first deployment in M3,
verified again in M4).

### Cold start — *coming in M3*

The Render free web service sleeps when idle (first request takes longer) and a free Supabase
project pauses after inactivity. Exact behaviour and the wake-up procedure before a demo will be
documented with the deployment.

### Demo credentials per role

Available once login exists (M3); seeded by Flyway `V4__seed_demo_data.sql` in every
environment. All accounts share the demo password **`SiteLog-demo-2026`**, which is valid only
for these `@sitelog.test` demo accounts.

| Role | Email | Sites |
|---|---|---|
| `ADMIN` | `admin@sitelog.test` | — |
| `SITE_MANAGER` | `manager@sitelog.test` | `ZG-01`, `ZG-02` |
| `SITE_MANAGER` | `manager.split@sitelog.test` | `ST-01` (second manager, to show the own-sites rule) |
| `SAFETY_SUPERVISOR` | `supervisor@sitelog.test` | — |
| `INSPECTOR` | `inspector@sitelog.test` | — |

What else the seed contains: [Database schema — Demo data](docs/architecture/database.md#demo-data-v4).

### HikariCP configuration

Set in `application.yml` for every profile (`spring.datasource.hikari.*`), not left at the
HikariCP defaults (10 connections, 30 s timeout).

| Setting | Value | Why |
|---|---|---|
| `maximum-pool-size` | `${DB_POOL_SIZE:5}` | In production the app connects through the Supabase Supavisor pooler in **session mode**, where every Hikari connection holds one server connection for as long as it is open. The free plan's pool is small and shared with the Supabase dashboard and any other client (check the pooler limit of the project's plan in the Supabase dashboard). 5 leaves headroom and is enough for one Render instance: transactions are short, and only one scheduled job will run at a time. `DB_POOL_SIZE` changes it without a new build. |
| `minimum-idle` | `2` | Two warm connections; the free instance serves little traffic. |
| `connection-timeout` | `10000` (10 s) | Fails fast when the pool is exhausted or the Supabase project is paused, so the request ends with an error page instead of hanging for 30 s. |
| `idle-timeout` | `300000` (5 min) | Idle connections above `minimum-idle` are released. |
| `max-lifetime` | `1500000` (25 min) | Every connection is replaced before the pooler or a network device drops it silently; HikariCP recommends a value a few minutes below any infrastructure timeout. |
| `keepalive-time` | `240000` (4 min) | Idle connections are tested every 4 min, so a connection closed by the pooler is replaced before a request gets it. |
| `spring.jpa.open-in-view` | `false` | A connection is held only inside a service transaction, never while a page renders, which keeps transactions short and a small pool sufficient. |

### Scheduled jobs and locking — *coming in M4*

Two jobs planned (overdue corrective actions, unsigned daily log reminder) with a lock against
duplicate execution. Strategy recorded in ADR-0004.

### Limitations — *coming in M4*

### Next steps — *coming in M4*

### Video (10 minutes) — *coming with the final submission*
