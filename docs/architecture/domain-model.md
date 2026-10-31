---
title: Domain model
sidebar_position: 1
description: Entities, relations, roles and state machines of SiteLog.
---

# Domain model

:::info Status
Model accepted on 2026-10-31 (milestone M1). The tables are created by Flyway migrations in
milestone M2; when they land, this page is updated to match the migrations exactly
(column names, types, constraints). The reasoning behind the mapping is in
[ADR-0001](../adr/0001-persistence-model.md).
:::

## Aggregates

| Aggregate root | Owns (cascade, orphan removal) | References (no cascade) |
|---|---|---|
| `DailyLog` | `WorkEntry` → `CrewAssignment`, `LogReopenRequest` | `Site`, `Crew`, `AppUser` |
| `Incident` | `CorrectiveAction` | `Site`, `Crew` (affected crews), `DailyLog` (optional), `AppUser` |
| `Site`, `Crew`, `AppUser` | — | `Role` |
| `IncidentDraft` | — | `Site`, `AppUser`, `Incident` (after acceptance) |

The assignment's chain *Site → DailyLog → WorkEntry → Crew* and *Site → Incident →
CorrectiveAction* is covered by these aggregates. `Site` is the parent in the UI and in the
REST URLs; `DailyLog` and `Incident` carry the `@Version` column and are the units that lock.

## Entity diagram — logs and incidents

Required relation types:

- **One-to-many:** `Site` → `DailyLog` → `WorkEntry`; `Site` → `Incident` → `CorrectiveAction`.
- **Many-to-many with an association entity carrying attributes:** `WorkEntry` ↔ `Crew`
  through `CrewAssignment` (`hours_worked`, `worker_count`, `task`).
- **Many-to-many without attributes:** `Incident` ↔ `Crew` (affected crews), plain join table.
- **Optional relations:** `Incident` → `DailyLog` (0..1), `CorrectiveAction` → `AppUser`
  assignee (0..1).

```mermaid
erDiagram
    SITE ||--o{ DAILY_LOG : "has"
    DAILY_LOG ||--o{ WORK_ENTRY : "contains"
    WORK_ENTRY ||--o{ CREW_ASSIGNMENT : "staffed by"
    CREW ||--o{ CREW_ASSIGNMENT : "assigned in"
    SITE ||--o{ INCIDENT : "has"
    INCIDENT ||--o{ CORRECTIVE_ACTION : "requires"
    INCIDENT ||--o{ INCIDENT_AFFECTED_CREW : "affects"
    CREW ||--o{ INCIDENT_AFFECTED_CREW : "affected by"
    DAILY_LOG |o--o{ INCIDENT : "optionally linked to"
    APP_USER |o--o{ CORRECTIVE_ACTION : "optional assignee"

    SITE {
        bigint id PK
        varchar code UK
        varchar name
        varchar address
        date start_date
        date end_date "nullable"
        bigint manager_id FK "AppUser, SITE_MANAGER"
    }
    DAILY_LOG {
        bigint id PK
        bigint site_id FK
        date log_date "unique with site_id"
        varchar status "DRAFT, SIGNED_OFF, REOPENED"
        int revision "incremented on each approved reopen"
        varchar weather
        text notes
        bigint signed_off_by FK "AppUser, nullable"
        timestamptz signed_off_at "nullable"
        bigint version "optimistic lock (@Version)"
    }
    WORK_ENTRY {
        bigint id PK
        bigint daily_log_id FK
        varchar description
        varchar location
        time start_time
        time end_time
    }
    CREW_ASSIGNMENT {
        bigint id PK
        bigint work_entry_id FK "unique with crew_id"
        bigint crew_id FK
        numeric hours_worked
        int worker_count
        varchar task
    }
    CREW {
        bigint id PK
        varchar name UK
        varchar company
        varchar trade
        boolean active
    }
    INCIDENT {
        bigint id PK
        bigint site_id FK
        bigint daily_log_id FK "nullable (optional relation)"
        timestamptz occurred_at
        varchar title
        text description
        varchar severity "LOW, MEDIUM, HIGH, CRITICAL"
        varchar category
        varchar status "REPORTED, UNDER_REVIEW, SIGNED_OFF, CLOSED"
        text root_cause "required for HIGH, CRITICAL"
        text witnesses "required for HIGH, CRITICAL"
        text medical_treatment "required for HIGH, CRITICAL"
        boolean authority_notified "required for HIGH, CRITICAL"
        bigint reported_by FK "AppUser"
        bigint signed_off_by FK "AppUser, nullable"
        timestamptz signed_off_at "nullable"
        bigint version "optimistic lock (@Version)"
    }
    INCIDENT_AFFECTED_CREW {
        bigint incident_id PK, FK
        bigint crew_id PK, FK
    }
    CORRECTIVE_ACTION {
        bigint id PK
        bigint incident_id FK
        varchar description
        bigint assignee_id FK "AppUser, nullable (optional relation)"
        date due_date
        varchar status "OPEN, DONE, OVERDUE"
        timestamptz completed_at "nullable"
    }
    APP_USER {
        bigint id PK
        varchar email UK
    }
```

