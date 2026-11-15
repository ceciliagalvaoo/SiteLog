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

- **Concurrency.** I found that the first ADR draft assumed that updating a child entity would increment the daily log's `@Version` automatically. I revised the planned approach to use `LockModeType.OPTIMISTIC_FORCE_INCREMENT` on the daily log when a child changes and documented the additional root update this requires. This strategy still needed implementation-level verification at the end of M1; the week 7 tests verified it.
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

### Review outcome

- **`.env` import in `application-dev.yml`.** Kept: Docker Compose and the application read the same git-ignored file, so the local credentials cannot drift apart. I verified it in the local setup.
- **ADR-0001 rationale.** I reviewed the reasons for Flyway over Liquibase, the surrogate key for `CrewAssignment`, and Supabase over other hosts, and kept them as written.

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

### Review outcome

- **`CHECK` constraints.** Value rules that a single row can check (enumerations, ranges, date order, lower-case e-mail) stay in the database as well as in the service; rules that need other tables, such as the site manager's role, stay in the service.
- **Demo password.** Kept in every environment: the deployed instance is a demo for grading, and its accounts and password are documented in the README.
- **Second site manager.** Kept, so the rule that managers see only their own sites can be demonstrated.

## Week 7 — M2: persistence mapping, MVC screens, and prototype revision 2

### My decisions and work

This week, I focused on integrating persistence with the MVC screens and completing the second prototype revision. I defined the business rules, reviewed the implementation, and checked data integrity, concurrency, query performance, and consistency with the Figma design.

- **Deletion rules.** I defined deletion policies that preserve historical records. A site can be deleted only if it has no daily logs or incidents; otherwise, the application asks the user to set an end date. A daily log can be deleted only in `DRAFT`, together with its work entries and crew assignments. A crew can be deleted only if it has never been used; otherwise, it must be deactivated.

- **Business rules confirmed after review.** I confirmed that a site appears as `Finished` when its end date is on or before today, daily logs cannot have future dates, and sign-off requires at least one work entry. An inactive crew remains on logs where it was previously assigned but cannot be selected for a new assignment.

- **Persistence and auditing.** I worked through the JPA mappings for migrations V1–V6, including notifications, lazy associations, versioned aggregate roots, and Envers auditing with a custom `revinfo` entity that records the username. I reviewed the repository queries, ordered child collections, and HikariCP configuration.

- **Services and MVC.** I structured the application flow around services, nested form objects, and controllers using Post/Redirect/Get with flash messages. I incorporated the deletion rules, shared notification-bell data through `ControllerAdvice`, HTTP 409 handling for optimistic-lock conflicts, and a cookie-based `LocaleResolver`.

- **Concurrency control.** I applied `OPTIMISTIC_FORCE_INCREMENT` when a daily log’s child records change, so updates to work entries and crew assignments also participate in the parent’s version checks. I checked the locking query’s scope and the timing of the version increment.

- **Bilingual notifications.** I chose to store notification message keys and arguments instead of preformatted text. This allows the same notification to be displayed in English or Croatian according to each user’s language.

- **Prototype revision 2.** I revised the Figma prototype to improve readability and component consistency. I darkened labels and placeholders, added icons to date and time inputs, labelled crew rows, and adjusted notification colours. I reorganised the components into icons, a 3×3 button set, form controls with five states, a cell-based table, chips with 12 variants and four additional families, and a crew row composed of reusable parts.

### Where AI assisted

I used Claude Code to support implementation within the requirements and technical constraints I specified. It helped draft repetitive code, suggest query structures, and prepare tests and documentation. I reviewed the generated output and corrected issues through testing and runtime checks.

- **Persistence.** AI assisted with drafts of migration V6, entity mappings, the Envers revision entity, repositories, and connection-pool configuration.
- **Services and MVC.** AI helped translate my rules into service methods, nested form handling, controllers, and concurrency and localisation configuration.
- **Templates.** AI supported drafting the Thymeleaf layout, parameterised fragments, CSS based on the Figma tokens, and initial English and Croatian message bundles.
- **Prototype documentation.** AI assisted with revision comparisons, contrast calculations, regeneration of `docs/prototype.pdf`, and updates to `docs/design-rationale.md`.
- **Tests.** AI helped draft 63 repository tests, SQL statement-count checks through Hibernate Statistics, aggregate-flow tests, PRG tests, and the N+1 regression test.

