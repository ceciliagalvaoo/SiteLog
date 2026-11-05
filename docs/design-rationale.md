---
title: Design rationale
sidebar_position: 2
description: Why the SiteLog high-fidelity prototype looks and behaves the way it does — layout, palette, components, WCAG AA contrast check, Croatian labels, mobile, role-based layout and what changed from the wireframes.
---

# Design rationale (M2)

:::info Status (2026-11-05, milestone M2)
High-fidelity prototype and component set reviewed against the assignment, the
[domain model](./architecture/domain-model.md) and the Flyway migrations. The prototype was made
**before** the Thymeleaf screens. Review findings were corrected in Figma; what the
implementation refines on top of the prototype is listed in
[From prototype to implementation](#from-prototype-to-implementation).
:::

- **Figma prototype (presentation mode, starts at Flow 1 – Sites):**
  [open the prototype](https://www.figma.com/proto/OdFchbgQXPWVLw115Wc3iz/SiteLog?node-id=20-3626&p=f&scaling=min-zoom&content-scaling=fixed&page-id=9%3A3&starting-point-node-id=20%3A3626)
- **Figma file:** [SiteLog](https://www.figma.com/design/OdFchbgQXPWVLw115Wc3iz/SiteLog) — one file,
  three pages: `Wireframes` (low-fi, M1), `Components`, `Screens`
- **Export:** [`docs/prototype.pdf`](./prototype.pdf) — 46 pages, one screen per page in
  the order of the [screen index](#screen-index), the component set on the last page
  (raster export at 2×, so the file stays small enough for the repository)
- **Low-fi record:** [wireframes](./design/wireframes.md) and
  [`docs/prototype/wireframes.pdf`](./prototype/wireframes.pdf)

## Starting point and tool

The screens were designed in Figma from the low-fi wireframes and the domain model. No UI kit
was imported. Every screen follows the domain rules (field names from the migrations, the three
daily log states, the four incident statuses, AR3 KPIs only) and was checked field by field
against the model. The `Components` page holds Figma components with variants (component sets)
built from the elements the screens repeat.

## Layout: why a left sidebar and a parent-first structure

| Decision | Why | Rejected alternative |
|---|---|---|
| Persistent **left sidebar** (Sites, Incidents, Assisted reporting, Compliance; Admin: Users, Crews) with the user, role and language switcher at the bottom | Four to six top-level areas fit vertically and stay visible on a 1440 screen; the role is always visible, which matters because every role sees different actions | Top navigation bar: runs out of width once the Croatian labels ("Potpomognuto izvješćivanje") and the admin items are added |
| **Site is the entry point**; a site detail has tabs Overview / Daily logs / Incidents / Compliance | Mirrors the aggregate: `Site 1─* DailyLog 1─* WorkEntry`, `Site 1─* Incident`. Breadcrumbs (`Sites / Riverside Tower / DL-…`) show where a record belongs | Flat "all daily logs" list across sites: loses the site context the Site Manager works in |
| **One form for the whole daily log** (header + work entries + nested crew rows) | The examiner checks parent-child CRUD on screens; editing a log in one place matches one `@Transactional` save and one optimistic-lock version | One page per work entry: more round trips, and a half-saved log on a construction site is worse than a longer form |
| **Detail page owns the workflow actions** (Sign off, Request reopen, Approve/Reject, Start review, Close) | Actions depend on status and role; keeping them on the detail page makes the state machine visible and easy to hide with `sec:authorize` | Inline actions in list rows: too easy to sign off the wrong log |
| **Modal** only for short focused tasks (reopen reason, corrective action) | One field or three; the context behind stays visible | Separate pages: lose the record the user is correcting |
| **Two-column AI screen**: note on the left, draft on the right, with a permanent "Needs supervisor signature" banner | The user compares the source text with each proposed field before signing; the banner states that nothing is saved without a supervisor | Wizard with hidden source text: encourages accepting the draft without reading the note |

## Visual language

### Palette (design tokens)

The tokens below are the values measured in the exported prototype; they become CSS variables in
`static/css/app.css` with the same names.

| Token | Value | Use |
|---|---|---|
| `--color-accent` | `#C1410C` | Primary button, active nav marker, links on buttons — safety orange, readable with white text |
| `--color-accent-text` | `#9A3412` | Orange text on light backgrounds (breadcrumbs, links) |
| `--color-danger` | `#B91C1C` | Destructive button (delete, reject) |
| `--color-sidebar` | `#1C1816` | Sidebar and mobile header background (warm near-black) |
| `--color-bg` | `#F4F4F4` | Page background |
| `--color-surface` | `#FFFFFF` | Cards, tables, inputs |
| `--color-text` | `#16211D` | Headings and body text |
| `--color-text-muted` | `#56665F` | Secondary text and field labels (darkened from the prototype, see the contrast check) |
| `--color-error` | `#991B1B` | Validation messages, error borders |

Orange was chosen because it is the colour of safety signage and high-visibility clothing on
construction sites; it is used sparingly (one primary action per screen) so it keeps meaning
"do this". Status information never relies on colour alone: chips carry a text label and a dot,
flash messages an icon (✓ ! i) and a title.

### Typography and spacing

The prototype uses one typeface, **Manrope** (a geometric sans-serif, free on Google Fonts, with
the Latin Extended glyphs Croatian needs: č ć đ š ž), for everything, with uppercase letter-spaced "eyebrow" labels (`DAILY LOG ·
DL-…`, `LOG OVERVIEW`) above headings and large headings for the record being viewed (the date
of a daily log, the incident title). The prototype frames use a small type scale (body 10–12 px
at 1440); the implementation raises it to a 14 px base and 12 px minimum (chips, captions) for
readability on site tablets, keeping the same hierarchy. Spacing follows a 4 px grid (`--space-1`
= 4 px … `--space-6` = 32 px).

## Components and variants

The `Components` page holds the reusable set (component sets drawn with the dashed purple
outline in Figma). Each one maps to a Thymeleaf fragment in `templates/fragments/`.

| Component | Variants | Fragment |
|---|---|---|
| Button | primary / secondary / danger × default / disabled | `button` |
| Text input | default / focused / error (message below) | `input` |
| Select, Textarea | default | `input` (select, textarea) |
| Table row | header, default, hover | `table-row` |
| Daily log status chip | Draft / Signed off / Reopened | `chip` |
| Incident status chip | Reported / Under review / Signed off / Closed | `chip` |
| Corrective action status chip | Open / Done / Overdue | `chip` |
| Severity chip | Low / Medium / High / Critical | `chip` |
| Flash message | success / error / info, dismissible | `flash` |
| Crew assignment row | Crew, Hours worked, Worker count, Task, remove | `crew-row` |
| Toggle | on / off (user and crew Active) | `toggle` |
| Modal | default / with validation error | `modal` |
| Sidebar navigation, notification bell and panel | used on every desktop screen | `navbar`, `notifications` |

## Accessibility: WCAG AA contrast check

Ratios measured on the exported prototype (text colour against its own background, WCAG 2.x
relative luminance; colours sampled from the export and rounded to the palette value). Target: **4.5:1** for normal text, 3:1 for large text (≥ 24 px, or ≥ 18.66 px
bold). Every chip family uses a dark text on a light tint of the same hue, so the label is the
information and the colour only reinforces it.

| Element | Text | Background | Ratio | Result |
|---|---|---|---|---|
| Primary button | `#FFFFFF` | `#C1410C` | 5.21 | Pass |
| Danger button | `#FFFFFF` | `#B91C1C` | 6.52 | Pass |
| Secondary button | `#282423` | `#FFFFFF` | 15.36 | Pass |
| Chip Draft | `#44403C` | `#F4F4F4` | 9.45 | Pass |
| Chip Signed off / Done | `#14532D` | `#DCFCE7` | 8.34 | Pass |
| Chip Reopened / High | `#9A3412` | `#FFEDD5` | 6.37 | Pass |
| Chip Reported / Open | `#1E3A8A` | `#DBEAFE` | 8.46 | Pass |
| Chip Under review / Medium | `#713F12` | `#FEF3C7` | 7.76 | Pass |
| Chip Closed | `#3F3F46` | `#E4E4E7` | 8.23 | Pass |
| Chip Overdue / Critical | `#7F1D1D` | `#FEE2E2` | 8.29 | Pass |
| Chip Low | `#164E63` | `#CFFAFE` | 8.20 | Pass |
| Flash success title | `#14532D` | `#F0FDF4` | 8.81 | Pass |
| Validation message | `#991B1B` | `#FFFFFF` | 8.34 | Pass |
| Breadcrumb link | `#9A3412` | `#F4F4F4` | 6.65 | Pass |
| Heading | `#16211D` | `#F4F4F4` | 15.04 | Pass |
| Sidebar item | `#A8BCB6` | `#1C1816` | 8.84 | Pass |
| Sidebar section label | `#6E8A83` | `#1C1816` | 4.72 | Pass |
| Muted text (prototype) | `#70807C` | `#F4F4F4` | 3.77 | **Fail** → `#56665F` = 5.52 |
| Field label (prototype) | `#678077` | `#FFFFFF` | 4.26 | **Fail** → `#56665F` = 6.07 |
| Placeholder (prototype) | `#929997` | `#FBFBFB` | 2.81 | **Fail** → `#6B7280` = 4.67 |
| Disabled button | `#77706B` | `#E6E4E4` | 3.84 | Exempt (inactive control) |

Severity is the one place where colour is semantically loaded. The four severity chips use four
distinct hues (cyan, yellow, orange, red) so they can be told apart, and each still has its text
label, so the scale also works for colour-blind users and in greyscale print.

Other accessibility choices: visible focus state on inputs (orange border, see the input
variants), error messages placed under the field and repeated in a summary flash at the top of
the form, buttons and links with text (no icon-only actions except the remove "×", which gets an
`aria-label`), and the language set on `<html lang>` from the active locale.

## Croatian labels

The interface ships in English and Croatian, and Croatian labels are typically 20–40 % longer
("Save changes" → "Spremi promjene", "Worker count" → "Broj radnika", "End date of corrective
action" → "Datum završetka korektivne radnje"). The component set shows both languages side by
side: buttons hug their label, form labels wrap above the control instead of truncating, and
crew-row columns have a minimum width rather than a fixed one. No text is placed in fixed-width
boxes, so the CSS uses `min-width`, wrapping and flex/grid instead of fixed widths.

## Mobile (390 px)

Site Managers fill daily logs on the site, often on a phone. The mobile frames cover the list and
the form required by the assignment, plus login and the sites list:

- the sidebar becomes a **bottom tab bar** (Sites, Incidents, Assist, Reports) and a dark header
  with a back arrow;
- the daily log **table becomes stacked cards** (date block, weather, entries, author, status chip)
  with status filter pills;
- the **form is one column**; each crew assignment row stacks its fields (Crew, then Hours worked
  and Worker count side by side, then Task) and the **Save button is sticky** at the bottom with
  the work entry count.

In CSS this is one breakpoint (`max-width: 640px`); the templates are the same at both widths.

## Layout by role

Actions the current user cannot perform are **not shown** (implemented with `sec:authorize` and
service checks, never only in the UI):

| Role | Sees |
|---|---|
| Site Manager | New daily log, Edit / Sign off on Draft and Reopened logs of own sites, Request reopen on signed-off logs, Report incident, Assisted reporting (write note) |
| Safety Supervisor | Approve / Reject on a pending reopen, Sign off for High/Critical incidents, Sign and create incident on an AI draft, corrective actions |
| Inspector | Read-only screens and the Compliance report; no action buttons |
| Admin | Extra sidebar group *Admin* with Users and Crews; New site / Site settings |

The incident detail states the rule in the interface ("Sign off is available to Safety
Supervisors only") and the AI screen keeps the "Needs supervisor signature" banner on every draft,
because AI output never becomes an incident without a supervisor's review and signature.

## States covered

| State | Where |
|---|---|
| List with data | Site daily logs (p. 31), incidents (p. 15), users (p. 36), crews (p. 40), sites (p. 28) |
| Empty | Notifications panel "You're all caught up" (p. 44); daily logs list empty state "No daily logs yet" (p. 31b) |
| Loading | AI screen "Analysing note…" with the button disabled (p. 23) |
| Error | Form validation errors (pp. 3, 18, 21, 39, 42), reopen reason required (p. 7), assistant unavailable with manual fallback (p. 26), AI field not extracted (p. 25) |
| Locked | Signed-off daily log, read-only with "Request reopen" as the only action (p. 5) |
| Workflow | Daily log Draft (p. 10), reopen pending (p. 8), Reopened with reason and approver (p. 9) |

### Validation messages

The texts in the prototype are the real messages; they are copied unchanged into
`messages.properties` (and translated in `messages_hr.properties`):

`Date is required` · `A reason is required to reopen a signed-off log` ·
`Title is required` · `Occurrence date and time are required` · `Description is required` ·
`Root cause is required` · `Enter witnesses or "None"` · `Enter treatment or "None"` ·
`Select Yes or No` · `Action is required` · `Due date cannot be in the past` ·
`Email is already used` · `Name is already used` · `Hours worked must be between 0 and 24`
(from the brief; to be drawn on a crew row).

## What changed from the wireframes

| Wireframes (M1, rev. 3) | High fidelity (M2) | Reason |
|---|---|---|
| Greyscale boxes, one frame per screen with a "frame variant" bar | Every state is its own frame (46 exported pages) | States must be exportable and clickable; the bar hid Draft, Reopened and the modal error |
| Sidebar listed the wireframe screens 01–09 | Real app navigation, Admin group, user menu with EN / HR and Log out | Navigation becomes the implemented layout |
| Site detail with three tabs | Overview tab with the four KPI cards plus Daily logs / Incidents / Compliance tabs | Site Manager lands on what needs attention (awaiting sign-off, open incidents) |
| AI review panel empty ("No analysis yet") | Idle, loading, populated, needs-manual-input and unavailable states | The AI call can be slow or fail; each outcome needs a screen |
| Incident form only (Low) | Low, High with investigation fields, High with errors, incident detail with corrective actions and a corrective action modal | AR2 (severity workflow) and the corrective action child list |
| No profile, admin or notification screens | My profile, Users, Crews, Site form, notifications panel | Users are created by the Admin (no self-registration); scheduled jobs surface as in-app notifications |
| Compliance KPI caption "14 of 17 incidents" | Counts corrective actions | % closed on time is about actions, not incidents |

## Out of scope (deliberately not designed)

- **No self-registration and no "forgot password"**: there is no e-mail in the system; the Admin
  creates users with an initial password and users change it in My profile.
- **No e-mail notifications** and **no global search**: notifications are in-app (bell, panel and
  list); the sites list has its own filter.
- **No site status field and no safety supervisor per site**: a site is `code`, `name`, `address`,
  an optional `description`, `start_date`, optional `end_date` (a site with an end date shows as
  *Finished*) and its Site Manager.
- **No temperature, no shift supervisor, no priority, no incident location**: the domain model
  prevails (decided in the wireframe review).

## From prototype to implementation

Design and model evolved together. The M2 review compared every frame with the migrations and
corrected the prototype in Figma (login without password recovery, site fields, site cards without
a progress figure, daily log timeline and weather values, profile fields, AI panel, compliance
caption), so the screens and the model now describe the same system. The influence also went the
other way:

**The prototype changed the model**

- **Site description**: the site form showed a description and it was worth keeping, so
  `site.description` (optional) was added to the schema.
- **In-app notifications**: the bell drawn in the top bar became a real feature — a
  `notification` table filled by the scheduled jobs and by workflow events, shown in the panel.

**The implementation refines the prototype**

| Topic | Prototype | Implementation | Reason |
|---|---|---|---|
| Navigation by role | Every frame shows the full sidebar, including the *Admin* group | The *Admin* group and admin actions are rendered only for the Admin (`sec:authorize`) | Each frame shows one screen; the template adapts to the signed-in role |
| Workflow actions | Incident detail shows Start review, Sign off and Close together | Only the next valid action for the status and role | Actions follow the severity workflow (AR2) |
| Change password | My profile shows the account fields | A *Change password* section is added to the profile (M3) | Users created by the Admin must replace their initial password |
| Contrast | Secondary grey text `#70807C` (3.77:1), placeholders (2.81:1) | `#56665F` (5.5:1) and `#6B7280` | WCAG AA — see [contrast check](#accessibility-wcag-aa-contrast-check) |
| Type size | 10–12 px body text at 1440 | 14 px base, 12 px minimum | Readability on site tablets |
| Data | Illustrative sites and people ("Riverside Tower") | Seed data (ZG-01 Lanište, ZG-02 Radnička, ST-01 Hotel Žnjan) | The demo runs on the real seed |

These rows are repeated, per screen, in the Figma frame → template table in the README.

## Screen index

PDF page → prototype frame → planned Thymeleaf template. The final mapping, with the implementation notes
per screen, is the Figma frame → template table in the README.

| Page | Frame | Planned template |
|---|---|---|
| 1 | Access – Login | `auth/login.html` |
| 2–3 | Daily logs – New daily log form, validation errors | `dailylog/form.html` |
| 4 | Site overview with "Daily log saved" flash | `site/detail.html` + `fragments/flash` |
| 5, 8–10 | Daily log detail – Signed off, reopen pending, Reopened, Draft | `dailylog/detail.html` |
| 6–7 | Request reopen modal, with error | `fragments/modal` in `dailylog/detail.html` |
| 11–14 | Mobile 390 – login, sites, daily logs cards, daily log form | same templates, responsive CSS |
| 15 | Incidents list | `incident/list.html` |
| 16–18 | Report incident – Low, High, High with errors | `incident/form.html` |
| 19–21 | Incident detail, corrective action modal, with errors | `incident/detail.html` |
| 22–26 | Assisted reporting – idle, loading, populated, manual input, unavailable | `ai/shift-note.html` |
| 27, 33 | Compliance report (global and site tab) | `report/compliance.html` |
| 28 | Sites list | `site/list.html` |
| 29, 31, 31b, 32 | Site detail – overview, daily logs (with data and empty), incidents | `site/detail.html` |
| 30, 34 | Site settings, New site | `site/form.html` |
| 35 | My profile | `account/profile.html` |
| 36–39 | Users list, detail, form, form error | `admin/user/list.html`, `detail.html`, `form.html` |
| 40–42 | Crews list, form, form error | `crew/list.html`, `crew/form.html` |
| 43–44 | Notifications panel, empty | `fragments/notifications` |
| 45 | Component set | `fragments/*`, `static/css/app.css` |

## Clickable flow

Presentation mode starts at **Flow 1 – Sites**: Sites list → New site, and Sites list → Site
detail → Site settings (edit site), with the sidebar links and the notification panel (overlay)
wired, which gives the clickable flow of at least four steps the assignment asks for. The daily
log flow (daily logs list → New daily log → validation errors → saved → detail) is drawn frame by
frame and can be followed in the PDF in the same order.
