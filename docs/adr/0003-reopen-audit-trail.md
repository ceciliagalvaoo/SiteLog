---
title: "ADR-0003: Reopen audit trail with Hibernate Envers"
sidebar_label: "0003 Reopen audit trail"
sidebar_position: 3
slug: /adr/0003-reopen-audit-trail
description: How changes to a reopened daily log are recorded — Hibernate Envers on the daily log aggregate, a custom revision entity with the user, audit tables created by Flyway.
---

# ADR-0003: Reopen audit trail with Hibernate Envers

- **Status:** Accepted
- **Decision date:** 2026-10-31
- **Written:** 2026-11-04
- **Milestone:** M2 (schema), M3 (implementation)

:::info Status of the implementation
Decision only. The audit tables are created by the Flyway migration
`V3__incidents_and_reopen.sql` and made append-only by `V5__append_only_audit_tables.sql`
(milestone M2); the `hibernate-envers` dependency, `@Audited` mappings, revision entity and
the before/after screen arrive with the audited reopen in M3.
:::

## Context

Advanced requirement 1: a daily log **locks after sign-off**, and a reopen **requires a reason**
and is **audited**. The assignment names the hard part: *a log that is both legally final after
sign-off and correctable through a defensible process.*

"Defensible" means that, for any signed-off log, SiteLog can answer later:

1. **Why** was it reopened, who asked and who approved? — `LOG_REOPEN_REQUEST` (reason,
   requested by, approved by, timestamps, status).
2. **Which action** happened and by whom? — sign-off, reopen approval, re-sign-off.
3. **What changed** while it was reopened — the value before and after, who changed it and
   when, for the log, its work entries and its crew assignments, including rows added or
   deleted.

The lock itself is covered elsewhere (service check + database triggers on `daily_log`,
`work_entry`, `crew_assignment`; see the [domain model](../architecture/domain-model.md) and
[ADR-0001](./0001-persistence-model.md)). This ADR decides how question 3 is answered, and how
it fits with 1 and 2.

Constraints from earlier decisions: the schema is owned by Flyway and Hibernate only validates
(`ddl-auto=validate`); the aggregate is `DailyLog → WorkEntry → CrewAssignment` mapped with JPA;
PostgreSQL 17 everywhere.

## Decision

1. **Hibernate Envers** records the data history of the daily log aggregate. `@Audited` on
   `DailyLog`, `WorkEntry` and `CrewAssignment`. Associations to entities that are not audited
   (`Site`, `Crew`, `AppUser`) are marked
   `@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)`, so the audit rows keep the
   foreign key without auditing reference data.
2. **Custom revision entity.** A `@RevisionEntity` with a `RevisionListener` that stores the
   authenticated user (taken from the `SecurityContext`) next to the revision number and
   timestamp. Every revision therefore answers *who* and *when*. Changes made without a user
   (scheduled jobs) are stored with a fixed system value. The Flyway seed (V4) is plain SQL and
   creates no revisions.
3. **Audit tables created by Flyway.** `revinfo` (revision entity) and `daily_log_aud`,
   `work_entry_aud`, `crew_assignment_aud` (each: the audited columns, `rev` → `revinfo`,
   `revtype` 0 = insert, 1 = update, 2 = delete; primary key `(id, rev)`) are written by hand in
   the migration, like every other table. `ddl-auto=validate` also validates them, so a
   mapping change without a migration fails at startup and in CI.
4. **Before/after by comparing revisions.** The reopen history is read with `AuditReader`
   (`forRevisionsOfEntity` / audit queries): for each audited row of the log, the revisions
   made between the reopen approval (`LOG_REOPEN_REQUEST.decided_at`) and the next sign-off
   are compared with the revision just before them; the differing fields are shown as
   before → after, with user and time from the revision entity. Added rows have no "before",
   deleted rows (`revtype` 2) have no "after".
