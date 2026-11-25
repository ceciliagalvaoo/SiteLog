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

> Status: M3 in progress (weeks 9 and 10 done). Login with four roles, user administration,
> sites, crews, daily logs (work entries, crew rows, sign-off, lock, audited reopen with revision
> history), incidents with the severity workflow and corrective actions, and notifications work
> on screens and through the REST API; the application is deployed. The AI feature (week 11),
> the compliance report and the scheduled jobs (M4) are not built yet. The
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
| M3 | week 11 (2026-12-13) | Security with all roles, REST API + OpenAPI, audited reopen and severity workflow, AI feature behind a port with stub + evaluation cases, first deployment | In progress. Week 9 done: login, roles and method-level authorization, user administration and password change, production profile, Dockerfile, Render Blueprint, app + database in Docker Compose, first deployment on Render + Supabase. Week 10 done early (24–25 Nov 2026, during the calendar week of week 9): REST API with OpenAPI and ProblemDetail, audited reopen (AR1) with revision history, incident severity workflow (AR2) with corrective actions, domain action aspect, notifications for reopen requests and high-severity incidents (migration V7). Week 11: AI feature, the M3 test package (REST, AR1/AR2, aspect, AI with stub), ADR-0002. |
| M4 | week 14 (2027-01-03) | Compliance report, test coverage, i18n (en/hr), scheduled jobs, performance measurement, final deployment | Planned |
| Final | week 15 (2027-01-10) | Everything, including the 10-minute video | Planned |

What exists today (M1, M2 and M3 weeks 9–10):

- Flyway migrations `V1`–`V7`: domain tables with constraints, lock triggers for signed-off
  daily logs, append-only audit tables (Envers and `domain_action`), notifications and demo data
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
  - **Reopen** (on the daily log detail): *Request reopen* with a mandatory reason (Site
    Manager of the site), *Approve* (optional note) / *Reject* (mandatory reason) for the Safety
    Supervisor, who has a queue of pending requests at `/reopen-requests`. **Revision history**
    `/daily-logs/{id}/history`: every Envers revision with before / after per field, the reopen
    requests and the recorded actions.
  - **Incidents** `/incidents` and `/sites/{id}/incidents`: list with status and severity
    filters, report and edit form (the four extra fields required for `HIGH` / `CRITICAL`),
    detail with the next workflow step for the signed-in role (start review → sign off → close),
    sign-off disabled while fields are missing, timeline of recorded actions; delete of a
    reported incident without actions.
  - **Corrective actions** (on the incident detail): add, edit, delete (Safety Supervisor), *mark
    as done* (Safety Supervisor or the assignee).
  - **Crews** `/crews`: list, create, edit, deactivate, delete only if never used.
  - **Notifications** `/notifications` and the bell in the top bar: unread counter, mark as read.
    Created for every active Safety Supervisor when a reopen is requested and when a `HIGH` /
    `CRITICAL` incident is reported, stored as message key + arguments and shown in the
    reader's language.
  - **Login** `/login` (e-mail + password) and **logout** (POST with CSRF token).
  - **My profile** `/profile`: name, preferred language, password change.
  - **Users** `/admin/users` (Admin): list with search, create, edit, activate / deactivate,
    assign sites to a site manager.
  - Error pages 403, 404, 409, 500 without stack traces, in the user's language.
