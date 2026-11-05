-- V4: demo data (runs in every environment, including production, so the demo accounts exist
-- on the deployed application).
--
-- Demo accounts — all share the documented demo password "SiteLog-demo-2026"
-- (BCrypt, strength 10, generated with Spring Security's BCryptPasswordEncoder):
--   admin@sitelog.test           ADMIN
--   manager@sitelog.test         SITE_MANAGER (sites ZG-01, ZG-02)
--   manager.split@sitelog.test   SITE_MANAGER (site ST-01)
--   supervisor@sitelog.test      SAFETY_SUPERVISOR
--   inspector@sitelog.test       INSPECTOR
--
-- Rows are linked through natural keys (user email, site code, crew name, site + log date,
-- work entry description) instead of hard-coded ids. Dates are fixed (October/November 2026)
-- so tests do not depend on the current date.
--
-- Signed-off logs are inserted as DRAFT, filled with work entries and crew assignments, and
-- only then updated to SIGNED_OFF: the lock triggers from V3 reject children of a signed-off
-- log, exactly as they will for the application.

-- ---------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------
INSERT INTO app_user (email, password_hash, full_name, preferred_language, active, role_id)
SELECT v.email, '$2a$10$BaoKGdeDLDnVSb9ZnN9tNOzZgJYfoVbkeXRApfP6KkRiL/iP7MoqK', v.full_name, v.lang, true, r.id
FROM (VALUES
    ('admin@sitelog.test',         'Ana Horvat',        'en', 'ADMIN'),
    ('manager@sitelog.test',       'Marko Kovačević',   'hr', 'SITE_MANAGER'),
    ('manager.split@sitelog.test', 'Luka Matić',        'hr', 'SITE_MANAGER'),
    ('supervisor@sitelog.test',    'Ivana Babić',       'en', 'SAFETY_SUPERVISOR'),
    ('inspector@sitelog.test',     'Tomislav Novak',    'en', 'INSPECTOR')
) AS v (email, full_name, lang, role_name)
JOIN role r ON r.name = v.role_name;

-- ---------------------------------------------------------------------------
-- Sites
-- ---------------------------------------------------------------------------
INSERT INTO site (code, name, address, description, start_date, end_date, manager_id)
SELECT v.code, v.name, v.address, v.description, v.start_date::date, v.end_date::date, u.id
FROM (VALUES
    ('ZG-01', 'Residential Complex Lanište', 'Lanište 12, 10000 Zagreb',
     'Two six-storey residential buildings with 84 apartments and a shared underground garage.',
     '2026-03-02', NULL,         'manager@sitelog.test'),
    ('ZG-02', 'Office Building Radnička',    'Radnička cesta 48, 10000 Zagreb',
     'Nine-storey office building with a reinforced concrete frame and a glass curtain wall.',
     '2026-05-04', NULL,         'manager@sitelog.test'),
    ('ST-01', 'Hotel Žnjan',                 'Put Žnjana 20, 21000 Split',
     NULL,
     '2026-04-13', '2027-06-30', 'manager.split@sitelog.test')
) AS v (code, name, address, description, start_date, end_date, manager_email)
JOIN app_user u ON u.email = v.manager_email;

-- ---------------------------------------------------------------------------
-- Crews
-- ---------------------------------------------------------------------------
INSERT INTO crew (name, company, trade, active) VALUES
    ('Kovač Formwork',        'Kovač Gradnja d.o.o.',     'Formwork',    true),
    ('Babić Concrete',        'Babić Beton d.o.o.',       'Concrete',    true),
    ('Jurić Steel Fixers',    'Jurić Armatura d.o.o.',    'Rebar',       true),
    ('Elektro Novak',         'Elektro Novak d.o.o.',     'Electrical',  true),
    ('Dalmacija Scaffolding', 'Skele Dalmacija d.o.o.',   'Scaffolding', true),
    ('Perić Plumbing',        'Perić Instalacije d.o.o.', 'Plumbing',    false);

-- ---------------------------------------------------------------------------
-- Daily logs (all start as DRAFT, see header)
-- ---------------------------------------------------------------------------
INSERT INTO daily_log (site_id, log_date, status, revision, weather, notes)
SELECT s.id, v.log_date::date, 'DRAFT', 0, v.weather, v.notes
FROM (VALUES
    ('ZG-01', '2026-10-26', 'SUNNY',  'Formwork for level 3 slab started.'),
    ('ZG-01', '2026-10-27', 'CLOUDY', 'Rebar fixing on level 3; one fall incident reported.'),
    ('ZG-01', '2026-10-28', 'RAIN',   'Concrete pour postponed to the afternoon because of rain.'),
    ('ZG-01', '2026-10-29', 'CLOUDY', 'Level 3 slab curing; electrical rough-in on level 1.'),
    ('ZG-02', '2026-10-27', 'WIND',   'Crane work stopped for two hours due to wind.'),
    ('ZG-02', '2026-10-28', 'FOG',    'Late start because of fog; block deliveries.'),
    ('ST-01', '2026-10-27', 'SUNNY',  'Scaffolding on the east facade.'),
    ('ST-01', '2026-10-28', 'SUNNY',  'Temporary power distribution replaced.')
) AS v (site_code, log_date, weather, notes)
JOIN site s ON s.code = v.site_code;

INSERT INTO work_entry (daily_log_id, description, location, start_time, end_time)
SELECT dl.id, v.description, v.location, v.start_time::time, v.end_time::time
FROM (VALUES
    ('ZG-01', '2026-10-26', 'Formwork assembly for level 3 slab', 'Building A, level 3', '07:00', '15:30'),
    ('ZG-01', '2026-10-26', 'Rebar delivery and storage',         'Storage yard',        '08:00', '11:00'),
    ('ZG-01', '2026-10-27', 'Rebar fixing for level 3 slab',      'Building A, level 3', '07:00', '16:00'),
    ('ZG-01', '2026-10-28', 'Concrete pour level 3 slab',         'Building A, level 3', '12:00', '19:00'),
    ('ZG-01', '2026-10-29', 'Electrical rough-in',                'Building A, level 1', '07:30', '15:30'),
    ('ZG-02', '2026-10-27', 'Steel column erection',              'Core B',              '07:00', '15:00'),
    ('ZG-02', '2026-10-28', 'Masonry block delivery',             'Ground floor',        '09:30', '14:00'),
    ('ST-01', '2026-10-27', 'Scaffolding erection east facade',   'East facade',         '06:30', '14:30'),
    ('ST-01', '2026-10-28', 'Replace temporary distribution board', 'Site office',       '07:00', '12:00')
) AS v (site_code, log_date, description, location, start_time, end_time)
JOIN site s ON s.code = v.site_code
JOIN daily_log dl ON dl.site_id = s.id AND dl.log_date = v.log_date::date;

INSERT INTO crew_assignment (work_entry_id, crew_id, hours_worked, worker_count, task)
SELECT we.id, c.id, v.hours, v.workers, v.task
FROM (VALUES
    ('ZG-01', '2026-10-26', 'Formwork assembly for level 3 slab', 'Kovač Formwork',        8.5, 6, 'Slab formwork'),
    ('ZG-01', '2026-10-26', 'Rebar delivery and storage',         'Jurić Steel Fixers',    3.0, 2, 'Unloading'),
    ('ZG-01', '2026-10-27', 'Rebar fixing for level 3 slab',      'Jurić Steel Fixers',    9.0, 5, 'Rebar fixing'),
    ('ZG-01', '2026-10-27', 'Rebar fixing for level 3 slab',      'Kovač Formwork',        4.0, 2, 'Edge formwork'),
    ('ZG-01', '2026-10-28', 'Concrete pour level 3 slab',         'Babić Concrete',        7.0, 8, 'Pour and vibrate'),
    ('ZG-01', '2026-10-28', 'Concrete pour level 3 slab',         'Kovač Formwork',        7.0, 2, 'Formwork watch'),
    ('ZG-01', '2026-10-29', 'Electrical rough-in',                'Elektro Novak',         8.0, 3, 'Conduits and boxes'),
    ('ZG-02', '2026-10-27', 'Steel column erection',              'Jurić Steel Fixers',    6.0, 4, 'Column erection'),
    ('ZG-02', '2026-10-28', 'Masonry block delivery',             'Babić Concrete',        4.5, 3, 'Unloading blocks'),
    ('ST-01', '2026-10-27', 'Scaffolding erection east facade',   'Dalmacija Scaffolding', 8.0, 5, 'Scaffold erection'),
    ('ST-01', '2026-10-28', 'Replace temporary distribution board', 'Elektro Novak',       5.0, 2, 'Board replacement')
) AS v (site_code, log_date, description, crew_name, hours, workers, task)
JOIN site s ON s.code = v.site_code
JOIN daily_log dl ON dl.site_id = s.id AND dl.log_date = v.log_date::date
JOIN work_entry we ON we.daily_log_id = dl.id AND we.description = v.description
JOIN crew c ON c.name = v.crew_name;

-- ---------------------------------------------------------------------------
-- Sign-off (children are complete, now the logs become read-only)
-- ---------------------------------------------------------------------------
UPDATE daily_log dl
SET status = 'SIGNED_OFF',
    signed_off_by = u.id,
    signed_off_at = v.signed_off_at::timestamptz,
    version = dl.version + 1
FROM (VALUES
    ('ZG-01', '2026-10-26', 'manager@sitelog.test',       '2026-10-26 17:30:00+01'),
    ('ZG-01', '2026-10-27', 'manager@sitelog.test',       '2026-10-27 17:45:00+01'),
    ('ZG-01', '2026-10-28', 'manager@sitelog.test',       '2026-10-28 19:30:00+01'),
    ('ZG-02', '2026-10-27', 'manager@sitelog.test',       '2026-10-27 16:00:00+01'),
    ('ST-01', '2026-10-27', 'manager.split@sitelog.test', '2026-10-27 15:10:00+01')
) AS v (site_code, log_date, signer_email, signed_off_at)
JOIN site s ON s.code = v.site_code
JOIN app_user u ON u.email = v.signer_email
WHERE dl.site_id = s.id AND dl.log_date = v.log_date::date;

-- ---------------------------------------------------------------------------
-- Reopen requests: one APPROVED (log reopened), one REJECTED (log stays signed off),
-- one PENDING (waiting for the supervisor)
-- ---------------------------------------------------------------------------
INSERT INTO log_reopen_request (daily_log_id, reason, requested_by, approved_by, requested_at, decided_at, status)
SELECT dl.id, v.reason, req.id, dcd.id, v.requested_at::timestamptz, v.decided_at::timestamptz, v.status
FROM (VALUES
    ('ZG-01', '2026-10-28', 'Concrete pour hours were recorded for the wrong crew.',
        'manager@sitelog.test', 'supervisor@sitelog.test', '2026-10-29 08:15:00+01', '2026-10-29 09:00:00+01', 'APPROVED'),
    ('ZG-02', '2026-10-27', 'Want to add a note about the crane stop.',
        'manager@sitelog.test', 'supervisor@sitelog.test', '2026-10-28 10:00:00+01', '2026-10-28 11:30:00+01', 'REJECTED'),
    ('ST-01', '2026-10-27', 'Worker count for the scaffolding crew is wrong (6, not 5).',
        'manager.split@sitelog.test', NULL, '2026-10-29 07:40:00+01', NULL, 'PENDING')
) AS v (site_code, log_date, reason, requester_email, decider_email, requested_at, decided_at, status)
JOIN site s ON s.code = v.site_code
JOIN daily_log dl ON dl.site_id = s.id AND dl.log_date = v.log_date::date
JOIN app_user req ON req.email = v.requester_email
LEFT JOIN app_user dcd ON dcd.email = v.decider_email;

-- The approved request reopens its log: SIGNED_OFF -> REOPENED, revision + 1
-- (the only change the lock trigger accepts on a signed-off log).
UPDATE daily_log dl
SET status = 'REOPENED',
    revision = dl.revision + 1,
    version = dl.version + 1
FROM site s
WHERE dl.site_id = s.id AND s.code = 'ZG-01' AND dl.log_date = DATE '2026-10-28';

-- ---------------------------------------------------------------------------
-- Incidents: every severity and every status
-- ---------------------------------------------------------------------------
INSERT INTO incident (site_id, daily_log_id, occurred_at, title, description, severity, category, status,
                      root_cause, witnesses, medical_treatment, authority_notified,
                      reported_by, signed_off_by, signed_off_at)
SELECT s.id, dl.id, v.occurred_at::timestamptz, v.title, v.description, v.severity, v.category, v.status,
       v.root_cause, v.witnesses, v.medical_treatment, v.authority_notified,
       rep.id, so.id, v.signed_off_at::timestamptz
FROM (VALUES
    ('ZG-01', '2026-10-29', '2026-10-29 10:20:00+01', 'Worker slipped on wet stairs',
        'A worker slipped on the wet temporary stairs to level 1; no injury.',
        'LOW', 'SLIP_TRIP', 'REPORTED',
        NULL, NULL, NULL, NULL::boolean,
        'manager@sitelog.test', NULL, NULL),
    ('ZG-02', NULL, '2026-10-28 11:05:00+01', 'Back strain while unloading blocks',
        'A worker strained his back while unloading masonry blocks by hand.',
        'MEDIUM', 'MANUAL_HANDLING', 'UNDER_REVIEW',
        NULL, NULL, NULL, NULL,
        'manager@sitelog.test', NULL, NULL),
    ('ZG-01', '2026-10-27', '2026-10-27 13:40:00+01', 'Fall from slab edge',
        'A steel fixer fell about 2 m from the level 3 slab edge onto the level 2 scaffold.',
        'HIGH', 'FALL_FROM_HEIGHT', 'SIGNED_OFF',
        'Edge protection removed for rebar delivery and not reinstalled.',
        'Ivan Perić (foreman), Josip Knežević',
        'First aid on site, X-ray at KB Dubrava; bruised shoulder.', true,
        'manager@sitelog.test', 'supervisor@sitelog.test', '2026-10-28 16:00:00+01'),
    ('ST-01', '2026-10-27', '2026-10-27 09:15:00+01', 'Electric shock from distribution board',
        'An electrician received a shock from a damaged temporary distribution board.',
        'CRITICAL', 'ELECTRICAL', 'CLOSED',
        'Board housing cracked by a forklift; residual current device faulty.',
        'Ante Vuković, Mate Šimić',
        'Hospitalised for 24 h observation at KBC Split.', true,
        'manager.split@sitelog.test', 'supervisor@sitelog.test', '2026-10-28 18:00:00+01'),
    ('ST-01', NULL, '2026-10-30 14:10:00+01', 'Scaffold tube dropped from level 4',
        'A scaffold tube fell from level 4 into the access zone; nobody was hit.',
        'HIGH', 'STRUCK_BY_OBJECT', 'UNDER_REVIEW',
        NULL, 'Luka Matić', NULL, NULL,
        'manager.split@sitelog.test', NULL, NULL),
    ('ZG-02', '2026-10-27', '2026-10-27 12:30:00+01', 'Crane load swung near workers',
        'Wind gust swung a crane load close to the steel crew; work stopped.',
        'LOW', 'NEAR_MISS', 'SIGNED_OFF',
        NULL, NULL, NULL, NULL,
        'manager@sitelog.test', 'manager@sitelog.test', '2026-10-27 16:30:00+01')
) AS v (site_code, log_date, occurred_at, title, description, severity, category, status,
        root_cause, witnesses, medical_treatment, authority_notified,
        reporter_email, signer_email, signed_off_at)
JOIN site s ON s.code = v.site_code
LEFT JOIN daily_log dl ON dl.site_id = s.id AND dl.log_date = v.log_date::date
JOIN app_user rep ON rep.email = v.reporter_email
LEFT JOIN app_user so ON so.email = v.signer_email;

INSERT INTO incident_affected_crew (incident_id, crew_id)
SELECT i.id, c.id
FROM (VALUES
    ('Worker slipped on wet stairs',           'Elektro Novak'),
    ('Back strain while unloading blocks',     'Babić Concrete'),
    ('Fall from slab edge',                    'Jurić Steel Fixers'),
    ('Fall from slab edge',                    'Kovač Formwork'),
    ('Electric shock from distribution board', 'Elektro Novak'),
    ('Scaffold tube dropped from level 4',     'Dalmacija Scaffolding'),
    ('Crane load swung near workers',          'Jurić Steel Fixers')
) AS v (incident_title, crew_name)
JOIN incident i ON i.title = v.incident_title
JOIN crew c ON c.name = v.crew_name;

-- ---------------------------------------------------------------------------
-- Corrective actions: OPEN, DONE and OVERDUE; one without an assignee
-- ---------------------------------------------------------------------------
INSERT INTO corrective_action (incident_id, description, assignee_id, due_date, status, completed_at)
SELECT i.id, v.description, u.id, v.due_date::date, v.status, v.completed_at::timestamptz
FROM (VALUES
    ('Fall from slab edge', 'Reinstall edge protection on the level 3 slab and add a daily check.',
        'manager@sitelog.test', '2026-10-30', 'DONE', '2026-10-30 15:00:00+01'),
    ('Fall from slab edge', 'Retrain the steel fixing crew on working near open edges.',
        'supervisor@sitelog.test', '2027-01-31', 'OPEN', NULL),
    ('Electric shock from distribution board', 'Replace the damaged distribution board and test all RCDs.',
        'manager.split@sitelog.test', '2026-10-28', 'DONE', '2026-10-28 12:00:00+01'),
    ('Back strain while unloading blocks', 'Provide pallet trucks and lifting aids for block deliveries.',
        'manager@sitelog.test', '2026-12-15', 'OPEN', NULL),
    ('Scaffold tube dropped from level 4', 'Install toe boards and debris netting on levels 3 to 5.',
        'manager.split@sitelog.test', '2026-11-02', 'OVERDUE', NULL),
    ('Scaffold tube dropped from level 4', 'Review the exclusion zone below the scaffold.',
        NULL, '2027-02-15', 'OPEN', NULL)
) AS v (incident_title, description, assignee_email, due_date, status, completed_at)
JOIN incident i ON i.title = v.incident_title
LEFT JOIN app_user u ON u.email = v.assignee_email;

-- ---------------------------------------------------------------------------
-- AI incident drafts: one ACCEPTED (became the slip incident), one PENDING_REVIEW,
-- one NEEDS_MANUAL_INPUT
-- ---------------------------------------------------------------------------
INSERT INTO incident_draft (site_id, shift_note, severity, category, affected_crews, immediate_action, status,
                            created_by, reviewed_by, incident_id, created_at, reviewed_at)
SELECT s.id, v.shift_note, v.severity, v.category, v.affected_crews, v.immediate_action, v.status,
       cr.id, rv.id, i.id, v.created_at::timestamptz, v.reviewed_at::timestamptz
FROM (VALUES
    ('ZG-01',
        'Around 10:20 one of the electricians slipped on the wet stairs to level 1. He is fine, no injury. Stairs were wet from the rain.',
        'LOW', 'SLIP_TRIP', 'Elektro Novak', 'Stairs dried and anti-slip mats placed.', 'ACCEPTED',
        'manager@sitelog.test', 'supervisor@sitelog.test', 'Worker slipped on wet stairs',
        '2026-10-29 10:45:00+01', '2026-10-29 12:00:00+01'),
    ('ZG-02',
        'Pallet of blocks tipped while the forklift was lowering it, a few blocks fell next to the Babić crew. Nobody hurt, area closed for 30 min.',
        'MEDIUM', 'STRUCK_BY_OBJECT', 'Babić Concrete', 'Area closed and pallet restacked.', 'PENDING_REVIEW',
        'manager@sitelog.test', NULL, NULL,
        '2026-10-28 15:20:00+01', NULL),
    ('ST-01',
        'Something happened near the hoist this morning, will check with the foreman.',
        NULL, NULL, NULL, NULL, 'NEEDS_MANUAL_INPUT',
        'manager.split@sitelog.test', NULL, NULL,
        '2026-10-30 09:00:00+01', NULL)
) AS v (site_code, shift_note, severity, category, affected_crews, immediate_action, status,
        creator_email, reviewer_email, incident_title, created_at, reviewed_at)
JOIN site s ON s.code = v.site_code
JOIN app_user cr ON cr.email = v.creator_email
LEFT JOIN app_user rv ON rv.email = v.reviewer_email
LEFT JOIN incident i ON i.title = v.incident_title;