## Entity diagram — users, reopen requests and AI drafts

```mermaid
erDiagram
    ROLE ||--o{ APP_USER : "granted to"
    APP_USER ||--o{ SITE : "manages"
    DAILY_LOG ||--o{ LOG_REOPEN_REQUEST : "reopened through"
    APP_USER ||--o{ LOG_REOPEN_REQUEST : "requests"
    APP_USER |o--o{ LOG_REOPEN_REQUEST : "decides"
    SITE ||--o{ INCIDENT_DRAFT : "for"
    APP_USER ||--o{ INCIDENT_DRAFT : "writes shift note"
    APP_USER |o--o{ INCIDENT_DRAFT : "reviews"
    INCIDENT_DRAFT |o--o| INCIDENT : "becomes, after sign-off"

    ROLE {
        bigint id PK
        varchar name UK "ADMIN, SITE_MANAGER, SAFETY_SUPERVISOR, INSPECTOR"
    }
    APP_USER {
        bigint id PK
        varchar email UK "login name"
        varchar password_hash "BCrypt"
        varchar full_name
        varchar preferred_language "en or hr, applied at login"
        boolean active "inactive users cannot log in"
        bigint role_id FK
    }
    LOG_REOPEN_REQUEST {
        bigint id PK
        bigint daily_log_id FK
        text reason "mandatory"
        bigint requested_by FK "AppUser"
        bigint approved_by FK "AppUser, nullable until decided"
        timestamptz requested_at
        timestamptz decided_at "nullable"
        varchar status "PENDING, APPROVED, REJECTED"
    }
    INCIDENT_DRAFT {
        bigint id PK
        bigint site_id FK
        text shift_note "free text written by the site manager"
        varchar severity "suggested by the AI"
        varchar category "suggested by the AI"
        text affected_crews "suggested by the AI"
        text immediate_action "suggested by the AI"
        varchar status "PENDING_REVIEW, NEEDS_MANUAL_INPUT, ACCEPTED, REJECTED"
        bigint created_by FK "AppUser"
        bigint reviewed_by FK "AppUser, nullable"
        bigint incident_id FK "nullable, unique: set on acceptance"
        timestamptz created_at
        timestamptz reviewed_at "nullable"
    }
    SITE {
        bigint id PK
    }
    DAILY_LOG {
        bigint id PK
    }
    INCIDENT {
        bigint id PK
    }
```

Entities repeated with only `id` are fully described in the first diagram.

### Mapping notes

- Every association is `FetchType.LAZY`, including `@ManyToOne` (whose JPA default is
  `EAGER`). Screens that need children load them with a fetch join or `@EntityGraph`.
- `CrewAssignment` is a full entity with its own `id` and a unique constraint on
  `(work_entry_id, crew_id)`, not a `@ManyToMany`, because it carries attributes.
- `Incident` ↔ `Crew` is a plain `@ManyToMany` over `incident_affected_crew`.
- `DailyLog.version` and `Incident.version` are `@Version` columns: a stale update fails
  and the API answers **409 Conflict**. Changing only a child (`WorkEntry`,
  `CrewAssignment`, `CorrectiveAction`) does not increment the root's version in Hibernate,
  so services that change a child load the root with `LockModeType.OPTIMISTIC_FORCE_INCREMENT`
  (see [ADR-0001](../adr/0001-persistence-model.md), decision 6).
- A signed-off log is protected in the database by triggers on `daily_log`, `work_entry` and
  `crew_assignment`. The child triggers check the **parent** log's status: `INSERT`, `UPDATE`
  and `DELETE` on `work_entry` / `crew_assignment` fail when the owning `daily_log.status` is
  `SIGNED_OFF`, and are allowed in `DRAFT` and `REOPENED`. Planned for the M2 migrations.