### What I reviewed and corrected

- **Duplicated work entries.** A test exposed duplicated work entries when nested fetch joins loaded child collections mapped as Hibernate bags. I changed the collections from `List` to `Set`, with `@OrderBy` to preserve their display order.

- **Force-increment lock scope.** The initial locking query also fetched the `Site`, causing Hibernate to attempt to lock an entity without `@Version`. I restricted the query to the `DailyLog`, which is the entity whose version must increase.

- **Version increment timing.** I verified that the forced version increment occurs at transaction commit rather than at flush and accounted for this when reviewing the concurrency tests.

- **Fragment names.** Names such as `select`, `button`, and `textarea` conflicted with HTML element names in Thymeleaf’s fragment selector. I renamed the fragments to make their selection unambiguous.

- **Daily log list performance.** I measured 64 SQL statements for the list page and reduced this to two using a `GROUP BY` projection. I rejected a paginated collection fetch join because Hibernate would apply pagination in memory.

### What I verified

- `./mvnw verify` passed all 137 tests against PostgreSQL 17 using Testcontainers.
- The repository tests checked query statement counts and database constraint enforcement.
- The documentation site built successfully with broken links configured to fail the build.
- The templates rendered in the running application in both English and Croatian.

### Representative prompts (summarised)

My prompts specified the expected behaviour and implementation constraints before requesting supporting drafts.

1. **Entity mapping.** Map migrations V1–V6 to JPA entities using lazy associations, `@Version` on the daily log and incident roots, a `CrewAssignment` entity with its own identifier, and an Envers revision entity recording the username. Validate the mappings with `ddl-auto=validate`.

2. **Repository queries.** Draft queries for the screen requirements: a daily log list without N+1 queries, a detail query fetching work entries, crew assignments and crews, site KPI counts, and existence checks supporting the deletion rules.

3. **Services and controllers.** Draft CRUD flows for sites, crews and daily logs, including nested form lists, PRG, flash messages, the specified deletion policies, and a forced parent version increment when daily log children change.

4. **Layout and localisation.** Translate the Figma components into a Thymeleaf layout and parameterised fragments, using the defined typography, spacing and colour tokens, with English and Croatian validation messages.

5. **Repository tests.** Draft PostgreSQL integration tests that check single-statement detail queries and verify that database constraints reject invalid data.

### Review outcome

- **`DailyLogAssembler`.** I reviewed the synchronisation of nested form lists: existing rows are updated by identifier, new rows are added, and omitted rows are removed.
- **Business rules.** I completed the review of site status, daily log dates, sign-off requirements, and the treatment of inactive crews.
- **Fragments and CSS.** I checked the fragments against the Figma components and reviewed the design tokens in `app.css`.
- **Croatian translations.** I reviewed `messages_hr.properties` for consistency with the construction vocabulary, including *gradilište*, *dnevnik*, *ekipa*, and *unos rada*.

## Week 8 — M2: aggregate screens, error pages, tests, and documentation

### My decisions and work

This week, I completed the M2 screens for the daily log aggregate, tested the service and web layers, and aligned the documentation with the code. I set the requirements, made the implementation decisions, and verified the result against the Figma prototype and the checkpoint requirements.

- **Figma links.** I linked the *Components* and *Screens* pages directly from the README so the evaluator can open either page without searching the Figma file.
- **Prototype as visual reference.** I used the hi-fi prototype as the visual reference for each screen. I compared the finished templates with their Figma frames and documented each deviation and its reason in the README’s Figma frame → template table.
- **Screens.** I defined the behaviour of the sites list with its *Active / Finished* filter, the nested daily log form, the detail page with its lock notice, the delete and sign-off confirmation modals, and the crew and notification pages. I chose server-side submissions to add and remove form rows without JavaScript. I also specified what the error pages could display and verified that they exposed no internal details.
- **Tests and documentation.** I defined the business rules and expected outcomes for the tests, including PRG, validation, conflict and not-found handling, and rendering in English and Croatian. I checked the architecture, database schema, and domain model documentation against the migrations and the current code.

### Where AI assisted

