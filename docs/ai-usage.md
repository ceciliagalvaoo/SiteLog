---
title: AI usage log
sidebar_position: 5
description: My design and implementation work on SiteLog and the tasks where AI assisted.
---

# AI usage log

I designed SiteLog, defined its business rules and domain model, chose its architecture and technologies, and planned the implementation. I used Claude Code to help prepare routine setup files and first drafts of documentation, database migrations, and selected tests. I used Figma AI and Figma Make to generate initial screen layouts from my specifications.

I defined the requirements for each task, reviewed the output against the assignment and the domain model, and decided what to incorporate. Each milestone records my own work, where AI assisted, what I corrected, and what I verified.

The AI feature within SiteLog is evaluated separately in `docs/ai-evaluation.md` from M3 onwards.

## Week 5 — M1: project setup, domain model, and wireframes

### My decisions and work

- **Domain model.** I defined the two branches of the aggregate, the four roles and their permissions, and the state machines for daily logs and incidents. I modelled `CrewAssignment` as an association entity that records hours, worker count, and task. Each work entry can have multiple crew assignments.
- **Architecture and tools.** I chose Render for application hosting, Supabase for PostgreSQL, Anthropic Claude through Spring AI 2.x for the product's AI feature, and Docusaurus for documentation. I selected `hr.algebra.sitelog` as the package name and kept the documentation site and its content in the existing `docs/` directory. I chose Supabase in part because Render's free database would expire before the defence.
- **Reopen audit trail.** I compared Hibernate Envers with a custom audit table and chose Envers to capture changes and deletions in work entries and crew assignments. I planned to formalise this choice in ADR-0003.
- **Security and tests.** I implemented the temporary `SecurityConfig` and wrote three Testcontainers smoke tests covering the Flyway schema version, the public health endpoint, and the redirect to login.
- **Screen coverage.** I walked through the user workflows and identified gaps in the initial wireframes. I added incident details with corrective actions, a corrective action form, a site form, crews, users, and a profile page to the high-fidelity design plan. I specified password change and a preferred language stored on the user record.

### Where AI assisted

- **Project setup.** Claude Code helped prepare the Spring Boot 4.1 and Java 25 scaffold and draft the application YAML files, Docker Compose configuration, Flyway baseline, and GitHub Actions workflow.
- **Documentation.** Claude Code helped configure Docusaurus and turn my model and decision notes into first drafts of the domain-model page, diagrams, ADR-0001, and README.
- **Wireframes.** Figma AI generated initial layouts from the screen list, fields, roles, and statuses I supplied. I reviewed the frames against the domain model and provided corrections across three revisions.

### What I reviewed and corrected

- **Concurrency.** I found that the first ADR draft assumed that updating a child entity would increment the daily log's `@Version` automatically. I revised the planned approach to use `LockModeType.OPTIMISTIC_FORCE_INCREMENT` on the daily log when a child changes and documented the additional root update this requires. This strategy still needed implementation-level verification at the end of M1.
- **Signed-off content.** The draft protection covered only `daily_log`. I identified that signed-off content also lives in `work_entry` and `crew_assignment`, so I expanded the planned trigger coverage to both child tables.
- **Wireframes.** The generated frames added temperature, a shift supervisor, inspections, and a compliance score. They also limited work entries to one crew and used incident statuses that differed from my state machine. I checked the frames field by field, requested corrections, and checked revision 3 against the model. I also identified screen 04 as the daily log form despite the misspelling in the export name.
- **Scaffold.** I corrected the Spring Boot parent version from `4.1.2.RELEASE` to `4.1.2` and replaced the default `contextLoads` test with the three smoke tests I wrote.

### What I verified

- `./mvnw verify` passed with Testcontainers and PostgreSQL 17.
- The application started with the `dev` profile against the Docker database, and `/actuator/health` reported `UP`.
- The documentation site built with broken links configured to fail the build.
- I checked in the Supabase dashboard that the project used PostgreSQL 17.11, the same major version as the local and test databases.

### Representative prompts (summarised)

1. **Project configuration.** Draft the application YAML, Docker Compose configuration, and Flyway baseline for the stack and package name I selected. Keep the scope to setup files. I am implementing security and tests myself.
2. **CI workflow.** Draft a minimal GitHub Actions workflow using Java 25 to run my existing `./mvnw verify` command on pushes and pull requests.
3. **Documentation setup.** Configure Docusaurus to use the existing `docs/` directory, preserve the assignment's required paths, and fail the build on broken links.
4. **Domain-model documentation.** Draft a page and diagrams from my model. Preserve the supplied entities, relationships, roles, and state transitions. Flag ambiguities for review.
5. **ADR-0001.** Turn my decision notes into an ADR covering context, decision, alternatives, and consequences. Preserve my choices and mark assumptions for review.
6. **README.** Organise my notes into a README covering SiteLog's purpose, stack, local setup, and commands. Use the information I supplied.
7. **Initial wireframes.** Generate layouts from my screen list and domain model. Support multiple crew assignments per work entry and use only the specified fields, roles, and statuses.
8. **Wireframe revisions.** Apply my review notes. Remove temperature, shift supervisor, inspections, and compliance scores. Support multiple crew assignments and use the incident statuses I supplied.

