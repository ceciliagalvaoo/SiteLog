---
title: Wireframes
sidebar_position: 1
---

# Low-fidelity wireframes (M1)

## Purpose

The wireframes fix the **structure** of SiteLog before any visual design or implementation:
which screens exist, how a user navigates between them, which actions each role sees, and which
states (draft, signed off, reopened, empty, AI draft pending review) every screen has to handle.
They are deliberately greyscale, with placeholder boxes and real labels, so the discussion
stays on information architecture and workflow rather than colours.

They were produced **first**, before the high-fidelity screens, the component set and any
Thymeleaf template, as required by the assignment (prototype before implementation).
The high-fidelity phase (M2) builds on these frames in the same Figma file, on separate pages
(`Wireframes` / `Components` / `Screens`).

- **Figma file:** [SiteLog on Figma](https://www.figma.com/design/OdFchbgQXPWVLw115Wc3iz/SiteLog?node-id=0-1)
  (page `Wireframes`)
- **Export:** [wireframes.pdf](../prototype/wireframes.pdf) (revision 3), one page per screen,
  in the order of the table below (page N = screen N), frames 1440 px wide, greyscale raster at
  the frames' native size
- **Revisions:** rev. 1 (first generation, 1180 px frames), rev. 2 (2026-10-31, layout fixes)
  and rev. 3 (2026-10-31, labels and fields aligned with the domain model). The PDF holds rev. 3
  only; see [Revision 3](#revision-3-2026-10-31) and the [history](#revision-history).
- **Review:** every revision was checked against the domain model and the assignment checklist
  (see the divergences and open points below).

## Screens

Each screen was exported as a single frame. Screens 05, 07 and 08 carry a "Frame variant" bar that
names further states; only the highlighted (first) variant is drawn in the export. A variant that
appears only as a label in that bar is listed as *named, not drawn*.

| # | Screen | PDF | Variants in the export | What it decides |
|---|---|---|---|---|
| 01 | Login | p. 1 | — | Form login with work email and password, "Remember me"; no self-registration ("Contact your SiteLog administrator"). Entry point for every role. |
| 02 | Sites list | p. 2 | — | Table with Code, Name, Address, Active daily logs, Open incidents; search and status filter; "New site" is an Admin-only action. |
| 03 | Site detail | p. 3 | — | The site is the parent of everything: header with address and site code, tabs Daily logs / Incidents / Compliance, KPI cards (logs this month, awaiting sign-off, open incidents, open corrective actions), recent logs with status Signed off / Draft / Reopened, "New daily log". |
| 04 | Daily log form | p. 4 | — | Parent-child editing in one form: Date, Weather, Notes; repeatable work entries (Description, Location, Start time, End time) each with a nested list of crew assignments (Crew, Hours worked, Worker count, Task, remove, "+ Add crew"); "+ Add work entry"; a "Log summary" card with status Draft and a single "Save". |
| 05 | Daily log detail | p. 5 | Drawn: **Signed off**. Named, not drawn: Draft, Reopened, Reopen request pending, Request modal, Modal error | Locked log: status badge and lock, signed by / at and revision, Date / Weather / Notes, work entries with crew rows (crew, hours, workers, task), activity timeline (Created, Signed off, Reopen requested, Reopen approved), "Request reopen" as the only action. |
| 06 | Incidents list | p. 6 | — | Incidents per site: ID, title, severity chip (Low / Medium / High / Critical), status (Reported / Under review / Signed off / Closed), date; filters by severity and status; "Report incident". |
| 07 | Incident form | p. 7 | Drawn: **Default** (severity Low). Named, not drawn: High severity | Title, Occurred at, Category, Severity, Description, Affected crews (multi-select with chips), Related daily log (optional); single "Report incident" button. |
| 08 | Shift note to incident (AI) | p. 8 | Drawn: **Before analysis**. Named, not drawn: After analysis | Two-step AI flow: free-text shift note (filled), "Analyse note", review panel with Reject / Edit / Sign and create incident and the line "AI output must be reviewed by a responsible person". The panel is empty ("No analysis yet"). |
| 09 | Compliance report | p. 9 | — | Site, period, from / to filters; AR3 KPI cards (incidents by severity, incidents by category, corrective actions Open / Done / Overdue, % closed on time); corrective actions table (Action, Incident, Assignee, Due date, Status) filtered All / Open / Done / Overdue; export. |

All pages refer to [wireframes.pdf](../prototype/wireframes.pdf).

### Navigation

A persistent left sidebar shows the current user and role; breadcrumbs (`Sites / Riverside Tower`,
`Riverside Tower / New daily log`) mark the parent-child path. The main flow is
**Sites list → Site detail → Daily log form → Daily log detail**, with Incidents, the AI screen
and the Compliance report reached from the site. In the wireframes the sidebar still lists the
wireframe screens (01–09); the hi-fi sidebar becomes the app navigation (open point 6).

## Review against the brief

All nine planned screens exist, are greyscale (no colour, no images) and use real labels. The
labels were checked field by field against the [domain model](../architecture/domain-model.md). Rev. 1 and rev. 2 had ten
mismatches (D1–D10); rev. 3 follows the model on all of them.

## Divergences from the domain model

Recorded against rev. 1. "Author decision" marks the cases where the model could have been
extended instead; rev. 3 applied the default (follow the model) everywhere, so no extension of
the model is needed.

| # | Wireframe | Rev. 1 / 2 showed | Domain model says | Decision | Rev. 3 |
|---|---|---|---|---|---|
| D1 | 06 Incidents list | Status Open / Investigating / Action required / Resolved | `Incident.status` is `REPORTED → UNDER_REVIEW → SIGNED_OFF → CLOSED` (AR2) | Use the model statuses. | **Resolved**: Reported, Under review, Signed off, Closed. |
| D2 | 09 Compliance report | "Compliance score", "Inspections completed", "Inspection #88" as action source, Priority column | No inspection entity, no score, no `priority`; every `CorrectiveAction` belongs to an `Incident`; AR3 metrics | AR3 KPIs; source column links an incident; drop Inspection and Priority. | **Resolved**: the four AR3 KPI cards; Incident column (INC-…); no Priority. Minor: the "% closed on time" caption reads "14 of 17 incidents" but should count actions. |
| D3 | 04 Daily log form | "Temperature (°C)", "Shift supervisor" | `DAILY_LOG` has `log_date`, `weather`, `notes`; the manager comes from `Site.manager` | Remove both fields. | **Resolved**: Date, Weather, Notes only. |
| D4 | 04 Daily log form | One Crew select with Hours and Workers per entry | `WorkEntry *─* Crew` through `CrewAssignment(hoursWorked, workerCount, task)` | Nested crew rows per entry with "+ Add crew" / remove. | **Resolved**: nested rows Crew / Hours worked / Worker count / Task, remove, "+ Add crew" (entry 1 has two crews). |
| D5 | 04 Daily log form | Work entry Activity / Hours / Workers | `WORK_ENTRY` has `description`, `location`, `start_time`, `end_time` | Entry header with those four fields. | **Resolved**. |
| D6 | 04 Daily log form | "Workers" inputs overflow the card | Layout defect | Inputs fill their grid column. | **Resolved** (since rev. 2). |
| D7 | 04 / 05 Daily log | "Save as draft" + "Save daily log"; "Submitted" step | `DRAFT → SIGNED_OFF → REOPENED`, no submitted state | One "Save"; "Sign off" on the detail; timeline created / signed off / reopen. | **Resolved**: single "Save"; timeline Created, Signed off, Reopen requested, Reopen approved. Minor: 03 still labels the column "Submitted by". |
| D8 | 05 Daily log detail | "Shift 07:00–18:00", "Supervisor", "Cloudy, 16 °C" | No shift times or supervisor; single `weather` field | Weather, notes, signed off by / at, revision. | **Resolved**: Date / Weather / Notes, "Signed by … · Revision 1"; times come from each entry's start–end. Minor: the timeline shows "Reopen approved" while the log is still Signed off, revision 1 (should be Reopened, or signed off again as revision 2). |
| D9 | 07 Incident form | Draft / Submit buttons, no title, "Location on site", free-text affected crew | `REPORTED` on creation; `title`; no location; `Incident *─* Crew`; optional `DailyLog` | One "Report incident"; Title; crews multi-select; related log; no location. | **Resolved**: all applied. |
| D10 | 02 / 03 Sites | "Location", "Compliance score 91 %", "Workers on site" | `SITE` has `code`, `name`, `address`, dates; no score | Code + Address; status filter active / finished; open corrective actions card instead of score. | **Resolved**: Code and Address columns, site code in the header, "Open corrective actions" card; score and workers-on-site cards removed. The status filter options are not visible in the export. |

## Open points

Written against rev. 1; this list covers what is missing in the frames themselves, not the model
divergences above.

| # | Open point | Rev. 2 | Rev. 3 | Evidence (rev. 3) |
|---|---|---|---|---|
| 1 | Frame width 1440 px (plus 390 px list and form in hi-fi) | Resolved (desktop) | **Resolved** (desktop) | All pages 1440 px; 390 px versions are an M2 task. |
| 2 | Reopen-reason modal (required reason, validation error) and pending-request state (AR1) | Open | **Open** | Still only named in the variant bar of p. 5 (Request modal, Modal error, Reopen request pending). |
| 3 | Draft and Reopened states of the daily log detail | Open | **Open** | Named in the variant bar of p. 5, not drawn. |
| 4 | High / Critical incident fields (AR2) | Open | **Open** | Variant "High severity" named on p. 7, not drawn; the base form is now correct (D9). |
| 5 | Populated AI review panel with "Needs supervisor signature" | Partial | **Partial** | Note filled; panel still "No analysis yet"; "After analysis" named, not drawn. |
| 6 | Consistency: entry count; sidebar as app navigation with EN / HR switcher | Partial | **Partial** | "4 entries" matches four rows; sidebar still the wireframe index, no language switcher. |
| 7 | Empty and error states | Open (expected) | **Open (expected)** | Not required in low-fi; part of the hi-fi set. |

## Revision 3 (2026-10-31)

Rev. 3 aligned every screen with the domain-model fields and was meant to draw each variant as a
separate frame. Nine PDFs were exported, one page each; no variant
came as an extra page or frame.

### What changed from rev. 2

- **Model alignment (D1–D5, D7–D10):** every field and status listed in the table above now
  follows the domain model. The daily log form gained the nested crew assignments, which is the
  layout change that matters most for the M2 form (LO4).
- 02 Sites list: Code / Name / Address columns; 03 Site detail: KPI cards reworked (no compliance
  score, no workers on site).
- 05 Daily log detail: header shows signed by / at and revision; work entries render crew rows;
  the "Submitted" step is gone.
- 07 Incident form: rebuilt with the model fields and a single "Report incident".
- 09 Compliance report: AR3 KPIs and an incident-linked corrective actions table.
- **Not changed:** the variant states (05 Draft / Reopened / pending / modal / modal error,
  07 High severity, 08 After analysis) are still tabs inside one frame; only the default state
  is in the export.

### Assessment for M1

The low-fi set now covers the structure, navigation and data of all nine screens without model
mismatches, which is what the wireframe stage has to fix. The missing states are named in each
frame's variant bar, so the structure is known; drawing them is carried over to the hi-fi screens
in M2, where each state is drawn as a separate top-level frame.

### Carried over to the high-fidelity phase (M2)

1. Daily log detail in Draft, Reopened and Reopen request pending, plus the reopen modal and its
   validation error (open points 2–3, AR1).
2. Incident form with severity High and the extra required fields (open point 4, AR2).
3. Populated AI review panel with the "Needs supervisor signature" banner (open point 5).
4. App navigation with EN / HR switcher; empty list, form with validation errors, 390 px list and
   form (open points 1, 6, 7).
5. Copy fixes: "% closed on time" counts actions; "Created by" instead of "Submitted by"; a
   consistent activity timeline (a reopened log shows Reopened or a new revision).
6. Screens missing from the wireframes, found while walking the navigation: incident detail (opened
   from the incidents list, with workflow actions and the corrective actions section), corrective
   action form, site form, crews list and form, users list and form (Admin), and "My profile" with
   password change and preferred language. Without them the Incident → CorrectiveAction branch of
   the aggregate and the Admin capabilities have no screen.
7. Loading and provider-error states for the AI screen.

## Revision history

- **Rev. 1:** first version; nine screens at 1180 px; ten model
  divergences (D1–D10) and seven open points recorded.
- **Rev. 2 (2026-10-31):** 1440 px frames; work entry overflow fixed (D6); "4 entries" matches
  the rows; "Frame variant" bars added to 05, 07, 08 (variants not drawn); shift note filled.
  D1–D5 and D7–D10 still open.
- **Rev. 3 (2026-10-31):** model alignment, see above.