5. **Continuous auditing, including `DRAFT`.** Envers records every change, not only changes
   in `REOPENED`. This is accepted on purpose:
   - the drafting history (who entered which hours, when) is useful evidence in a dispute
     about a log, not noise;
   - the volume is small: one log per site per day with a handful of entries;
   - filtering by status would need conditional auditing (custom event listeners), more code
     at exactly the transition where a missed change matters (sign-off and reopen);
   - the reopen view selects its revisions by time window and `revision` number, so the
     extra `DRAFT` revisions do not get in its way.
6. **Three layers, one job each.** `LogReopenRequest` = why and who approved; a domain
   `@Aspect` on the service methods = which action (sign-off, reopen request/approval/rejection,
   incident transitions) and by whom; Envers = what changed in the data. None of the three
   tries to do another's job.

## Alternatives rejected and why

| Alternative | Why rejected |
|---|---|
| **Own table `DAILY_LOG_AUDIT`** (one row per changed field: entity, entity id, field, old value, new value, user, time) | Gives before/after directly and only for `REOPENED`, but the service must write every row by hand. Reopen edits touch the children, including deletions; a forgotten field or a new column without audit code silently breaks the trail. Values are stored as text, losing types. More code and tests than the annotation-based alternative for the same evidence. |
| **Audit triggers in PL/pgSQL** (generic trigger copying `OLD`/`NEW` into an audit table, e.g. as `jsonb`) | Catches even manual SQL, but the database does not know the application user: every transaction would have to set a session variable (`SET LOCAL`) through the pool, which is easy to miss and hard to test. Audit logic in PL/pgSQL is harder to read back from JPA and to show on a screen. The project already uses triggers for the lock, where they only say *no*; recording history is better kept where the user is known. |
| **Event sourcing** (store log events, derive the current state) | Full history by construction, but it replaces the JPA aggregate model of [ADR-0001](./0001-persistence-model.md) and the CRUD screens and REST API the assignment asks for, with projections, replay and versioned events. Far more design than one audited aggregate needs. |
| **Only `LogReopenRequest`** | Records why and who approved, but not what changed. Fails requirement 1 ("audited reopen") and the "defensible process" of the hard part: there would be no before/after. |

## Consequences accepted

- **One more dependency and its rules.** `hibernate-envers` (managed by Spring Boot) must be
  added; non-audited targets need `NOT_AUDITED` or Envers fails at startup.
- **Audit schema maintained by hand.** Every change to an audited column needs the same change
  in its `*_aud` table in the same migration. `validate` and the Testcontainers test catch a
  mismatch.
- **Extra writes per transaction.** Each transaction that changes an audited entity inserts one
  `revinfo` row and one `*_aud` row per changed entity. Acceptable at this volume.
- **Audit rows also for `DRAFT`.** Storage grows with every edit, not only reopen edits;
  accepted for the reasons in decision 5.
- **Before/after is computed, not stored.** The reopen history page needs a query that pairs
  revisions; it is tested in M3. Field-level differences are calculated in the service, not
  read from a column.
- **Same transaction as the change.** Envers writes in the business transaction: if the change
  rolls back (409, trigger error), its audit rows roll back too, so the trail never records a
  change that did not happen.
- **Append-only audit tables (decided 2026-11-04).** `V5__append_only_audit_tables.sql` adds
  triggers on `revinfo`, `daily_log_aud`, `work_entry_aud` and `crew_assignment_aud` that reject
  `UPDATE` and `DELETE` (row triggers) and `TRUNCATE` (statement triggers, because `TRUNCATE`
  does not fire row triggers) with SQLSTATE `P0001`, message `audit_append_only`. `INSERT` stays
  allowed. Consequences:
  - Envers must keep its default `DefaultAuditStrategy`, which only inserts audit rows.
    `ValidityAuditStrategy` is ruled out: it updates the previous audit row to set `REVEND`,
    which the triggers reject.
  - A revision row cannot be completed after it is inserted, so the `RevisionListener` fills
    `username` in `newRevision()` only.
  - The protection stops application code and ordinary SQL, not a database administrator: the
    table owner can disable the triggers and a superuser can bypass them
    (`session_replication_role = replica`).

  Details: [database schema](../architecture/database.md#audit-tables-envers).
