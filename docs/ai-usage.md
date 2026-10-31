---
title: AI usage log
sidebar_position: 5
description: How AI supported routine setup and initial drafts under the author’s direction and review.
---

# AI usage log

This log documents how I used AI assistants as supporting tools while building SiteLog. It is separate from the product's own AI feature, which is evaluated in `docs/ai-evaluation.md` from M3 onwards.

**Tools used:** Claude Code for development assistance in the repository, and Figma AI for initial wireframe drafts.

The log is organised by milestone. Each milestone section records my own design work, where AI assisted, the prompts that mattered, what I corrected, and how I validated the result. New milestones are added below as the project progresses.

## Approach to AI assistance

I was responsible for analysing the assignment, defining the domain model, choosing the architecture and technologies, and planning the implementation. I used AI as a supporting tool for routine setup, repetitive configuration, and initial documentation and design drafts.

I defined the purpose and constraints of each task before requesting assistance. The assistants helped turn those instructions into draft files or screen layouts. I decided which suggestions to accept, checked them against the requirements, and corrected inconsistencies before incorporating them into the project.

## Week 5 — M1: project setup, domain model, and wireframes

### My design and planning work

I established the main modelling and technology decisions before using AI to draft supporting artefacts.

- **Domain model:** I defined the two branches of the aggregate; modelled `CrewAssignment` as an association entity containing hours, worker count, and task; specified the four roles and their capabilities; and defined the daily log and incident state machines. This model was accepted as the basis for subsequent documentation.
- **Technology selection:** I chose Render for application hosting, Supabase for PostgreSQL, Anthropic Claude through Spring AI 2.x for the product's AI integration, and Docusaurus for documentation. I selected the package name `hr.algebra.sitelog`. A practical consideration for Supabase was that Render's free database would expire before the defence.
- **Reopen audit trail (AR1):** I compared Hibernate Envers with a custom audit table and selected Envers to capture changes and deletions in work entries and crew assignments without implementing per-field audit logic. This decision is to be formalised in ADR-0003.
- **Security and tests:** I implemented the temporary `SecurityConfig` and wrote the three Testcontainers smoke tests, including assertions for the Flyway version, public health endpoint, and redirect to login. I also performed the validation described below.
- **Documentation structure:** I chose a single `docs/` directory for the Docusaurus site and its content, preserving the paths required by the assignment.
- **Screen coverage:** I reviewed the wireframes by walking through user workflows. I identified that the incidents list had no detail screen and that profile and administration areas were missing. I added incident details with corrective actions, a corrective action form, a site form, crews, users, and “My profile” to the high-fidelity design plan. The profile includes password change and a preferred language stored on the user.

### AI support for setup and first drafts

I used the assistants to help prepare supporting material from requirements and decisions I had already established.

#### Routine setup and configuration drafts

Claude Code provided assistance with the initial Spring Boot 4.1 / Java 25 scaffold through start.spring.io and draft setup files: `application*.yml`, `docker-compose.yml`, the Flyway baseline, and the CI workflow.

I used this assistance to reduce repetitive file preparation. I reviewed the resulting files against my selected stack, checked what the configuration actually did, and corrected configuration issues.

#### Documentation drafts

The assistant helped set up Docusaurus and prepare initial versions of the domain-model page, its diagrams, ADR-0001, and the README. These drafts were based on the model and decisions I supplied.

I checked the drafts against the assignment and domain model, reviewed the technical explanations, and revised claims that did not accurately describe the intended behaviour. The documentation remained subject to the same review as the code and configuration.

#### Initial wireframe layouts

Figma AI generated initial screen layouts from a written screen list. These were visual drafts based on my instructions. I reviewed their screen coverage and user flows against the model I had defined.

I compared the screens with the domain model, identified unsupported fields and inconsistent states, and provided corrections across three revisions. I also identified additional screens needed to complete the intended workflows.

### Prompts that mattered (M1)

The requests below are the ones behind the M1 setup, documentation and wireframe work described above. Prompts from later milestones are listed in their own milestone sections.

#### 1. Routine project configuration

> I have selected Spring Boot 4.1, Java 25, and PostgreSQL for SiteLog. Help me draft the routine setup files for this stack: application YAML, Docker Compose for the local database, and the Flyway baseline structure. Follow the versions and package name I provide. Keep the output limited to setup files; I am implementing the security configuration and tests myself.

#### 2. CI workflow boilerplate