### Items still under review at the end of M1

- Whether to keep the `.env` import in `application-dev.yml`. I verified that it worked in the local setup.
- The wording of ADR-0001's rationale for choosing Flyway over Liquibase, using a surrogate key for `CrewAssignment`, and selecting Supabase over other hosts.

## Week 6 — M2: architecture, database design, and high-fidelity prototype

### My decisions and work

- **Signed-off daily logs.** I specified two layers of protection. The service rejects edits, and database triggers enforce the rules on `daily_log`, `work_entry`, and `crew_assignment`. I defined the permitted reopen transition and its conditions.
- **Reopen transition tests.** I wrote integration tests to verify that a signed-off daily log can be reopened when its revision increases by one and its content stays unchanged. I also tested that the database rejects a reopen operation that attempts to change the log's content.
- **Schema and constraints.** I used the fields in my domain model and added an optional `site.description` after reviewing the prototype. I specified `CHECK` constraints for incident categories and weather values to match the Java enums.
- **Audit trail.** I confirmed Hibernate Envers in ADR-0003 and decided that `revinfo` and the `*_aud` tables should reject `UPDATE`, `DELETE`, and `TRUNCATE`. I recorded this decision on 2026-11-04. Migration V5 enforces it.
- **Demo data.** I kept a second site manager assigned to a different site so I could demonstrate that managers can access only their own sites.
- **Prototype rules.** I specified that administrators create accounts, users receive in-app notifications, and weather remains optional. I left self-registration and password recovery out of scope. I chose Manrope and used the domain model as the source of truth when the prototype and model differed.
- **Prototype assembly.** I assembled the screens in Figma, organised them into sections, built the component set and its variants by hand, wired the clickable flow, added the missing daily log empty state, and corrected the screens after the review so they match the model (login, site form and cards, daily log timeline and weather, profile fields, AI panel, compliance caption).

### Where AI assisted

- **Database migrations and tests.** Claude Code helped translate my schema, trigger, audit, and seed specifications into initial SQL for migrations V2–V5 and draft selected integration tests in `src/test/java/hr/algebra/sitelog/db/`. I wrote the reopen transition tests described above.
- **Technical documentation.** Claude Code prepared first drafts of the architecture overview, database schema page, and ADR-0003 from my decisions and the accepted domain model.
- **Prototype drafts and exports.** Figma Make generated initial screens from my prompts for the design system, daily logs, incidents and compliance, and site, profile, and admin flows. Claude Code helped compare my exports with the model and assignment, assemble them into `docs/prototype.pdf`, and draft `docs/design-rationale.md`, including the contrast measurements. I reviewed the rationale.

### What I reviewed and corrected

- **Envers schema.** I checked the draft against the source for the Hibernate Envers version in use. The audit schema uses the `_aud` suffix, `rev`, and `revtype`. The `revtype` column maps to `smallint` on PostgreSQL. The `@Version` column is excluded from auditing.
- **Audit strategy.** I kept the default Envers strategy because it inserts new audit rows. `ValidityAuditStrategy` updates the previous row to set `REVEND`, which conflicts with the append-only rule.
- **Flyway smoke test.** I replaced the M1 assertion that pinned the schema version to `"1"` with a check that no migration is pending. This lets the test remain valid as migrations are added.
- **Trigger locking.** The trigger reads the parent log status with a plain `SELECT`. I kept concurrency control with the application's optimistic locking strategy because a `FOR SHARE` lock could conflict with the root version update when two editors work concurrently.
- **`TRUNCATE` protection.** I checked that each audit table had a statement-level trigger for `TRUNCATE`, which row-level triggers do not cover.

### What I verified

- `./mvnw verify` passed 56 tests against PostgreSQL 17 using Testcontainers.
- Migrations V1–V5 applied to a clean PostgreSQL 17 database.
- The new diagrams parsed with the Mermaid version used by the documentation site.
- The documentation site built with broken links configured to fail the build.

### Representative prompts (summarised)

1. **Signed-off logs.** Draft PostgreSQL triggers that reject `INSERT`, `UPDATE`, and `DELETE` on work entries and crew assignments belonging to a signed-off daily log. For the daily log itself, implement the reopen conditions I specify.
2. **Envers schema.** Draft the audit tables using the table names, column names, and types expected by the Hibernate Envers version in the project. Check these against that version's source.
3. **Append-only audit tables.** Draft a migration that rejects `UPDATE`, `DELETE`, and `TRUNCATE` on the revision and audit tables.

### Items still under review at the end of M2

- Which `CHECK` constraints belong in the database and which rules should be enforced only in the service.
- The demo password, which the seed currently creates in every environment, including production.
- Whether to retain the second site manager beyond the demo scenario.