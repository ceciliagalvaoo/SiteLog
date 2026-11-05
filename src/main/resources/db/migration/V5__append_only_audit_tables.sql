-- V5: the Envers audit tables are append-only.
--
-- Why: the audit trail is the evidence for AR1 (what changed in a reopened daily log, by whom,
-- when). If audit rows could be rewritten or deleted, the history of a reopen could be erased.
-- INSERT stays allowed; UPDATE, DELETE and TRUNCATE are rejected on revinfo, daily_log_aud,
-- work_entry_aud and crew_assignment_aud.
--
-- This fits Envers with its default strategy (DefaultAuditStrategy): it only persists new
-- audit rows (OrmTools.saveData -> persist/insert) and never updates existing ones.
-- ValidityAuditStrategy would NOT work with these triggers: it UPDATEs the previous audit row
-- to set REVEND, so it is deliberately not used (keep
-- hibernate.envers.audit_strategy at its default).
--
-- Every violation raises SQLSTATE P0001 (raise_exception) with MESSAGE 'audit_append_only',
-- the same convention as 'daily_log_locked' in V3.
--
-- Not covered (on purpose): the table owner can still DISABLE TRIGGER or drop the table, and a
-- superuser can bypass triggers with session_replication_role = replica. These triggers stop
-- application code and ordinary SQL, not a database administrator.

CREATE FUNCTION audit_append_only_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = 'P0001',
        MESSAGE = 'audit_append_only',
        DETAIL  = format('%s on %s rejected', TG_OP, TG_TABLE_NAME),
        HINT    = 'Audit tables only accept INSERT.';
END;
$$;

-- Row-level: UPDATE and DELETE of any existing audit row.
CREATE TRIGGER trg_revinfo_append_only
    BEFORE UPDATE OR DELETE ON revinfo
    FOR EACH ROW EXECUTE FUNCTION audit_append_only_guard();

CREATE TRIGGER trg_daily_log_aud_append_only
    BEFORE UPDATE OR DELETE ON daily_log_aud
    FOR EACH ROW EXECUTE FUNCTION audit_append_only_guard();

CREATE TRIGGER trg_work_entry_aud_append_only
    BEFORE UPDATE OR DELETE ON work_entry_aud
    FOR EACH ROW EXECUTE FUNCTION audit_append_only_guard();

CREATE TRIGGER trg_crew_assignment_aud_append_only
    BEFORE UPDATE OR DELETE ON crew_assignment_aud
    FOR EACH ROW EXECUTE FUNCTION audit_append_only_guard();

-- Statement-level: TRUNCATE does not fire row triggers, so it needs its own trigger.
CREATE TRIGGER trg_revinfo_no_truncate
    BEFORE TRUNCATE ON revinfo
    FOR EACH STATEMENT EXECUTE FUNCTION audit_append_only_guard();

CREATE TRIGGER trg_daily_log_aud_no_truncate
    BEFORE TRUNCATE ON daily_log_aud
    FOR EACH STATEMENT EXECUTE FUNCTION audit_append_only_guard();

CREATE TRIGGER trg_work_entry_aud_no_truncate
    BEFORE TRUNCATE ON work_entry_aud
    FOR EACH STATEMENT EXECUTE FUNCTION audit_append_only_guard();

CREATE TRIGGER trg_crew_assignment_aud_no_truncate
    BEFORE TRUNCATE ON crew_assignment_aud
    FOR EACH STATEMENT EXECUTE FUNCTION audit_append_only_guard();