I used Claude Code for repetitive drafting where I could provide a clear pattern and check the output against the project. I supplied the Figma references, expected behaviour, and validation criteria, then reviewed and tested the drafts before incorporating them.

- **Thymeleaf screens.** I used AI to draft repeated markup for forms, confirmation modals, and error pages based on the components and behaviours I specified.
- **Localisation.** AI helped draft 208 new message keys in English and Croatian using the project’s existing message bundle format. I checked the messages in the rendered screens.
- **README table.** AI helped draft the frame-to-template table. I checked each entry against the Figma frame and the implemented template and verified the reason for each deviation.
- **Tests.** AI helped draft repeated test setup and cases from the scenarios I defined. I reviewed the assertions for the bilingual rendering tests, service unit tests, and `@WebMvcTest` controller tests, then ran the suite.
- **Documentation.** AI helped with wording and formatting updates across the domain model, architecture overview, database schema, ADR-0003, and README. I verified the technical details against the code and migrations.

### What I reviewed and corrected

- **Enter key in the daily log form.** Pressing Enter activated the first submit button, which removed a row. I added a visually hidden *Save* submit button at the top of the form and verified that Enter saves the log.
- **Error pages and the database.** The advice that supplied layout data queried the database even on error pages, which prevented the 500 page from rendering during a database outage. I restricted the advice to the application’s package so Spring Boot’s error controller could render without querying the database.
- **Site status filter.** The draft applied the *Active / Finished* filter in the controller after fetching a page. This could produce incorrect counts and partly empty pages. I moved the filter into the repository query so it applies before pagination.
- **Stale compiled classes.** I traced misleading failures to invalid `.class` files written by the IDE into `target/`. I ran `./mvnw clean verify` and documented the command in the README.
- **Decimal hours.** The hours field rejected values such as `7.5`. I set `step="any"` on the number input and checked that server-side range validation still applied.
- **Flash message overload.** A call to `Flash.success` with a site name as its single `String` argument selected the overload that expected a `detailKey`. The page then displayed `??name??`. I renamed that overload to `successWithDetail` and added a regression test in `FlashTests` to verify that the site name is stored as a message argument.

### What I verified

- I ran the full test suite against PostgreSQL 17 using Testcontainers. The 147 service unit tests contributed to **99.8% service-layer line coverage** and **90.9% overall line coverage**. Maven generates the JaCoCo report, which is committed in `docs/jacoco/`.
- I checked the results of all 20 rendering tests for the English and Croatian screens.
- I ran the application with demo data and navigated every screen at **1440 px** and **390 px**. None required horizontal scrolling.
- I verified that the documentation site built successfully with broken links configured to fail the build.

### Representative prompts (summarised)

I gave Claude Code specific drafting tasks with requirements I could verify in the resulting markup or tests.

1. **Aggregate screens.** Draft the repeated form, detail, and confirmation markup using the specified Figma components. Use submit buttons for adding and removing rows without JavaScript. Flag deviations for the README table.
2. **Error pages.** Draft the 403, 404, 409, and 500 templates using the empty-state and button components. Use translated messages and omit stack traces and exception details. The pages must render when the database is unavailable.
3. **Rendering test.** Draft a test that renders each screen in English and Croatian and fails if a translation key is missing or a template fails to render.
4. **Service tests.** Draft unit test cases for the rules I confirmed for finished sites, future log dates, sign-off with at least one work entry, inactive crews, and deletion. Target at least 80% service-layer line coverage.
5. **Controller tests.** Draft `@WebMvcTest` cases for PRG redirects with flash messages, forms re-rendered with field errors, and HTTP 409 and 404 responses.

### Review outcome

- **Screens.** I compared each template with its Figma frame and confirmed that every deviation has a reason in the README.
- **Keyboard and input behaviour.** I confirmed that Enter saves the daily log and that decimal hours are accepted and validated.
- **Error handling.** I confirmed that error pages render without database queries and show no internal details.
- **Flash messages.** I confirmed that success messages display the site name correctly and that the regression test covers the single-`String` case.
- **Tests.** I checked that the service tests exercise the confirmed business rules and that the controller tests cover PRG, validation, conflicts, and not-found responses.
- **Documentation.** I checked that the architecture, database schema, and domain model pages match migrations V1–V6 and the current packages.