- **Security** (Spring Security 7): users from `app_user` with BCrypt hashes, one role each;
  an inactive user cannot sign in; the preferred language is applied at login. URL rules in the
  `SecurityFilterChain`, business rules as `@PreAuthorize` on every service, including "only the
  manager of this site" (`SiteAccess`). Buttons a role may not use are hidden; the service
  refuses the call anyway (403). Details: [Architecture — Security](docs/architecture/overview.md#8-security)
  and the [permission matrix](docs/architecture/domain-model.md#permissions).
- **REST API** `/api/v1` for sites, daily logs, work entries, crew assignments, incidents,
  corrective actions, reopen requests and revisions, with OpenAPI / Swagger UI and RFC 9457
  `ProblemDetail` errors (see [REST API](#rest-api)).
- **Audit trail** in three layers: reopen requests (why, who decided), Hibernate Envers (what
  changed, before / after) and the `@AuditedAction` aspect, which records sign-off, reopen
  request / approval / rejection, incident transitions and corrective action changes with
  their outcome in `domain_action` ([Architecture — Domain action aspect](docs/architecture/overview.md#13-domain-action-aspect)).
- **Deployment files**: `application-prod.yml` (environment variables only), multi-stage
  `Dockerfile`, `render.yaml`, app + database with one `docker compose up` (see
  [Deployment](#deployment)).
- Tests (`./mvnw clean verify`, Testcontainers PostgreSQL 17): schema constraints, lock and
  append-only triggers, demo seed, entity mappings, repositories (including an N+1 regression
  test, see [performance](docs/performance.md)), services, controllers, a test that renders
  every screen in English and Croatian, and security tests: every protected endpoint × the five
  demo users + anonymous (`RoleAccessMatrixTests`), and forbidden calls over real HTTP (403 page,
  database unchanged, missing or forged CSRF token, inactive user, logout, session fixation;
  `ForbiddenCallsHttpTests`); REST API tests per controller (`@WebMvcTest`: status codes,
  `Location`, `ProblemDetail`) and over real HTTP (`RestApiHttpTests`: CSRF, 401, the `HIGH`
  incident flow 422 → 201 → 403 → 200 → 409 → 200); reopen and incident flows against
  PostgreSQL (`ReopenAndIncidentFlowTests`); the aspect (`DomainActionAspectTests`). Coverage
  report: [`docs/jacoco/`](docs/jacoco/index.html).

Deployed at https://sitelog-aq2e.onrender.com (Render + Supabase).

Next in M3 (week 11): AI feature (shift note → incident draft, stub and Claude adapters,
supervisor review), the M3 test package, ADR-0002.

## Technology choices

| Technology | Why |
|---|---|
| Java 25 (Temurin) | Required by the assignment; current LTS. |
| Spring Boot 4.1 (4.1.2) | Required by the assignment; dependency management and auto-configuration for everything below. |
| Spring MVC + Thymeleaf (+ security dialect) | Server-rendered screens with form binding and validation; the dialect hides actions per role. |
| Spring Security 7 | Form login against `app_user` (BCrypt), CSRF, URL rules in a `SecurityFilterChain` bean and `@PreAuthorize` on the services, so screens and the REST API share one set of rules. |
| Spring Data JPA / Hibernate 7 | Aggregate mapping with conscious fetch strategies; repositories without boilerplate. |
| PostgreSQL 17 | Graded database; same engine locally (Docker), in tests (Testcontainers) and in production (Supabase). |
| Flyway | Schema and seed as versioned SQL; Hibernate only validates (see [ADR-0001](docs/adr/0001-persistence-model.md)). |
| HikariCP | Spring Boot's default pool, configured explicitly for the Supabase pooler (see [HikariCP configuration](#hikaricp-configuration)). |
| Bean Validation | Constraint annotations on form objects and DTOs, messages in en/hr. |
| Spring Boot Actuator | `/actuator/health` for Docker and the Render health check. |
| springdoc-openapi 3.x (3.2.0) | OpenAPI 3.1 document and Swagger UI generated from the REST controllers, with the status codes and `ProblemDetail` examples of every operation. |
| Spring AI 2.x + Anthropic Claude *(M3)* | Provider abstraction with structured output; see [AI provider](#ai-provider). |
| ShedLock *(M4, to be decided in ADR-0004)* | Prevents duplicate scheduled-job runs on more than one instance. |
| JUnit 6, Mockito, Testcontainers | Unit tests with mocks; integration tests against real PostgreSQL, not H2. |
| JaCoCo | Coverage report in `docs/jacoco/`; `./mvnw verify` fails if a `service` package drops below 80 % line coverage. |
| Maven (wrapper) | Reproducible build without a local Maven install. |
| Docker / Docker Compose | Multi-stage image (same for Render and local); `docker compose up` starts app + database with one command. |
| GitHub Actions | `ci.yml`: build and tests on every push; `docs.yml`: documentation site. |
| Docusaurus | Documentation site rendered from `docs/`, published to GitHub Pages. |
| Figma | Wireframes and prototype, made before the implementation. |

## Hosting and AI decisions

### Hosting

- **Application: Render** (Docker web service built from the multi-stage `Dockerfile`, see [Deployment](#deployment)), `prod` profile
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
| http://localhost:8080/login | Sign in with a [demo account](#demo-accounts-and-permissions) (start here) |
| http://localhost:8080/sites | Sites list; a site opens its overview and daily logs |
| http://localhost:8080/incidents | Incidents of all sites (filters: status, severity) |
| http://localhost:8080/reopen-requests | Pending reopen requests (Safety Supervisor only) |
| http://localhost:8080/crews | Crews |
| http://localhost:8080/notifications | Notifications |
| http://localhost:8080/profile | My profile: name, language, password |
| http://localhost:8080/admin/users | Users (Admin only) |
| http://localhost:8080/swagger-ui.html | Swagger UI of the REST API (signed-in users only) |
| http://localhost:8080/v3/api-docs | OpenAPI document (JSON, signed-in users only) |
| http://localhost:8080/actuator/health | Health check (public) |
| `?lang=hr` / `?lang=en` on any page | Switch the language (kept in a cookie) |

Profiles: `dev` (local) and `prod` (Render + Supabase, every value from environment variables).
`stub` (AI without provider) arrives with the AI feature in M3.

### Run app and database with one command (Docker Compose)

```bash
cp .env.example .env            # then set POSTGRES_PASSWORD in .env
docker compose up --build       # PostgreSQL 17 + the application image, profile prod
curl localhost:8080/actuator/health   # {"status":"UP"}
docker compose down             # keeps the data volume
```

The `app` service is built from the same multi-stage `Dockerfile` as the deployment and waits
until the database is healthy (`depends_on: condition: service_healthy`). `docker compose up -d db`
still starts only the database for the `dev` workflow above (stop one of them if both use port 8080).
Then open http://localhost:8080/login. The container runs the `prod` profile, whose session
cookie is marked `Secure`; Chrome and Firefox accept such a cookie on `http://localhost`.

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

## REST API

JSON API under `/api/v1` for the two branches of the aggregate (sites → daily logs → work
entries → crew assignments; sites → incidents → corrective actions), plus reopen requests and
the revision history. It calls the same services as the screens, so the same role rules apply
(403 otherwise). Design and conventions:
[Architecture — REST API](docs/architecture/overview.md#10-rest-api).

| | Local | Production |
|---|---|---|
| Base URL | `http://localhost:8080/api/v1` | `https://sitelog-aq2e.onrender.com/api/v1` |
| Swagger UI | http://localhost:8080/swagger-ui.html | https://sitelog-aq2e.onrender.com/swagger-ui.html |
| OpenAPI 3.1 | http://localhost:8080/v3/api-docs | https://sitelog-aq2e.onrender.com/v3/api-docs |

- **Authentication: session + CSRF** (no separate token mechanism). Swagger UI and the OpenAPI
  document need a signed-in user: sign in at `/login` in the same browser first. In Swagger UI,
  call `GET /api/v1/csrf` and paste the `token` into *Authorize → csrf* before any `POST`,
  `PUT` or `DELETE`.
- **Status codes:** `201` + `Location` on create, `200` on update and workflow steps, `204` on
  delete; errors as RFC 9457 `ProblemDetail` (`application/problem+json`) with a stable `code`
  and, for field errors, `errors[]`: `400` invalid request, `401` not signed in, `403` role or
  CSRF, `404` not found, `409` stale version / locked log / step not allowed, `422` business rule
  (for example the four required fields of a `HIGH` incident).
- **Optimistic lock:** send the `version` you last read in the body (`?version=` on `DELETE`);
  a stale one answers `409`.
- **Paging:** `?page=0&size=20&sort=logDate,desc`; `size` at most 100, an unknown sort property
  answers `400`.

Example with `curl` and `jq` (local; for production use the production base URL and allow for
the [cold start](#cold-start)):

```bash
BASE=http://localhost:8080
JAR=$(mktemp)

# 1. CSRF token of a new (anonymous) session
TOKEN=$(curl -s -c "$JAR" -b "$JAR" "$BASE/api/v1/csrf" | jq -r .token)

# 2. Form login: 302 on success (to /login?error on failure)
curl -s -o /dev/null -w '%{http_code}\n' -c "$JAR" -b "$JAR" -X POST "$BASE/login" \
  --data-urlencode email=manager@sitelog.test \
  --data-urlencode password=SiteLog-demo-2026 \
  --data-urlencode _csrf="$TOKEN"

# 3. The login creates a new session and token: fetch it again
TOKEN=$(curl -s -c "$JAR" -b "$JAR" "$BASE/api/v1/csrf" | jq -r .token)

# 4. GET: the sites, then the daily logs of ZG-01 (a site this manager manages)
curl -s -b "$JAR" "$BASE/api/v1/sites?sort=code,asc" | jq '.content[] | {id, code, name}'
SITE=$(curl -s -b "$JAR" "$BASE/api/v1/sites?sort=code,asc" | jq '.content[] | select(.code == "ZG-01") | .id')
curl -s -b "$JAR" "$BASE/api/v1/sites/$SITE/daily-logs" | jq '.content[] | {id, logDate, status}'
LOG=<id of a SIGNED_OFF log from the list>
VERSION=$(curl -s -b "$JAR" "$BASE/api/v1/daily-logs/$LOG" | jq .version)

# 5. POST with the CSRF header: request a reopen of that log
curl -s -i -b "$JAR" -X POST "$BASE/api/v1/daily-logs/$LOG/reopen-requests" \
  -H "X-CSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"version\": $VERSION, \"reason\": \"Crew hours of the concrete crew were entered twice\"}"
# -> 201 Created, Location: .../api/v1/daily-logs/<LOG>/reopen-requests/<requestId>
#    409 if the log already has a pending request or is not signed off
```

Without the `X-CSRF-TOKEN` header the `POST` answers `403`; without the session cookie every
`/api/**` call answers `401` as `ProblemDetail` (the screens redirect to `/login` instead).

## Deployment

- **Image route: multi-stage `Dockerfile`** (not buildpacks, not Render's native runtime). Stage 1
  builds the jar with the Maven wrapper on `eclipse-temurin:25-jdk-alpine`; stage 2 runs it on
  `eclipse-temurin:25-jre-alpine` as a non-root user. `.dockerignore` is an allow-list, so `.env`
  and other local files never enter the image.
- **Render** web service (Docker, free plan, Frankfurt) defined in `render.yaml`, health check
  `/actuator/health`, auto-deploy on every commit to `main`; **Manual Deploy** in the dashboard
  redeploys the latest commit.
- **Supabase** PostgreSQL through the Supavisor **session pooler** (port 5432) with
  `sslmode=require`; Flyway migrates the schema on boot.
- Profile `prod` reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (and optionally `DB_POOL_SIZE`)
  from the environment, with no defaults; no stack traces in error responses and only
  `/actuator/health` exposed.

Step-by-step guide, environment variables, redeploy and wake-up procedure:
[`docs/operations/deployment.md`](docs/operations/deployment.md).

### Live URL

**https://sitelog-aq2e.onrender.com** — Render free web service (Frankfurt) on a Supabase
PostgreSQL database (session pooler, Frankfurt), first deployed in M3 week 9. Health check:
[`/actuator/health`](https://sitelog-aq2e.onrender.com/actuator/health). Sign in with the
[demo accounts](#demo-accounts-and-permissions). If the page takes a while, the service is waking
up (see Cold start below).

### Cold start

The Render free web service sleeps when idle, so the first request after a pause can take a few
minutes (about 3 minutes measured locally with the free plan's 512 MB / 0.1 CPU), and a free
Supabase project pauses after inactivity. The wake-up procedure before a demo is in
[Deployment — Cold start and pausing](docs/operations/deployment.md#cold-start-and-pausing).

## Demo accounts and permissions

Seeded by Flyway `V4__seed_demo_data.sql` in every environment (local, Docker Compose,
production). All accounts share the demo password **`SiteLog-demo-2026`**, which is valid only
for these `@sitelog.test` demo accounts. Sign in at `/login` with the e-mail; the UI switches to
the account's preferred language.

| Role | E-mail | Sites | What the account can do today |
|---|---|---|---|
| `ADMIN` | `admin@sitelog.test` | — | Create, edit and delete sites (and choose their manager); create, edit, deactivate and delete crews; create and edit users, activate / deactivate them, assign sites to a site manager. Reads everything. Does not write daily logs. |
| `SITE_MANAGER` | `manager@sitelog.test` | `ZG-01`, `ZG-02` | **Only on ZG-01 and ZG-02:** create, edit and delete draft or reopened daily logs, work entries and crew rows; sign off logs; request a reopen; report incidents, edit them while reported, move `LOW` / `MEDIUM` incidents through review, sign-off and close, delete a reported incident without actions. Marks as done the corrective actions assigned to them. Reads every site. |
| `SITE_MANAGER` | `manager.split@sitelog.test` | `ST-01` | The same on ST-01 only; a write on ZG-01 or ZG-02 answers 403 (shows the own-sites rule). |
| `SAFETY_SUPERVISOR` | `supervisor@sitelog.test` | — (acts on every site) | Approves or rejects reopen requests (queue `/reopen-requests`); reviews, signs off and closes incidents of any severity (the only role for `HIGH` / `CRITICAL`) and edits them until sign-off; manages corrective actions. Receives the reopen and high-severity notifications. Reads everything. |
| `INSPECTOR` | `inspector@sitelog.test` | — | Reads everything (including the revision history and the API); writes nothing. |

Every account can read its notifications and change its own name, language and password
(`/profile`; current password required, new one at least 12 characters and at most 72 bytes). The full matrix
(confirmed on 2026-11-24) is in
[Domain model — Permissions](docs/architecture/domain-model.md#permissions).
What else the seed contains: [Database schema — Demo data](docs/architecture/database.md#demo-data-v4).

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
| Deployment (Render + Supabase, env vars, redeploy, cold start) | [`docs/operations/deployment.md`](docs/operations/deployment.md) |

The site is live at https://ceciliagalvaoo.github.io/SiteLog/. It is deployed by `.github/workflows/docs.yml` on pushes to `main` that touch `docs/` (repository setting: *Settings → Pages → Source: GitHub Actions*).

## Project structure

```
.
├── src/main/java/hr/algebra/sitelog/   application code by feature: site, crew, dailylog, incident,
│                                       notification, user (+ user/security: login), audit (aspect);
│                                       api/ (REST /api/v1); common/ and config/
├── src/main/resources/
│   ├── application.yml                 shared settings (HikariCP, ddl-auto=validate, Envers, actuator, springdoc)
│   ├── application-dev.yml             local PostgreSQL from .env
│   ├── application-prod.yml            Render + Supabase, all values from environment variables
│   ├── db/migration/                   Flyway migrations V1–V7 (schema, triggers, audit, seed, notifications, domain actions)
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
├── Dockerfile, .dockerignore           multi-stage image (JDK 25 build, JRE 25 runtime, non-root)
├── docker-compose.yml                  PostgreSQL (db) + application (app) with one command
├── render.yaml                         Render Blueprint (web service, health check, env var names)
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
Croatian translation in `messages_hr.properties`; the *Admin* sidebar group and every action the
signed-in role may not perform are hidden (`sec:authorize`, plus the `siteAccess` bean for "only
the manager of this site"): Admin – sites, crews, users; Site Manager of the site – daily logs,
work entries, crew rows, sign-off, reopen request, incident report; Safety Supervisor – reopen
decisions, incident review / sign-off / close, corrective actions; Inspector – read only. Hiding
is cosmetic: the services refuse the same calls with 403.

| Frame (PDF page) | Template | Deviation from the frame and reason |
|---|---|---|
| 01 Access – Login | `auth/login.html` | Own page outside the layout (no navigation before sign-in). *Keep me signed in* omitted: no remember-me cookie on shared site tablets, the session ends with *Log out*. One message for a wrong password and an inactive account, so the page never reveals which e-mails exist. The *Live project* card is omitted (an anonymous page shows no project data) and the company footer became "Accounts are created by the administrator". EN / HR switcher added top right. |
| 02 Daily logs – New daily log form | `dailylog/form.html` (+ `fragments/crew-row`, `fragments/input`) | One *Save daily log* button: every saved log is a draft until sign-off, so "Save and finish later" would do the same; *Cancel* added. Rows are added and removed with submit buttons (`action=addEntry`, `removeEntry-i`, `addCrew-i`, `removeCrew-i-j`), so the form works without JavaScript and nothing is saved until *Save*. |
| 03 Daily log form – validation errors | `dailylog/form.html` + `fragments/flash` | Same summary and field messages. Errors are also shown on work entry and crew row fields ("Hours worked must be between 0 and 24", "Select a crew"), which the frame does not draw. |
| 04 Site overview – "Daily log saved" flash | `site/detail.html` + `fragments/flash` | The flash is a message key stored before the redirect (POST-Redirect-GET) and rendered in the user's language; the detail line shows the log date. |
| 05 Daily log detail – Signed off, read-only | `dailylog/detail.html` | No editing control is rendered; a lock notice states the rule. *Request reopen* only for the Site Manager of the site. The *Activity* card became **Reopen requests** (every request with its status chip, reason, requester and decision) plus a *Revision history* link to the full audit trail; "Created by" is not shown (the creator is not a column of the log). Shift facts in one row of five (date, weather, crews, workers, crew hours). |
| 06–07 Request reopen modal (+ error) | `fragments/modal :: modalIcon` in `dailylog/detail.html` | Same icon, eyebrow, title, text and *Send request*. A missing reason is a server-side error: the page is rendered again with the dialog already open (`data-open-on-load`) and the message under the field, so it also works without JavaScript. |
| 08 Daily log detail – reopen pending | `dailylog/detail.html` | Same amber banner (requester, time, reason, "remains locked"); *Approve* / *Reject* only for the Safety Supervisor, everyone else sees the banner only. *Reject* opens a modal with a mandatory reason (not drawn; built from the Modal component) because the site manager is told why. The supervisor reaches it from the new *Reopen requests* queue (see below). |
| 09 Daily log detail – Reopened | `dailylog/detail.html` | *Reopened* chip and the amber banner with reason, requester and approver (the approved request). Editing works as on a draft through *Edit log* and the per-entry / per-row controls instead of inline inputs in every crew row with *Save changes* / *Cancel editing*: one edit form keeps one optimistic-lock version and one Envers revision per save. |
| 10 Daily log detail – Draft | `dailylog/detail.html` | Adds per-entry *Edit entry / Remove entry*, per-row *Edit / Remove* and *Add crew* (small changes without reopening the whole form), *Delete log* only when the draft has no incident or reopen history, and a confirmation before *Sign off* because sign-off locks the log. |
| 11 Mobile 390 – Login | `auth/login.html` | Same markup: below 640 px the brand panel is hidden and the logo sits above the form, as drawn. |
| 12 Mobile 390 – Sites list | `site/list.html` | Same template; cards in one column below 640 px. |
| 13 Mobile 390 – Daily logs as cards | `site/detail.html` (`?tab=logs`) | The same table becomes cards (date block, date, weather · entries, status chip) through CSS. The status filter pills are not implemented: one log per day keeps the list short, it is paged and sorted by date, and each card carries its status chip. |
| 14 Mobile 390 – New daily log form, sticky Save | `dailylog/form.html` | One column; the *Log summary* card turns into the sticky bar with the work entry count and *Save daily log*. Crew rows stack Crew / Hours + Workers / Task as drawn. |
| 15 Incidents – Incidents list | `incident/list.html` (`/incidents`) | Same columns and chips; the row title links the whole row, the sub-line shows site code and reporter (there is no incident number such as INC-1042). Filters are *Status* and *Severity* (GET, kept by the pager) instead of search and site: the AR2 questions are "what is open" and "what is serious". The KPI strip is left to the compliance report (AR3, M4). *Report incident* is on the site's list only, because an incident belongs to the site it is reported from. Below 640 px the table becomes cards. |
| 16–18 Report incident – Low, High, High with errors | `incident/form.html` | One template for new and edit. The *Investigation* section is always present: grey and optional for Low / Medium, amber "Investigation required" with `*` markers for High / Critical. The server renders the state from the submitted severity; `app.js` only switches it while the select changes. Errors as drawn (title, occurred at, description, root cause, witnesses, treatment, "Select Yes or No"). *Site* is read-only (from the URL), *Authority notified?* is a Yes / No segmented control instead of a select, affected crews are checkbox chips. *Cancel* added. |
| 19 Incidents – Incident detail with corrective actions | `incident/detail.html` | Same summary strip, *What happened*, *Investigation*, *Corrective actions* and *Status timeline*. Only the next valid action is rendered (Start review → Sign off → Close) and only for a role that may do it; *Sign off* stays visible but disabled while AR2 fields are missing, and an amber notice lists them (each one is also marked "Missing" in the investigation card). *Close* is disabled while an action is open, with the reason. A *Timeline* card lists every recorded action (aspect, `@AuditedAction`). *Delete incident* (Site Manager, reported and without actions) asks for confirmation. *Mark as done* for the supervisor and the assignee; *Edit* / *Remove* for the supervisor. |
| 20–21 Add corrective action modal (+ errors) | `correctiveaction/form.html` | Drawn as its own page with the modal's card (icon, eyebrow, title, subtitle, *Cancel* / *Add action*): the form needs the assignee list and must show its errors after the POST without JavaScript. Same page for *Edit corrective action*. Safety Supervisor only. |
| 22–26 Assisted reporting (idle, loading, draft, manual input, unavailable) | `ai/shift-note.html` | M3 (AI feature). |
| 27 Compliance report | `report/compliance.html` | M3/M4 (AR3). |
| 28 Sites – Sites list | `site/list.html` | Card statistics (daily logs, open incidents, city) replaced by *Site manager*, *Started*, *End date*: counts per card would cost queries per site and are shown on the site overview; there is no city field. *Project status* offers All / Active / Finished (derived from the end date, there is no status column); search matches code or name. |
| 29 Sites – Site detail – overview | `site/detail.html` | KPI captions say what is counted ("Draft or reopened logs", "Open or overdue") instead of illustrative values; a *Site details* card shows code, manager, dates and description; *Delete site* added (see the modal row). |
| 30 Sites – Site settings | `site/form.html` | Same template as New site (`siteId` set); required fields are marked with `*`. |
| 31 Site detail – daily logs list | `site/detail.html` (`?tab=logs`) | Columns Date, Weather, Work entries, Crews, Crew hours, Status come from one aggregate query (no N+1). "Workers" and "Created by" are not columns of that query; search and month filter replaced by paging. |
| 31b Site detail – daily logs empty | `site/detail.html` + `fragments/table-row :: emptyState` | Same title and text, plus the *New daily log* button in the empty state. |
| 32 Site detail – incidents | `incident/list.html` (`/sites/{id}/incidents`) | The *Incidents* tab links to the site's incident list, which repeats the site header and tab bar with *Incidents* current; the count is the number of incidents in the list. KPI strip and search as on p. 15. `?tab=incidents` still answers with a link to the list. |
| 33 Site detail – compliance | `site/detail.html` (`?tab=compliance`) | Empty state with the number of open corrective actions; the report arrives in M3/M4. |
| 34 Sites – New site form | `site/form.html` | Error summary (Flash component) when the form has errors; the end date error is shown on *End date*. |
| 35 Account – My profile (user menu open) | `user/profile.html` + `fragments/navbar :: userMenu` | Identity card shows e-mail and managed sites instead of *Last sign-in* (not stored). The e-mail is read-only: it is the login and only the Admin changes it. Language is the segmented control (English / Croatian) and applies at once. A *Change password* card is added below, with its own errors (current password wrong, too short, confirmation mismatch); passwords are never sent back to the browser. |
| 36 Administration – Users list | `user/list.html` | *Role* and *Status* filter selects replaced by a text search (name or e-mail) and sortable *Name*, *E-mail* and *Status* columns. *Assigned sites* column omitted (one more query per row; the sites are on the user page); *Language* column added. Admin only. |
| 37 Administration – User detail | `user/form.html` (edit) | Detail and edit are one page: header with name, role and status chips and *Deactivate user* / *Activate user* (not on the Admin's own account). *Recent activity* is omitted until the audit trail exists (Envers, M3). |
| 38–39 New user form (+ e-mail already used) | `user/form.html` | Same fields, summary and "Email is already used" on the field. *Assigned sites* is a separate card on the edit page, with checkboxes and its own POST, only for an active Site Manager (a site can never be left without a manager, so the whole assignment is checked at once). *Active* only when editing (a new user is active). The initial password needs at least 12 characters (the frame says 10), the password policy of the application. Own account: role and *Active* read-only. |
| 40 Administration – Crews list | `crew/list.html` | Search, trade and status filters omitted: the list is short and Admin-only; *Edit* and *Delete* in each row. |
| 41 Administration – New crew form | `crew/form.html` | *Trade* is a text field, not a select: `crew.trade` is free text in the model. Same template for *Edit crew*. |
| 42 New crew form – name already used | `crew/form.html` | Same summary and "Name is already used" on the field (checked by the service, backed by the unique index). |
| 43–44 Notifications panel (unread, empty) | `fragments/notifications.html` (bell) + `notification/list.html` | The bell panel is in the layout; the full page lists all notifications with paging, *Open* and *Mark as read*. |
| — (no frame) Revision history | `dailylog/history.html` (`/daily-logs/{id}/history`) | Built from the daily log detail layout: Envers revisions newest first, each change with a *Before* / *After* table (old value struck through on red, new value on green, column headers so colour is not the only signal); side panel with the recorded actions and the reopen requests. Every signed-in user. |
| — (no frame) Reopen requests queue | `dailylog/reopen-requests.html` (`/reopen-requests`) | Built from the Data table: pending requests oldest first with log date, site, reason, requester and *Review* (opens the log, where p. 8 applies). Linked in the sidebar and, as a fifth tab, in the mobile tab bar for the Safety Supervisor only. |
| 45 Components – component set | `fragments/*.html`, `static/css/app.css`, `static/img/icons.svg` | One fragment per component; tokens as CSS variables. |
| *no frame* – delete confirmation (site, daily log, crew) and sign-off confirmation | `fragments/modal :: confirm` in `site/detail.html`, `dailylog/detail.html`, `crew/list.html` | Built from the Modal component with a warning icon. A site with daily logs or incidents gets the explanation instead of the *Delete* button ("Set an end date to finish it"); a crew in use is refused and the message suggests deactivating it. |
| *no frame* – work entry and crew row pages | `workentry/form.html`, `crewassignment/form.html` | Built from the "Work completed" block and the Crew row component, to change one entry or one row from the log detail. |
| *no frame* – error pages 403, 404, 409, 500 and fallback | `error/403.html`, `error/404.html`, `error/409.html`, `error/500.html`, `error.html` + `fragments/error-page.html` | Built from the empty state and button components inside the layout; texts from `messages*.properties`, never a stack trace or exception message. 409 offers *Reload the page*. |
| *no frame* – pager | `fragments/pagination.html` | Lists are paged on the server (`?page=`); the frames fit on one page. |

## HikariCP configuration

Set in `application.yml` for every profile (`spring.datasource.hikari.*`), not left at the
HikariCP defaults (10 connections, 30 s timeout).

| Setting | Value | Why |
|---|---|---|
| `maximum-pool-size` | `${DB_POOL_SIZE:5}` | In production the app connects through the Supabase Supavisor pooler in **session mode**, where every Hikari connection holds one server connection for as long as it is open. On the project's free plan (Nano compute) the pool size is 15 server connections per user + database, shared with the Supabase dashboard and any other client ([values read in the dashboard](docs/operations/deployment.md)). 5 leaves headroom, also while an old and a new instance overlap during a deploy (10 at most), and is enough for one Render instance: transactions are short, and only one scheduled job will run at a time. `DB_POOL_SIZE` changes it without a new build. |
| `minimum-idle` | `2` | Two warm connections; the free instance serves little traffic. |
| `connection-timeout` | `10000` (10 s) | Fails fast when the pool is exhausted or the Supabase project is paused, so the request ends with an error page instead of hanging for 30 s. |
| `idle-timeout` | `300000` (5 min) | Idle connections above `minimum-idle` are released. |
| `max-lifetime` | `1500000` (25 min) | Every connection is replaced before the pooler or a network device drops it silently; HikariCP recommends a value a few minutes below any infrastructure timeout. |
| `keepalive-time` | `240000` (4 min) | Idle connections are tested every 4 min, so a connection closed by the pooler is replaced before a request gets it. |
| `spring.jpa.open-in-view` | `false` | A connection is held only inside a service transaction, never while a page renders, which keeps transactions short and a small pool sufficient. |

## Coming later

These sections are required for the final submission and are filled in when the work exists.

### Scheduled jobs and locking — *coming in M4*

Two jobs planned (overdue corrective actions, unsigned daily log reminder) with a lock against
duplicate execution. Strategy recorded in ADR-0004.

### Limitations — *coming in M4*

### Next steps — *coming in M4*

### Video (10 minutes) — *coming with the final submission*