- An `IncidentDraft` never writes to `incident` by itself; an `Incident` is created only
  when a supervisor accepts the draft (see [Incident draft](#incident-draft-ai-output)).

## Decision — reopen audit trail (AR1)

Advanced requirement 1 asks for an audited reopen: while a log is `REOPENED`, each change must
record the value before and after, who changed it and when. `LOG_REOPEN_REQUEST` records the
reopen itself (reason, requester, approver), not the field changes. Two options:

**(a) Hibernate Envers.** `@Audited` on `DailyLog`, `WorkEntry` and `CrewAssignment`; Envers
writes `*_AUD` tables, and a custom `REVINFO` entity (with a `RevisionListener`) adds the user.

- Pros: little code; whole-entity history; queryable with `AuditReader`; standard and easy to
  name in the defence.
- Cons: audits every change, also in `DRAFT`, unless filtered; `*_AUD` tables still need
  Flyway migrations (`ddl-auto=validate`); "before/after" per field must be computed by
  comparing revisions; one more dependency.

**(b) Own table `DAILY_LOG_AUDIT`** (proposal, not in the diagrams above):

```text
DAILY_LOG_AUDIT (proposed)
  id            bigint PK
  daily_log_id  bigint FK -> DAILY_LOG
  revision      int        -- DAILY_LOG.revision at the time of the change
  entity        varchar    -- DAILY_LOG, WORK_ENTRY, CREW_ASSIGNMENT
  entity_id     bigint
  field         varchar
  old_value     text
  new_value     text
  changed_by    bigint FK -> APP_USER
  changed_at    timestamptz
```

- Pros: written only for edits in `REOPENED`; one row per field gives before/after directly;
  simple to show on screen and in the report; full control in plain SQL.
- Cons: the service must write each row (risk of missing a field); more code and tests; values
  stored as text.

**Decision (2026-10-31): (a) Hibernate Envers**, recorded in ADR-0003 (M2).

- Reopen edits touch the children (`WorkEntry`, `CrewAssignment`), including deletions. Envers
  records inserts, updates and deletes on every `@Audited` entity without per-field service code,
  which is where a hand-written trail (option b) would most likely miss a change.
- Before/after is obtained by comparing two revisions with `AuditReader`; a `RevisionListener`
  stores the authenticated user on the custom revision entity.
- The `*_AUD` tables and the revision table are created by Flyway migrations, so the audit schema
  is explicit and validated like the rest (`ddl-auto=validate`).
- The audit works in three layers: `LOG_REOPEN_REQUEST` (why and who approved), a domain
  `@Aspect` (which action happened — sign-off, reopen, incident transitions) and Envers (what
  changed in the data).

## Roles and capabilities

| Role | Can | Cannot |
|---|---|---|
| `ADMIN` | Manage sites, crews and users. | Sign off logs or incidents. |
| `SITE_MANAGER` | Create, edit and delete daily logs and work entries of the sites they manage; sign off a log; request a reopen (with reason); write shift notes for the AI draft; report incidents; sign off `LOW` / `MEDIUM` incidents. | Edit a signed-off log; approve a reopen; sign off `HIGH` / `CRITICAL` incidents; accept AI drafts. |
| `SAFETY_SUPERVISOR` | Review, edit and accept or reject AI drafts; sign off incidents of any severity (only role allowed for `HIGH` / `CRITICAL`); approve or reject reopen requests; manage corrective actions. | Manage users, sites and crews. |
| `INSPECTOR` | Read logs and incidents; view the compliance report. | Any write operation. |

Authorization is enforced on service methods (`@PreAuthorize`), and the Thymeleaf security
dialect hides actions the current user cannot perform.

## State machines

### Daily log (advanced requirement 1)

```mermaid
stateDiagram-v2
    [*] --> DRAFT : SITE_MANAGER creates log
    DRAFT --> DRAFT : edit work entries and crews
    DRAFT --> SIGNED_OFF : SITE_MANAGER signs off
    SIGNED_OFF --> REOPENED : SAFETY_SUPERVISOR approves reopen request (reason required, revision + 1)
    REOPENED --> REOPENED : edit (changes audited)
    REOPENED --> SIGNED_OFF : SITE_MANAGER signs off again
    note right of SIGNED_OFF
        Read-only. The service rejects edits (409)
        and database triggers block changes to the log,
        its work entries and crew assignments.
        A rejected reopen request leaves the log SIGNED_OFF.
    end note
```

### Incident (advanced requirement 2)

```mermaid
stateDiagram-v2
    [*] --> REPORTED : incident reported (or AI draft accepted)
    REPORTED --> UNDER_REVIEW : review started
    UNDER_REVIEW --> SIGNED_OFF : sign-off
    SIGNED_OFF --> CLOSED : close
    note right of UNDER_REVIEW
        HIGH and CRITICAL require root cause, witnesses,
        medical treatment and authority notified,
        validated in the service before sign-off.
    end note
    note right of SIGNED_OFF
        HIGH and CRITICAL: only SAFETY_SUPERVISOR.
        LOW and MEDIUM: SITE_MANAGER or SAFETY_SUPERVISOR.
    end note
```

### Incident draft (AI output)

```mermaid
stateDiagram-v2
    [*] --> PENDING_REVIEW : AI output is valid
    [*] --> NEEDS_MANUAL_INPUT : AI output invalid, incomplete or provider unavailable
    NEEDS_MANUAL_INPUT --> PENDING_REVIEW : supervisor completes the fields
    PENDING_REVIEW --> ACCEPTED : SAFETY_SUPERVISOR signs, Incident created
    PENDING_REVIEW --> REJECTED : SAFETY_SUPERVISOR rejects
    ACCEPTED --> [*]
    REJECTED --> [*]
```
