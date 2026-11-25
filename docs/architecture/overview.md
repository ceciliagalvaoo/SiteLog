---
title: Architecture overview
sidebar_position: 2
description: System context, containers, components, layering rules, profiles, request flows and cross-cutting concerns of SiteLog.
---

# Architecture overview

The domain is described in the [domain model](./domain-model.md); persistence choices in
[ADR-0001](../adr/0001-persistence-model.md); the reopen audit trail in
[ADR-0003](../adr/0003-reopen-audit-trail.md); the physical schema in
[Database schema](./database.md).

## 1. System context

Who uses SiteLog and which external systems it depends on. Diagrams use Mermaid flowcharts
in the C4 style (Mermaid's native C4 syntax is experimental).

```mermaid
flowchart TB
    manager["Site manager<br/>[person]<br/>Daily logs, sign-off,<br/>reopen requests, shift notes"]
    supervisor["Safety supervisor<br/>[person]<br/>Incident sign-off, reopen approval,<br/>AI draft review"]
    inspector["Inspector<br/>[person]<br/>Read-only, compliance report"]
    admin["Admin<br/>[person]<br/>Sites, crews, users"]

    sitelog["SiteLog<br/>[software system]<br/>Construction site daily logs<br/>and safety incidents"]

    db[("Supabase PostgreSQL<br/>[external system]<br/>Managed database")]
    anthropic["Anthropic API<br/>[external system]<br/>Claude model, shift note<br/>to incident draft"]
    pages["GitHub Pages<br/>[external system]<br/>Documentation site only"]

    manager -->|"HTTPS, browser"| sitelog
    supervisor -->|"HTTPS, browser"| sitelog
    inspector -->|"HTTPS, browser / REST"| sitelog
    admin -->|"HTTPS, browser"| sitelog
    sitelog -->|"JDBC over TLS"| db
    sitelog -->|"HTTPS, profile ai only"| anthropic
    pages -.-|"documents, not part of the runtime"| sitelog
```

| Element | Status |
|---|---|
| Four roles | Implemented (M3 week 9): form login with the users of `app_user`, one role each ([section 8](#8-security)). |
| Supabase PostgreSQL | Chosen in M1 ([ADR-0001](../adr/0001-persistence-model.md)); `prod` profile, deployed in M3 week 9 ([section 9](#9-deployment)). |
| Anthropic API | Chosen in M1; AI feature in M3 (ADR-0002). Called only when profile `ai` is active. |
| GitHub Pages | Implemented: `docs.yml` publishes this site. It is not the application deployment. |

## 2. Containers

```mermaid
flowchart LR
    user["Browser<br/>[person, any role]"]
    client["REST client<br/>[Swagger UI, curl]"]

    subgraph render["Render (free web service)"]
        app["SiteLog application<br/>[container: Spring Boot 4.1, Java 25,<br/>Docker image from Dockerfile]<br/>Thymeleaf screens, REST API /api/v1,<br/>scheduled jobs, Flyway on startup"]
    end

    subgraph supabase["Supabase"]
        pooler["Supavisor pooler<br/>[session mode, sslmode=require]"]
        pg[("PostgreSQL 17.11<br/>[container: database]<br/>Domain tables, *_aud tables,<br/>triggers, shedlock table")]
    end

    ai["Anthropic API<br/>[external, Claude]"]

    subgraph github["GitHub"]
        ci["GitHub Actions<br/>ci.yml: ./mvnw verify<br/>docs.yml: Docusaurus build"]
        ghp["GitHub Pages<br/>[documentation site]"]
    end

    user -->|"HTTPS, HTML forms"| app
    client -->|"HTTPS, JSON"| app
    app -->|"JDBC, HikariCP"| pooler
    pooler --> pg
    app -->|"HTTPS via Spring AI 2.x"| ai
    ci -->|"publishes"| ghp
    ci -.->|"builds and tests the same code<br/>(Testcontainers, profile stub)"| app
```

| Container | Technology | Status |
|---|---|---|
| SiteLog application | Spring Boot 4.1 jar in a Docker image on Render | Multi-stage `Dockerfile` and `render.yaml` implemented (M3 week 9); runs locally with `docker compose up`. Deployed on Render (https://sitelog-aq2e.onrender.com) in M3 week 9. |
| Database | PostgreSQL 17 — `postgres:17-alpine` locally and in tests, Supabase 17.11 in production | Local + tests implemented (M1). Domain schema: implemented, migrations `V2`–`V7` ([Database schema](./database.md)). Supabase: connection settings in `application-prod.yml`, deployed in M3 week 9 (session pooler). |
| AI provider | Anthropic Claude through Spring AI 2.x | M3. |
| CI | GitHub Actions `ci.yml` (`./mvnw verify`), `docs.yml` (this site) | Implemented (M1). |

## 3. Components (inside the application)

Base package `hr.algebra.sitelog`, split **by feature**, each feature split **by layer**
(`domain`, `repository`, `service`, `web`). The diagram shows the packages that exist today
(solid) and the ones planned for M3–M4 (dashed).

```mermaid
flowchart TB
    subgraph dailylog["dailylog (detail of one feature)"]
        direction TB
        dl_web["web<br/>DailyLogController, WorkEntryController,<br/>CrewAssignmentController, LogReopenController,<br/>nested forms, RowActions"]
        dl_service["service<br/>DailyLogService, WorkEntryService,<br/>CrewAssignmentService, LogReopenService,<br/>DailyLogHistoryService, DailyLogAssembler,<br/>DailyLogRules, commands and view records"]
        dl_repo["repository<br/>DailyLogRepository (fetch joins,<br/>DailyLogSummary projection), WorkEntry-,<br/>CrewAssignment-, LogReopenRequestRepository"]
        dl_domain["domain<br/>DailyLog, WorkEntry, CrewAssignment,<br/>LogReopenRequest, LogStatus, ReopenStatus, Weather"]
        dl_web --> dl_service
        dl_service --> dl_repo
        dl_service --> dl_domain
        dl_repo --> dl_domain
    end

    subgraph features["other features (same layers)"]
        site["site<br/>CRUD, Active/Finished filter,<br/>SiteAccess (own-site rule)"]
        crew["crew<br/>CRUD, deactivate"]
        notification["notification<br/>bell, list, mark as read,<br/>NotificationPublisher"]
        incident["incident<br/>IncidentService, CorrectiveActionService,<br/>IncidentAccess, IncidentRules, screens"]
        user["user<br/>AppUser, Role, UserService,<br/>security: login, web: profile, users"]
        audit["audit<br/>@AuditedAction, DomainActionAspect,<br/>domain_action recorder and timeline"]
    end

    api["api<br/>REST /api/v1: controllers, request/response<br/>records, ProblemDetail, CSRF endpoint, OpenAPI"]

    subgraph common["common + config"]
        c_web["common.web<br/>WebExceptionHandler, LayoutModelAdvice,<br/>Flash, SafeRedirects, Pages"]
        c_other["common.exception / validation /<br/>audit (Envers) / domain / time"]
        config["config<br/>SecurityConfig, MethodSecurityConfig,<br/>AuditConfig, WebConfig (locale)"]
    end

    subgraph planned["planned"]
        ai["ai: IncidentDraftExtractor port,<br/>Anthropic + stub adapters (M3 week 11)"]:::planned
        report["report: compliance, AR3 (M4)"]:::planned
        jobs["scheduled jobs + ShedLock (M4)"]:::planned
    end

    api --> dl_service
    api --> incident
    api --> site
    dl_service --> site
    dl_service --> crew
    dl_service --> user
    dl_service --> notification
    incident --> notification
    audit -.->|"intercepts @AuditedAction methods"| dl_service
    audit -.-> incident
    c_web --> notification
    c_web --> incident
    c_web --> user
    c_web -.->|"advises every @Controller"| dl_web
    classDef planned stroke-dasharray: 5 5
```

| Package | Contents today | Status |
|---|---|---|
| `config` | `SecurityConfig` (filter chain: URL rules, form login, logout, BCrypt encoder, per-path 401/403 handlers), `MethodSecurityConfig` (`@EnableMethodSecurity`), `AuditConfig` (`@EnableAspectJAutoProxy(proxyTargetClass = true)`: Spring Boot 4 has no AOP auto-configuration without its AspectJ starter; `aspectjweaver` already comes with Spring Data JPA), `WebConfig` (`CookieLocaleResolver` + `LocaleChangeInterceptor`). | M2 (locale); M3 week 9 (security); M3 week 10 (`AuditConfig`, API handlers). |
| `common.domain` | `BaseEntity`: identity key, equality by id. | M2. |
| `common.exception` | `DomainException` (message key + arguments) and its subclasses `NotFoundException`, `InvalidFieldException`, `InvalidTransitionException`, `LogLockedException`, `DeletionNotAllowedException`, `MissingSeverityFieldsException` (AR2: the list of missing fields). | M2; `MissingSeverityFieldsException` M3 week 10. |
| `common.validation` | `@Chronological` class-level constraint (end not before start) and its validator. | M2. |
| `common.audit` | Envers revision entity `AuditRevision` (`revinfo`) and `AuditRevisionListener` (stores the user's email, or `system`). | M2 (mapped and writing revisions). |
| `common.time` | `TimeConfig`: one `Clock` bean in `sitelog.time-zone` (Europe/Zagreb). | M2. |
| `common.web` | `WebExceptionHandler`, `LayoutModelAdvice` (the two `@ControllerAdvice`s, section 7), `Flash` (flash message keys), `SafeRedirects` (back to the referer only if it is a local path), `Pages` (caps page size and whitelists sort fields). | M2; `LayoutModelAdvice` skips REST handlers since M3 week 10. |
| `user` | `domain`: `AppUser`, `Role`, `RoleName`. `security`: `AppUserDetailsService` (login lookup), `AppUserPrincipal` (the user kept in the session), `PreferredLanguageLoginSuccessHandler`. `service`: `UserService` (administration, profile, password change), `PasswordPolicy`, `CurrentUserProvider` with `SecurityContextCurrentUserProvider`. `web`: `LoginController`, `ProfileController`, `UserAdminController` (`/admin/users`), forms with `@PasswordLength`. | M2 (entities); M3 week 9 (login, administration, profile). |
| `site` | CRUD screens, `Active` / `Finished` filter computed in the query, delete rule; `service.SiteAccess`, the `@siteAccess` bean of the own-site rule ([section 8](#8-security)). | M2; `SiteAccess` M3 week 9. |
| `crew` | CRUD screens, delete only if unused, otherwise deactivate. | M2. |
| `dailylog` | Aggregate `DailyLog → WorkEntry → CrewAssignment`: nested form, separate work entry and crew row pages, detail with lock notice, sign-off, delete. Reopen: `LogReopenService` (request, approve, reject, supervisor queue), `LogReopenController` (modals on the log detail, `/reopen-requests` queue, `/daily-logs/{id}/history`), `DailyLogHistoryService` (Envers `AuditReader`, before/after per field). | M2 (CRUD, sign-off, lock); M3 week 10 (reopen, history). |
| `incident` | `Incident`, `CorrectiveAction`, enums, repositories; `IncidentStatsService` (open incident counter in the sidebar); `IncidentService` (report, edit, start review, sign-off, close, delete), `CorrectiveActionService`, `IncidentAccess` (`@incidentAccess` bean of the record-level rules), `IncidentRules` (AR2 field check, version check); `IncidentController`, `CorrectiveActionController` and their forms. | M2 (entities); M3 week 10 (workflow and screens). |
| `notification` | `Notification` entity, bell data, list page, *mark as read*, *mark all as read*, *open*; `NotificationPublisher` (creates notifications as message key + arguments); `MessageArgsConverter` (`body_args` column). | M2 (reading); M3 week 10 (producers, [section 14](#14-notifications)). |
| `audit` | `aspect`: `@AuditedAction`, `DomainActionAspect`. `domain`: `DomainAction`, `DomainActionType`, `ActionOutcome`, `AuditedEntityType`. `service`: `DomainActionRecorder` (writes the row), `DomainActionService` (timeline of one record). | M3 week 10 ([section 13](#13-domain-action-aspect)). |
| `api` | One package for the REST API, split by resource: `api.site`, `api.dailylog`, `api.incident`, `api.reopen` (controllers, request and response records); `api.common` (`ApiPaging`, `PageResponse`); `api.error` (`ApiExceptionHandler`, `FieldViolation`); `api.security` (`CsrfTokenController`, `ApiSecurityHandlers`, 401/403 as ProblemDetail); `api.docs` (`OpenApiConfig`, example bodies). | M3 week 10 ([section 10](#10-rest-api)). |
| `ai` | Port `IncidentDraftExtractor`, Anthropic and stub adapters, draft review flow. | M3 week 11. |
| `report` | Compliance report per site and period (AR3). | M4. |
| Scheduled jobs | Overdue corrective actions, unsigned log reminder; lock strategy in ADR-0004. | M4. |

## 4. Layering rules

| Layer | Contains | May call | Must not |
|---|---|---|---|
| `web` | `@Controller`, form objects (`DailyLogForm`, `WorkEntryForm`, ...; Bean Validation annotations), view names | its own and other features' `service` (for example the crew options of the daily log form) | contain business rules; touch repositories; open transactions; put entities in a form backing object |
| `api` | `@RestController` under `/api/v1/...`, request/response **records**, mappers entity → record | its feature's `service` | return entities; contain business rules |
| `service` | `@Service` classes: business rules, state transitions, transactions (class-level `@Transactional(readOnly = true)`, `@Transactional` on writes), authorization (`@PreAuthorize`); **commands** in (`DailyLogCommand`, ...) and **view records** out (`DailyLogDetails`, `SiteListItem`, ...) | own `repository`, other features' `repository` for existence checks of the delete rules, other features' `service`, the AI port (M3) | depend on `web`/`api` types (HTTP, `Model`, `RedirectAttributes`); return entities to controllers |
| `repository` | Spring Data JPA interfaces; JPQL with fetch joins / `@EntityGraph`; record projections for reports | — | contain business decisions |
| `domain` | JPA entities, enums, domain exceptions; invariants that depend only on the entity's own state | — | depend on Spring MVC or on other layers |

Rules applied everywhere:

- **Constructor injection only** (no field `@Autowired`); a single constructor needs no annotation.
- **Thin controllers:** bind and validate the form → call one service method → redirect
  (POST-Redirect-GET) with a flash message, or re-render the form on validation errors.
- **Forms and DTOs are not entities.** Forms live in `web`, request/response records and their
  mappers in `api`. Mapping happens inside the service transaction or from a fully loaded
  object, because `open-in-view=false` makes lazy loading outside a transaction fail.
- **Domain exceptions → HTTP.** Services throw subclasses of `DomainException`, which carry a
  message key and arguments, never translated text. For screens: `InvalidFieldException` is
  caught by the controller and put on the form field (form re-rendered); everything else goes
  to `WebExceptionHandler` (section 7). For the REST API, `ApiExceptionHandler`
  (`@RestControllerAdvice`) turns the same exceptions into `ProblemDetail` (400 validation, 404
  not found, 409 conflict / locked log / stale version / workflow step, 422 business rule;
  [section 10](#10-rest-api)). 429 for the AI request cap comes with the AI feature.
- **Schema only through Flyway**; Hibernate validates (`ddl-auto=validate`). Implemented.

## 5. Profiles

Profiles are combined: one **datasource** profile plus one **AI** profile
(for example `dev,stub`, `prod,ai`). No profile has default credentials; secrets come from the
environment.

| Profile | Datasource | AI adapter | Secrets come from | Status |
|---|---|---|---|---|
| `dev` | PostgreSQL 17 from `docker-compose.yml`; pool settings shared from `application.yml` (5 by default) | — (combine with `stub` or `ai`) | git-ignored `.env` (template `.env.example`), read by Docker Compose and by Spring (`spring.config.import`) | Implemented (M1). |
| `test` | Testcontainers `postgres:17-alpine`, connection injected by `@ServiceConnection` | `stub` | none (container credentials are generated) | Testcontainers implemented (M1) without a named profile; `test` profile with the stub adapter in M3. |
| `stub` | — | `StubIncidentDraftExtractor`: deterministic drafts, no network | none | M3. Always used in tests and CI. |
| `ai` | — | `AnthropicIncidentDraftExtractor` via Spring AI 2.x; cheap configurable model, low `max-tokens`, request cap | `ANTHROPIC_API_KEY` environment variable | M3. |
| `prod` | Supabase through the Supavisor pooler, session mode, `sslmode=require`; Flyway migrates on startup | `ai` on Render (`stub` possible if the key is absent) | Render environment variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (optional `DB_POOL_SIZE`); AI key in M3 | `application-prod.yml` implemented (M3 week 9); AI part with the AI feature. |

Exactly one AI adapter bean exists at runtime: activating neither or both of `stub` and `ai`
fails at startup instead of picking a provider silently.

## 6. Request flows

### 6.1 Creating a daily log (screen, POST-Redirect-GET)

```mermaid
sequenceDiagram
    autonumber
    actor M as Site manager (browser)
    participant F as SecurityFilterChain
    participant C as DailyLogController (web)
    participant S as DailyLogService (proxy)
    participant R as Repositories
    participant DB as PostgreSQL

    M->>F: POST /sites/42/daily-logs (form fields, CSRF token)
    F->>F: authenticated session and CSRF checked
    F->>C: create(siteId, @Valid DailyLogForm, BindingResult)
    alt form invalid (Bean Validation)
        C-->>M: 200, form re-rendered with field errors (no service call, no transaction)
    else form valid
        C->>S: create(siteId, command built from the form)
        S->>S: @PreAuthorize (SITE_MANAGER of this site), evaluated before the transaction
        Note over S,DB: @Transactional begins (connection taken from HikariCP)
        S->>R: load Site, check date not in the future and no log exists for (site, date)
        R->>DB: SELECT
        S->>R: save(new DailyLog, status DRAFT)
        R->>DB: INSERT daily_log (and daily_log_aud + revinfo via Envers)
        Note over S,DB: commit: flush, UNIQUE (site_id, log_date) checked, connection returned
        S-->>C: id of the new log (no entity leaves the service)
        C-->>M: 302 redirect to /sites/42, flash "Daily log saved"
        M->>F: GET /sites/42
        F->>C: SiteController.detail (read-only service calls, no entity in the model)
        C-->>M: 200, site overview with the flash message
    end
```

- **Transaction boundary:** starts when the call enters the service proxy and ends at commit,
  before the controller builds the redirect. The controller and the view run outside any
  transaction; with `open-in-view=false` they cannot trigger lazy loading.
- **Authorization:** `@PreAuthorize` is checked by the method-security interceptor, which by
  default sits outside the transaction interceptor, so a denied call never opens a transaction.
  A denial returns 403 (screen: error page; API: `ProblemDetail`).
- **Validation in two places:** field format on the form (`@NotNull`, `@Size`, ...); business
  rules (one log per site and day, site must be active) in the service, backed by the database
  constraint.
- **REST variant:** `POST /api/v1/sites/42/daily-logs` with a JSON request record goes to the
  **same** service method. Success answers `201 Created` with a `Location` header and a
  response record; a validation error answers 400 and a domain error its `ProblemDetail`
  status, instead of a re-rendered form or a flash message.

Status: screens, service and Envers implemented (M2); `@PreAuthorize` with real roles
implemented (M3 week 9); the REST variant implemented (M3 week 10, `DailyLogApiController`).

### 6.2 Signing off a daily log, and two users at the same time

The edit form and the sign-off form both carry the log's `version` as a hidden field.
Below, user A signs off while user B saves a change to a work entry of the same log; both
opened the page at `version = 7`.

```mermaid
sequenceDiagram
    autonumber
    actor A as User A (sign-off)
    actor B as User B (edit work entry)
    participant S as DailyLogService
    participant DB as PostgreSQL (daily_log row, triggers)

    A->>S: signOff(logId, version 7)
    Note over S,DB: transaction A begins
    S->>DB: SELECT daily_log (status DRAFT, version 7)
    S->>S: status is DRAFT or REOPENED, form version equals 7
    S->>DB: UPDATE daily_log SET status = SIGNED_OFF, version = 8 WHERE id = ? AND version = 7
    DB->>DB: trigger on daily_log: OLD.status is not SIGNED_OFF, change allowed
    Note over S,DB: commit A, 1 row updated

    B->>S: updateWorkEntry(logId, entryId, version 7)
    Note over S,DB: transaction B begins
    S->>DB: SELECT daily_log with OPTIMISTIC_FORCE_INCREMENT (status SIGNED_OFF, version 8)
    alt normal case: the service sees the new state
        S-->>B: domain exception (log locked, or stale version 7 vs 8), rollback, 409
    else B read the log before A committed
        S->>DB: UPDATE work_entry
        DB-->>S: trigger on work_entry finds parent SIGNED_OFF, raises error
        S-->>B: rollback, 409
    else B wrote first and A's UPDATE runs at the same time
        S->>DB: at commit, UPDATE daily_log SET version = 8 WHERE id = ? AND version = 7
        DB-->>S: waits for A's row lock, re-checks WHERE after A commits, 0 rows
        S-->>B: ObjectOptimisticLockingFailureException, rollback, 409
    end
```

- **Who wins:** whichever transaction updates the `daily_log` row first. PostgreSQL row locks
  serialize the two writes; under `READ COMMITTED` the loser's `WHERE version = 7` is
  re-evaluated against the committed row and matches nothing. The second request always ends
  in **409 Conflict** (`ProblemDetail` in the API, flash error plus reload on the screen); no
  update is lost.
- **Why `OPTIMISTIC_FORCE_INCREMENT`:** B changes only a child (`work_entry`). Without the
  forced increment the root's `version` would not change and the conflict with A's sign-off
  could go unnoticed ([ADR-0001](../adr/0001-persistence-model.md), decision 6).
- **Where the triggers act:** last line of defence. The service rejects edits of a
  `SIGNED_OFF` log first (domain exception → 409); the triggers on `daily_log`, `work_entry`
  and `crew_assignment` reject any `INSERT`/`UPDATE`/`DELETE` that reaches the database anyway
  (a bug in the service, a race like the second branch above, or manual SQL). The exact rules
  are in [Database schema — Locked daily logs](./database.md#locked-daily-logs).
- **Reopen** follows the same pattern: a `LogReopenRequest` with a reason; on approval by a
  safety supervisor the log goes to `REOPENED`, `revision` + 1, and the changes made while
  reopened are read back from the Envers revisions ([section 11](#11-ar1-audited-reopen),
  [ADR-0003](../adr/0003-reopen-audit-trail.md)).

Status: implemented in M2 for sign-off and child edits — version check in the service
(`DailyLogRules.requireVersion`), `OPTIMISTIC_FORCE_INCREMENT` (`findForChildUpdateById`),
triggers, 409 page. The stale-version and lock cases are tested against PostgreSQL in `DailyLogAggregateFlowTests`
(`childChangeBumpsTheLogVersionAndAStaleVersionIsRefused`, `signOffLocksTheLogAndItsChildren`).
The reopen workflow is implemented (M3 week 10); a test with two threads signing off at the
same time is M4.

### 6.3 Shift note → AI draft → supervisor review

```mermaid
sequenceDiagram
    autonumber
    actor M as Site manager
    actor V as Safety supervisor
    participant S as IncidentDraftService
    participant P as IncidentDraftExtractor (port)
    participant X as Adapter (Anthropic or stub)
    participant DB as PostgreSQL

    M->>S: submit shift note
    S->>S: check request cap (429 when exceeded)
    S->>P: extract(shift note)
    P->>X: call provider (no database transaction held)
    X-->>S: structured output (severity, category, crews, immediate action)
    S->>S: validate output
    S->>DB: INSERT incident_draft (PENDING_REVIEW, or NEEDS_MANUAL_INPUT if invalid or timed out)
    V->>S: review, edit and accept draft
    S->>S: @PreAuthorize SAFETY_SUPERVISOR, severity rules (AR2)
    S->>DB: INSERT incident, UPDATE incident_draft (ACCEPTED, incident_id) in one transaction
```

The AI never writes an `Incident`. The provider call happens before the transaction that saves
the draft, so a slow provider does not hold a database connection. Status: planned for M3
week 11 (details will be in ADR-0002 and `docs/ai-evaluation.md`).

## 7. Cross-cutting concerns

| Concern | Design | Status |
|---|---|---|
| **Security** | Form login with users from the database (BCrypt), four roles. URL rules in the `SecurityFilterChain` (public: health check, login, static assets); business authorization with `@PreAuthorize` on service methods, including ownership checks (a site manager only edits logs of the sites they manage). The Thymeleaf security dialect hides actions; it does not replace the service check. REST API secured with the same rules (same session, same CSRF protection). | **Implemented**: screens in M3 week 9 ([section 8](#8-security)); REST API in M3 week 10 ([section 10](#10-rest-api)). |
| **Auditing** | Three layers: `LogReopenRequest` records **why** and who requested/decided; the domain aspect `DomainActionAspect` records **which action** (sign-off, reopen, incident and corrective action changes), by whom and with which outcome in `domain_action`; Hibernate Envers records **what changed** (before/after per revision) on `DailyLog`, `WorkEntry`, `CrewAssignment`. See [ADR-0003](../adr/0003-reopen-audit-trail.md). | Envers **implemented in M2** (`@Audited` on the three entities, `revinfo` with the user, append-only tables V5). Reopen workflow, aspect (append-only `domain_action`, V7) and the before/after history page: **implemented in M3 week 10** ([sections 11](#11-ar1-audited-reopen) and [13](#13-domain-action-aspect)). |
| **Concurrency** | `@Version` on `DailyLog` and `Incident`; `OPTIMISTIC_FORCE_INCREMENT` when a child changes (work entry, crew row, corrective action); `SELECT ... FOR UPDATE` on a reopen request while it is decided; conflict → 409 (section 6.2). | Implemented in M2 for the daily log; M3 week 10 for incidents, corrective actions and reopen decisions. Two-thread sign-off test **planned for M4**. |
| **i18n** | All UI text from `messages.properties` (en) and `messages_hr.properties` (hr); `fallback-to-system-locale: false`, so an unsupported language falls back to English. `WebConfig`: `?lang=hr` is read by a `LocaleChangeInterceptor` and stored in the `lang` cookie by a `CookieLocaleResolver` (default English, one year); an invalid value is ignored. Flash messages and domain errors are message keys, translated when the page renders. | **Implemented in M2** for every existing screen, validation and error page. The user's `preferred_language` is applied at login (M3 week 9). |
| **Error handling** | Domain exceptions thrown by services; `ApiExceptionHandler` (`@RestControllerAdvice`) → `ProblemDetail` for `/api/**`; screens get flash errors or an error page. No stack traces or internal messages in responses (`include-stacktrace: never`, `include-message: never` outside `dev`). | Screens **implemented in M2** (see below); REST `ProblemDetail` **implemented in M3 week 10** ([section 10](#10-rest-api)). |
| **Scheduling** | Two daily `@Scheduled` jobs (overdue corrective actions, unsigned log reminder), protected against double execution by ShedLock (ADR-0004). Each job calls a service method, which holds the transaction. The overdue job is what moves a corrective action from `OPEN` to `OVERDUE`. | **Planned for M4**. |
| **Transactions** | `@Transactional` on services only (`readOnly = true` for queries); `open-in-view=false`. | Implemented (M1/M2). |
| **Health** | `/actuator/health` public, used by Docker and the Render health check; no details exposed. | Implemented (M1). |

### Controller advices (screens)

Two `@ControllerAdvice` classes in `common.web` serve the Thymeleaf screens.

| Advice | Scope | What it does |
|---|---|---|
| `WebExceptionHandler` | `annotations = Controller.class` | Optimistic-lock conflict (stale version from the form, or concurrent commit) → **409** page `error/409` with "changed by someone else" and a reload link. `LogLockedException` → redirect to the log detail with an error flash. `InvalidTransitionException`, `DeletionNotAllowedException` → redirect back (`SafeRedirects`) with the error flash. `NotFoundException` → **404** page. `DataAccessException` whose cause is the `daily_log_locked` trigger message, or any `DataIntegrityViolationException` → **409** page; anything else is re-thrown to the generic error page. |
| `LayoutModelAdvice` | `basePackages = "hr.algebra.sitelog"` | Adds what the layout needs to every model: `currentUser`, `unreadCount`, the newest 10 unread `notifications`, `openIncidentCount`. Skipped for handlers with `@ResponseBody` (every `@RestController`): a JSON response has no layout, so each REST call saves these 4 queries. |

`LayoutModelAdvice` is restricted to the application's own package on purpose: Spring Boot's
`BasicErrorController` (403, 404 for unknown URLs, 500) must render even when the database is the
cause of the error, so the error pages do not query it, and the layout treats those attributes as
optional. The error templates (`error/403.html`, `404`, `409`, `500`, fallback `error.html`) show
translated text only, never a stack trace or exception message.


## 8. Security

Implemented in M3 week 9; the REST API (M3 week 10) goes through the same chain and the same
service rules. Two layers, each with its own job: the **filter chain** decides who
may reach a URL at all, **method security** on the services decides who may perform an
operation. The services are the rule; the URL rules and the hidden buttons are additional.

```mermaid
flowchart LR
    req["HTTP request"] --> chain
    subgraph chain["SecurityFilterChain (SecurityConfig)"]
        direction TB
        csrf["CSRF check on POST"]
        url["URL rules<br/>public / /admin/** ADMIN /<br/>crew forms + POSTs ADMIN /<br/>everything else authenticated"]
        csrf --> url
    end
    chain -->|"anonymous on a protected URL"| login["302 /login<br/>(/api/**: 401 ProblemDetail)"]
    chain --> ctrl["@Controller<br/>(binds form, no rules)"]
    ctrl --> proxy
    subgraph proxy["Service proxy"]
        direction TB
        pre["@PreAuthorize<br/>hasRole(...) and @siteAccess..."]
        tx["@Transactional method"]
        pre --> tx
    end
    pre -->|"refused: AccessDeniedException"| forbidden["403 page<br/>(user's language;<br/>/api/**: 403 ProblemDetail)"]
```

### URL level: `SecurityConfig`

| Rule | Paths | Why at URL level |
|---|---|---|
| Public | `/login`, `/error`, `/actuator/health`, `/css/**`, `/js/**`, `/img/**`, `/favicon.ico` | Needed before sign-in (login page and its assets) and by the Docker / Render health check. |
| Public | `GET /api/v1/csrf` | A REST client needs a CSRF token before it can `POST /login` ([section 10](#10-rest-api)). The token is bound to the caller's own session, so handing it out reveals nothing. |
| `ADMIN` | `/admin/**` (user administration) | Second line of defence next to `@PreAuthorize("hasRole('ADMIN')")` on `UserService`. |
| `ADMIN` | `GET /crews/new`, `GET /crews/*/edit`, every `POST` under `/crews` | Refused before form binding and validation, so a non-admin never sees the crew form, not even re-rendered with errors. The crew list stays readable. |
| Signed in | everything else, including `/api/**`, `/v3/api-docs` and `/swagger-ui.html` | Every page shows site data; no anonymous reading. |

### Method level: `@PreAuthorize` on the services

`MethodSecurityConfig` carries `@EnableMethodSecurity`. Every service class has
`@PreAuthorize("isAuthenticated()")` for its read methods, and each write method narrows it:

| Service methods | Expression |
|---|---|
| Sites (create, update, delete, manager options), crews (create, update, delete), all of `UserService` except the profile | `hasRole('ADMIN')` |
| `DailyLogService.create`, `SiteService.getSummaryForNewLog` (the *New daily log* form) | `hasRole('SITE_MANAGER') and @siteAccess.canManageSite(#siteId)` |
| Daily log update, sign-off, delete, edit form; work entry and crew row create / update / delete | `hasRole('SITE_MANAGER') and @siteAccess.canManageLog(#dailyLogId)` (or `#id`) |
| Incident: report; *Report incident* form options | `hasRole('SITE_MANAGER') and @siteAccess.canManageSite(#siteId)` |
| Incident: edit, edit form | `hasRole('SAFETY_SUPERVISOR') or (hasRole('SITE_MANAGER') and @incidentAccess.isOnOwnSite(#id))`; the service then allows a site manager only while `REPORTED` |
| Incident: start review, sign-off, close | `hasRole('SAFETY_SUPERVISOR') or (hasRole('SITE_MANAGER') and @incidentAccess.isLowSeverityOnOwnSite(#id))`; the service repeats the severity check on the loaded record |
| Incident: delete | `hasRole('SITE_MANAGER') and @incidentAccess.isOnOwnSite(#id)` |
| Corrective action: create, edit, delete, form | `hasRole('SAFETY_SUPERVISOR')` |
| Corrective action: mark as done | `hasRole('SAFETY_SUPERVISOR') or @incidentAccess.isAssignee(#actionId)` |
| Reopen: request | `hasRole('SITE_MANAGER') and @siteAccess.canManageLog(#dailyLogId)` |
| Reopen: approve, reject, pending queue | `hasRole('SAFETY_SUPERVISOR')` |
| Profile, preferred language, password change | `isAuthenticated()` |

- **Why on the services:** the REST API (M3 week 10) and any future caller go through the same methods,
  so one rule protects screens and API. A refused call throws `AccessDeniedException` before the
  method body and before the transaction opens.
- **`IncidentAccess`** (`incident.service`, bean name `incidentAccess`) does the same for incidents:
  `isOnOwnSite`, `isLowSeverityOnOwnSite` (own site **and** `LOW` / `MEDIUM`), `isAssignee` (the
  signed-in user is the assignee of the corrective action).
- **`SiteAccess`** (`site.service`, bean name `siteAccess`) answers only "is the signed-in user
  the manager of this site / of this log's site?" (`existsByIdAndManager_Id`,
  `existsByIdAndSite_Manager_Id`). The role is checked in the expression itself. An id that does
  not exist answers `true`, so the service throws its not-found exception and the user sees 404
  instead of a misleading 403; nothing is revealed, since every signed-in user may read every site.
- **Why a separate `MethodSecurityConfig`:** the `@WebMvcTest` slices import `SecurityConfig` and
  mock the services. Keeping `@EnableMethodSecurity` out of `SecurityConfig` keeps those slices
  free of method-security proxies; the rules are tested on the real services instead
  (`MethodSecurityTests`, `RoleAccessMatrixTests`).
- **In the templates** the Thymeleaf security dialect hides actions the user may not perform:
  `sec:authorize="hasRole('ADMIN')"` for admin actions, and for the own-site rule a flag computed
  once per page with `th:with` (`#authorization.expression('hasRole(''SITE_MANAGER'')') and
  @siteAccess.canManageSite(site.id)`, or `canManageLog(log.id)` on the log detail). This is
  cosmetic only.

The resulting permission matrix per role is in
[Domain model — Permissions](./domain-model.md#permissions).

### Login, session and logout

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant F as SecurityFilterChain
    participant P as DaoAuthenticationProvider
    participant D as AppUserDetailsService
    participant H as PreferredLanguageLoginSuccessHandler

    U->>F: POST /login (email, password, _csrf)
    F->>P: authenticate
    P->>D: loadUserByUsername(email)
    D-->>P: AppUserPrincipal (id, role, language, active, BCrypt hash)
    alt inactive or wrong password
        P-->>U: 302 /login?error (same message for both)
    else valid
        P->>P: erase hash from the principal, new session id
        F->>H: onAuthenticationSuccess
        H-->>U: lang cookie = preferred language, 302 to the saved page or /sites
    end
```

| Topic | Implementation |
|---|---|
| Users | `AppUserDetailsService` looks the e-mail up case-insensitively in `app_user` with its role (one query). `AppUserPrincipal` is a copy of the row, not the JPA entity (the session outlives the persistence context); authority `ROLE_<role>`. |
| Passwords | `BCryptPasswordEncoder` (strength 10), the same algorithm as the hashes in the `V4` seed. Policy: at least 12 characters, at most 72 bytes (BCrypt's input limit); a change requires the current password and a confirmation. |
| Inactive user | `isEnabled()` is the `active` flag; the provider refuses the login before checking the password. The page shows the same message as for a wrong password, so it does not reveal which e-mails exist. |
| Language at login | `PreferredLanguageLoginSuccessHandler` writes the user's `preferred_language` through the application's `LocaleResolver` (the `lang` cookie of section 7), then continues to the saved request or `/sites`. |
| Current user | `SecurityContextCurrentUserProvider`: `currentUserId()` reads the id from the principal without a query; `findCurrentUser()` reloads name and role, so a name changed on the profile shows at once. The Envers `AuditRevisionListener` records the same user. |
| CSRF | On (Spring Security default) for screens and the REST API alike. Thymeleaf forms with `th:action` get the hidden `_csrf` field automatically; REST clients send the token from `GET /api/v1/csrf` in the `X-CSRF-TOKEN` header. A POST, PUT or DELETE without a valid token answers 403. The token stays in the session (`HttpSessionCsrfTokenRepository`, the default). |
| Session | HTTP session with Spring Security's default session-fixation protection (new session id at login). In `prod` the cookie is `Secure`, `HttpOnly`, `SameSite=Lax`. |
| Logout | `POST /logout` with the CSRF token (a GET link cannot log anyone out); invalidates the session, deletes `JSESSIONID`, redirects to `/login?logout`. |
| Refused calls | Screens: signed-in user → 403 page in the user's language; anonymous → redirect to `/login`. `/api/**`: 403 and 401 as `ProblemDetail` JSON. One entry point and one access-denied handler decide by path (`ApiSecurityHandlers`), so a screen request is never answered with JSON. Tested over real HTTP in `ForbiddenCallsHttpTests` (database unchanged after a refused POST, missing or forged CSRF token, inactive user, logout, session fixation) and `RestApiHttpTests` (401 on the API, redirect on the screens, 403 without the CSRF header). |

## 9. Deployment

Deployment setup and first deployment on Render (https://sitelog-aq2e.onrender.com) in M3 week 9. Full
procedure, environment variables, redeploy and the wake-up steps before a demo:
[Deployment (Render + Supabase)](../operations/deployment.md).

```mermaid
flowchart LR
    dev["git push to main"] --> gh["GitHub repository"]
    gh -->|"auto-deploy on commit<br/>(render.yaml Blueprint)"| build["Render build<br/>multi-stage Dockerfile:<br/>JDK 25 build, JRE 25 runtime"]
    build --> app["SiteLog container<br/>profile prod, non-root,<br/>health check /actuator/health"]
    browser["Browser"] -->|"HTTPS"| proxy["Render proxy<br/>(TLS ends here)"]
    proxy -->|"HTTP + X-Forwarded-*"| app
    app -->|"JDBC, HikariCP (5),<br/>sslmode=require"| pooler["Supabase Supavisor<br/>session pooler :5432"]
    pooler --> pg[("Supabase PostgreSQL 17<br/>Flyway migrates on boot")]
```

| Decision | Choice | Reason |
|---|---|---|
| Image | Multi-stage `Dockerfile` (`eclipse-temurin:25-jdk-alpine` build, `25-jre-alpine` runtime), not buildpacks | The same image runs locally (`docker compose up`) and on Render; the build is explicit and reviewable; the runtime has no JDK or Maven and runs as a non-root user. |
| Service definition | `render.yaml` Blueprint (free plan, Frankfurt, auto-deploy on commit, health check) | Versioned with the code; secrets are only names (`sync: false`), values are entered in the Render dashboard. |
| Database connection | Supavisor **session** pooler, port 5432, TLS required | The direct connection is IPv6 only, which Render does not reach; the transaction pooler (6543) breaks server-side prepared statements and session state. In session mode each Hikari connection holds one pooler connection, hence the small pool (README, *HikariCP configuration*). |
| Configuration | `application-prod.yml` reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` with no defaults | A missing variable fails the startup instead of connecting somewhere else; Render keeps the previous deploy. |
| Exposure | Only `/actuator/health` (no details); no stack trace or message in error responses | Nothing about the configuration leaks through the deployed instance. |
| Startup on 0.1 CPU | `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1 -XX:+UseSerialGC` | Measured locally with the free plan's limits: 349 s → 168 s to start, at the cost of peak throughput the application does not need. |

## 10. REST API

Implemented in M3 week 10, package `hr.algebra.sitelog.api`. The API is a second entry point to
the **same services** as the screens: the controllers map JSON records to the service commands
and the service view records back to response records. No entity leaves the service, no rule
lives in a controller, and `@PreAuthorize` protects both entry points with one set of rules.

### Resources

Base path `/api/v1`. The full list with request and response schemas is the OpenAPI document.

| Resource | Endpoints |
|---|---|
| Sites | `GET /sites` (paged), `GET /sites/{id}`, `POST /sites`, `PUT /sites/{id}`, `DELETE /sites/{id}` |
| Daily logs | `GET /sites/{siteId}/daily-logs` (paged), `POST /sites/{siteId}/daily-logs`, `GET` / `PUT` / `DELETE /daily-logs/{id}`, `POST /daily-logs/{id}/sign-off` |
| Work entries | `GET` / `POST /daily-logs/{logId}/work-entries`, `GET` / `PUT` / `DELETE .../work-entries/{id}` |
| Crew assignments | `GET` / `POST /daily-logs/{logId}/work-entries/{entryId}/crew-assignments`, `GET` / `PUT` / `DELETE .../crew-assignments/{id}` |
| Incidents | `GET /incidents` and `GET /sites/{siteId}/incidents` (paged, filter by status and severity), `POST /sites/{siteId}/incidents`, `GET` / `PUT` / `DELETE /incidents/{id}`; workflow steps `POST /incidents/{id}/start-review`, `/sign-off`, `/close` |
| Corrective actions | `GET` / `POST /incidents/{incidentId}/corrective-actions`, `GET` / `PUT` / `DELETE .../corrective-actions/{id}`, `POST .../corrective-actions/{id}/done` |
| Reopen requests | `GET` / `POST /daily-logs/{id}/reopen-requests`, `GET .../reopen-requests/{requestId}`, `POST .../{requestId}/approve`, `POST .../{requestId}/reject`; supervisor queue `GET /reopen-requests` |
| Revisions | `GET /daily-logs/{id}/revisions` (Envers history with before/after) |
| CSRF token | `GET /csrf` (public) |

Workflow steps (sign-off, start review, close, approve, reject, done) are `POST` on a
sub-resource, not a `PUT` of the `status` field: each step has its own rule and its own
permission, and a client cannot jump to a state the workflow does not allow.

### Conventions

| Topic | Rule |
|---|---|
| Create | `201 Created`, `Location` header with the new resource's URL, response record in the body. |
| Update, workflow step | `200 OK` with the updated resource. |
| Delete | `204 No Content`. |
| Paging | `?page=&size=&sort=property,dir` on the collections; `size` at most 100; sortable properties are whitelisted per resource (for example `code`, `name`, `startDate` for sites); an unknown sort property answers **400** (the screens ignore it instead), so a client learns about its typo. Response: `PageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`, `sort`). |
| Optimistic lock | The `version` the client last read travels **in the request body** (query parameter `?version=` on `DELETE`), the same contract as the hidden field of the forms. A stale version or a concurrent commit answers **409**. `If-Match` / `412` was not used: the screens and the API share one service signature with an explicit version, and 409 is what the forms already answer. |
| Errors | RFC 9457 `ProblemDetail` (`application/problem+json`) for every error, see below. |
| Documentation | springdoc-openapi 3.2.0: OpenAPI 3.1 document at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, only `hr.algebra.sitelog.api` and `/api/**` documented. Both need a signed-in user. Every operation lists its status codes with example `ProblemDetail` bodies. |

### Error responses

`ApiExceptionHandler` (`@RestControllerAdvice(basePackages = "hr.algebra.sitelog.api")`,
`@Order(HIGHEST_PRECEDENCE)` so it wins over `WebExceptionHandler`, which matches every
`@Controller` and therefore also `@RestController`). Every body has `type`, `title`, `status`,
`detail` (in the request's language), `instance` (the path) and `code` (the stable message
key); field errors add `errors[]` with `field`, `code`, `message`.

| Status | When |
|---|---|
| 400 | Bean Validation of the request body (`errors[]` per field), malformed JSON, wrong parameter type, missing `version`, unknown sort property. |
| 401 | Not signed in (written by the filter chain, `ApiAuthenticationEntryPoint`). |
| 403 | `@PreAuthorize` refused the call, or the CSRF token is missing or wrong (`ApiAccessDeniedHandler`). |
| 404 | Record not found. |
| 409 | Stale `version` or concurrent commit; signed-off (locked) log; workflow step not allowed in the current state; delete refused; database constraint or lock trigger. |
| 422 | Well-formed request refused by a business rule on a field (duplicate code, future date, inactive crew, past due date); for a `HIGH` / `CRITICAL` incident, `MissingSeverityFieldsException` with one entry in `errors[]` per missing AR2 field. |
| 500 | Anything else, without internals in the body. |

Example, `POST /api/v1/sites/3/incidents` with severity `HIGH` and two AR2 fields missing:

```json
{"type":"about:blank","title":"Unprocessable Content","status":422,
 "detail":"High and Critical incidents need root cause, witnesses, medical treatment and authority notification.",
 "instance":"/api/v1/sites/3/incidents","code":"incident.flash.missingFields",
 "errors":[{"field":"rootCause","code":"validation.incident.rootCause.required","message":"Root cause is required"},
           {"field":"authorityNotified","code":"validation.incident.authorityNotified.required","message":"Select Yes or No"}]}
```

### Authentication for REST clients

Decision of 2026-11-24: the API uses the **same session and the same CSRF protection** as the
screens, with no exception in the filter chain.

```mermaid
sequenceDiagram
    autonumber
    participant C as REST client (cookie jar)
    participant A as SiteLog
    C->>A: GET /api/v1/csrf
    A-->>C: 200 {headerName: X-CSRF-TOKEN, parameterName: _csrf, token}, anonymous session cookie
    C->>A: POST /login (form: email, password, _csrf)
    A-->>C: 302, new JSESSIONID (session fixation protection, new token)
    C->>A: GET /api/v1/csrf
    A-->>C: 200 {token} of the signed-in session
    C->>A: POST /api/v1/... with header X-CSRF-TOKEN and JSESSIONID
    A-->>C: 201 / 200 / ProblemDetail
```

- **Why not stateless (tokens, CSRF off for `/api/**`):** the screens already have a secure
  session login; a second authentication mechanism would be a second thing to secure and to
  test, for an API whose clients are Swagger UI, `curl` and the tests.
- **Why not `CookieCsrfTokenRepository`** (`XSRF-TOKEN` cookie readable by JavaScript): it would
  change the token storage of every screen and expose the token to any script on the page, only
  to save the client one `GET`.
- **OpenAPI:** two security schemes, `session` (cookie `JSESSIONID`) and `csrf` (header
  `X-CSRF-TOKEN`), the latter required on every `POST`, `PUT` and `DELETE`. In Swagger UI the
  browser session is reused, and the token from `GET /api/v1/csrf` is pasted once into
  *Authorize* (`persist-authorization: true` keeps it across reloads).

Tests: one `@WebMvcTest` class per API controller (status codes, `Location`, `ProblemDetail`
bodies, 403 per role), `ApiExceptionHandlerTests`, and `RestApiHttpTests` on a real Tomcat with
PostgreSQL: CSRF endpoint, 401 vs. login redirect, admin site CRUD (201 → 422 duplicate → 204 →
404), 403 for a site manager, 403 without the CSRF header, stale version 409, OpenAPI only for
signed-in users, and the `HIGH` incident flow below.

## 11. AR1: audited reopen

Implemented in M3 week 10 (`LogReopenService`, `LogReopenController`, `ReopenApiController`,
`DailyLogHistoryService`). Rules in [Domain model — Reopen requests](./domain-model.md#reopen-requests).

```mermaid
sequenceDiagram
    autonumber
    actor M as Site manager (own site)
    actor V as Safety supervisor
    participant X as DomainActionAspect
    participant S as LogReopenService
    participant DB as PostgreSQL

    M->>X: request(logId, version, reason)
    X->>S: @PreAuthorize SITE_MANAGER and @siteAccess.canManageLog
    S->>DB: log SIGNED_OFF, version unchanged, no PENDING request
    S->>DB: INSERT log_reopen_request (PENDING), INSERT notification per active supervisor
    X->>DB: INSERT domain_action LOG_REOPEN_REQUESTED, SUCCESS
    V->>X: approve(logId, requestId, optional note)
    X->>S: @PreAuthorize SAFETY_SUPERVISOR
    S->>DB: SELECT log_reopen_request ... FOR UPDATE (still PENDING?)
    S->>DB: UPDATE daily_log SET status = REOPENED, revision = revision + 1 (version + 1)
    DB->>DB: V3 trigger: the one change allowed on a SIGNED_OFF row
    S->>DB: UPDATE request APPROVED, approved_by, decided_at, decision_note
    Note over S,DB: commit; Envers writes daily_log_aud + revinfo with the supervisor
    X->>DB: INSERT domain_action LOG_REOPEN_APPROVED, SUCCESS
    M->>S: edits (DailyLogService, audited by Envers), then sign-off again
```

- **Two supervisors at once.** The request row is read with `PESSIMISTIC_WRITE`
  (`SELECT ... FOR UPDATE`, `LogReopenRequestRepository.findForDecisionById`). The second
  decision waits for the first commit, then finds the request decided and answers **409**
  (`reopen.flash.alreadyDecided`). Without the lock, an approve and a reject could both read
  `PENDING` and both write.
- **A concurrent change of the log** during the approval conflicts through `@Version` (409);
  nothing is decided.
- **Reject** needs a reason (`decision_note`, V7 `CHECK`); the log stays `SIGNED_OFF` and is not
  touched.
- **History page** `/daily-logs/{id}/history` (and `GET /api/v1/daily-logs/{id}/revisions`):
  `DailyLogHistoryService` reads the Envers revisions of the log, its work entries and their
  crew rows with `AuditReader` (three audit queries; deleted children are found through
  `relatedId`), compares each revision with the previous snapshot of the same row and lists
  before → after per field, grouped by revision with user and time. The API returns the values
  as typed JSON; the screen formats them in the reader's language (date and time patterns,
  hours as a localised number: `6.50` → `6,5` in Croatian, enum values through their message
  keys). The reopen requests and the recorded domain actions are shown next to the revisions.

Test: `ReopenAndIncidentFlowTests` (Testcontainers): sign-off → request → approve → edit →
sign-off again, with the reopen request, the Envers revisions and the `domain_action` rows
checked; rejection needs a reason and keeps the log signed off; only one pending request per log.

## 12. AR2: incident severity workflow

Implemented in M3 week 10 (`IncidentService`, `CorrectiveActionService`, `IncidentAccess`,
`IncidentRules`; screens `incident/*`, `correctiveaction/form`; API `IncidentApiController`,
`CorrectiveActionApiController`). State machine and rules in
[Domain model — Incidents and corrective actions](./domain-model.md#incidents-and-corrective-actions).

| Check | Where |
|---|---|
| `HIGH` / `CRITICAL` need root cause, witnesses, medical treatment, authority notified | `IncidentRules.missingSeverityFields`, called by the service on report, on every edit and again on sign-off → `MissingSeverityFieldsException` (screen: field errors; API: 422 with `errors[]`). The form uses a validation group for the same fields (user experience only). `ck_incident_severity_fields` (V3) is the database safety net. |
| `HIGH` / `CRITICAL` signed off only by a safety supervisor | `@PreAuthorize` with `@incidentAccess.isLowSeverityOnOwnSite(#id)` for the site manager, **and** `loadForTransition` repeats the check on the loaded record, so the rule does not depend on the expression alone. |
| A site manager cannot downgrade a `HIGH` / `CRITICAL` incident to sign it himself | `IncidentService.update` (`validation.incident.severity.downgrade`). |
| Close only when every corrective action is `DONE` | `IncidentService.close` → 409. |
| Concurrent changes | `Incident.@Version`; corrective action changes load the incident with `OPTIMISTIC_FORCE_INCREMENT` (same pattern as work entries). |

Screens: the detail page renders only the next valid step for a role that may do it; *Sign off*
stays visible but disabled while AR2 fields are missing, with the list of missing fields. The
timeline card shows the recorded domain actions of the incident.

Test over real HTTP (`RestApiHttpTests.highSeverityIncidentWorkflow`): report `HIGH` without
the fields → 422; with the fields → 201; supervisor starts review → 200; site manager signs off
→ 403; supervisor signs off → 200; corrective action → 201; close with the action open → 409;
action done → 200; close → 200.

## 13. Domain action aspect

**What the aspect intercepts:** every Spring bean method annotated with `@AuditedAction`
(pointcut `@annotation(hr.algebra.sitelog.audit.aspect.AuditedAction)`, an `@Around` advice in
`DomainActionAspect`). Today these 14 service methods:

| Area | Methods (`DomainActionType`) |
|---|---|
| Daily log | `DailyLogService.signOff` (`LOG_SIGNED_OFF`) |
| Reopen | `LogReopenService.request`, `approve`, `reject` (`LOG_REOPEN_REQUESTED`, `_APPROVED`, `_REJECTED`) |
| Incident | `IncidentService.report`, `update`, `startReview`, `signOff`, `close`, `delete` (`INCIDENT_REPORTED`, `_UPDATED`, `_REVIEW_STARTED`, `_SIGNED_OFF`, `_CLOSED`, `_DELETED`) |
| Corrective action | `CorrectiveActionService.create`, `update`, `markDone`, `delete` (`CORRECTIVE_ACTION_ADDED`, `_UPDATED`, `_DONE`, `_DELETED`) |

For each call it records the action, the record id (first `Long` argument, or the returned id
for `report`), the signed-in user (id and e-mail, from the security context without a query),
the time, the duration and the **outcome**: `SUCCESS`, `REJECTED` (a `DomainException`, with
its message key), `DENIED` (`AccessDeniedException` from `@PreAuthorize`) or `FAILED` (anything
else, class name only). It writes one structured line to the `sitelog.audit` logger and one row
to `domain_action` (V7, append-only). The exception is always re-thrown unchanged: the aspect
observes, it never changes the result.

```mermaid
flowchart LR
    call["Controller / API call"] --> aspect
    subgraph proxy["Service proxy (CGLIB), advice order"]
        direction LR
        aspect["DomainActionAspect<br/>@Order(HIGHEST_PRECEDENCE)"] --> sec["@PreAuthorize"] --> tx["@Transactional"] --> body["service method"]
    end
    aspect -->|"after the call returns or throws"| row[("domain_action<br/>+ sitelog.audit log")]
```

- **Why outermost:** outside method security, the aspect sees refused calls (`DENIED`);
  outside the transaction, it sees failures that only happen at commit (optimistic lock
  conflict, trigger), and a `SUCCESS` row is written only after the business transaction has
  committed. `DomainActionRecorder` writes the row in its own transaction (or joins an outer
  one, then rolls back with it).
- **Recording never breaks the call:** the log line is written first; a failure to store the
  row is logged and swallowed.
- **Annotation read by reflection**, not bound as an advice parameter: binding
  (`@annotation(audited)`) needs Spring's `ExposeInvocationInterceptor` to run first, which is
  not the case for the outermost advice ("JoinPointMatch was NOT bound").
- **Only public methods called through the proxy** are intercepted; a call from inside the same
  class is not (Spring AOP limitation, documented on the annotation).
- **Where it is shown:** the incident timeline and the daily log history page list the
  `SUCCESS` rows of the record (`DomainActionService.timeline`). `DENIED`, `REJECTED` and
  `FAILED` rows stay in the table and the log for investigation.
- **Third layer of the audit trail** ([ADR-0003](../adr/0003-reopen-audit-trail.md), decision 6):
  `log_reopen_request` says why and who decided, Envers says what changed, `domain_action` says
  which action happened, by whom and how it ended.

Tests: `DomainActionAspectTests` (record id from argument or return value, `REJECTED` with
message key, `DENIED`, `FAILED` with class only, no annotation → not intercepted, no user,
storage failure does not break the call); `ReopenAndIncidentFlowTests` checks the rows against
PostgreSQL.

## 14. Notifications

`NotificationPublisher` (M3 week 10) creates notifications for other services; it has no
`@PreAuthorize` (the caller's rule already decided that the event may happen, and the M4 jobs
have no signed-in user) and joins the caller's transaction, so a rolled-back change sends
nothing.

| Event | Producer | Recipients |
|---|---|---|
| `REOPEN_REQUESTED` | `LogReopenService.request` | every **active** safety supervisor; link to the log |
| `HIGH_SEVERITY_INCIDENT` | `IncidentService.report`, and `update` when the severity is raised to `HIGH` / `CRITICAL` | every **active** safety supervisor; link to the incident |
| `CORRECTIVE_ACTION_OVERDUE`, `DAILY_LOG_UNSIGNED` | scheduled jobs | planned for M4 |

A notification stores a message key and its arguments (`title` = `notification.type.<TYPE>`,
`body_key` + `body_args`, V7), never finished text; the bell and the list page render it in the
reader's language. Only the recipient can see or mark it as read.