> Draft a minimal GitHub Actions workflow that runs my existing Maven verification command, `./mvnw verify`, on pushes and pull requests. Use Java 25. My tests already exist; keep the assistance limited to the workflow YAML and explain the configuration so I can review it.

#### 3. Documentation structure

> Help me configure Docusaurus with the documentation content inside the existing `docs/` directory. Preserve the file paths required by my assignment. Suggest a simple sidebar and configure broken links to fail the build. Keep the content structure aligned with the page list I provide.

#### 4. Documentation from my domain model

> Using the domain model and notes below, prepare a first draft of the domain-model page and its diagrams. Preserve the entities, relationships, roles, and state transitions exactly as specified. Do not introduce new fields or business rules. Flag any ambiguity for my review.

#### 5. Formatting an architecture decision record

> Turn my decision notes below into an ADR-0001 draft with context, decision, alternatives considered, and consequences. Preserve my selected technologies and reasons. Do not invent technical guarantees or present assumptions as verified behaviour. Mark missing information for review.

#### 6. README first draft

> Help organise my project notes into a concise README covering the purpose of SiteLog, the stack I selected, local setup, and the commands I provide. Use only the supplied information. Do not claim that features or checks are complete unless my notes explicitly confirm this.

#### 7. Initial Figma layouts

> Create initial wireframe layouts for SiteLog using the screen list and domain model below. Follow the supplied fields, roles, relationships, and status values. A work entry can have multiple crew assignments. Do not add inspections, compliance scores, or fields outside the model. These layouts are drafts that I will review and refine.

#### 8. Figma revision after my review

> Update the wireframes according to my review notes: remove temperature and shift supervisor fields, allow multiple crew assignments per work entry, use the incident statuses I specify, and remove inspections and compliance scores. Preserve the rest of the agreed screen structure. I will check the revised frames against the model.

### Review findings and corrections

The review process included checking the assumptions behind generated output, as well as whether it built or rendered successfully.

#### Concurrency handling in ADR-0001

The initial draft claimed that `@Version` on the daily log would detect concurrent edits to its work entries. I identified that the claim did not account for child-entity updates: a child change does not automatically increment the root's version.

The documented approach was revised to load the root with `LockModeType.OPTIMISTIC_FORCE_INCREMENT` whenever a child changes. The documentation also records the cost of an additional root update per child change. This is the planned concurrency strategy and still requires implementation-level verification.

#### Scope of immutability

The initial draft protected only `daily_log`. I checked this against the domain requirements and identified that legally relevant content also resides in `work_entry` and `crew_assignment`.

The planned database triggers were expanded to cover those child entities so that the protection reflects the scope of the relevant content.

#### Wireframe consistency with the domain model

The generated frames introduced elements that were not part of the model, even though prompt 7 explicitly required multiple crew assignments per work entry and excluded inspections, compliance scores and fields outside the model:

- Temperature and shift supervisor fields.
- A single crew per work entry instead of multiple crew assignments.
- Incident statuses that differed from the defined state machine.
- Inspections and a compliance score outside the agreed scope.

Stating the constraints in the prompt was therefore not enough: each frame had to be checked field by field against the domain model. I documented these divergences, requested their correction with prompt 8, and checked the result in revision 3. I also identified screen 04 as the daily log form despite its misspelled export name.

#### Configuration quality

The scaffold contained the invalid parent version `4.1.2.RELEASE`, which was corrected to `4.1.2`.

I replaced the scaffold's default `contextLoads` test, which contained no explicit assertions, with tests I wrote to check the Flyway version, the public health endpoint, and the redirect to login.

### Validation performed

- `./mvnw verify` passes with Testcontainers and PostgreSQL 17.
- The application starts with the `dev` profile against the Docker database, and `/actuator/health` reports `UP`.
- The documentation site builds with broken links configured to fail the build.
- The Supabase project's PostgreSQL version, 17.11, was checked in its dashboard for consistency with the local and test database setup.

These checks cover the M1 setup only.

### AI-drafted material not yet fully reviewed

Two drafted items were accepted for M1 but still need a closer review of their reasoning:

- **`.env` import in `application-dev.yml`:** the drafted dev profile imports the `.env` file (`spring.config.import`) instead of relying on exported environment variables. It works and was validated above, but I have not yet decided whether to keep this approach.
- **ADR-0001 rationale:** the arguments for Flyway over Liquibase, the surrogate key of `CrewAssignment`, and Supabase over other hosts are drafts that I still want to refine in my own words.
