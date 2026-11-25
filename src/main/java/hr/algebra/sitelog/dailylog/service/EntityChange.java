package hr.algebra.sitelog.dailylog.service;

import java.util.List;

/**
 * What one revision did to one row of the aggregate.
 *
 * @param entity     DAILY_LOG, WORK_ENTRY or CREW_ASSIGNMENT
 * @param changeType ADDED, MODIFIED or DELETED (Envers revtype 0 / 1 / 2)
 * @param label      what the row is, for the reader: work entry description, crew name; null for the log itself
 * @param fields     the fields that differ from the previous revision of the same row
 *                   (all values for ADDED, the last values for DELETED)
 */
public record EntityChange(String entity, Long entityId, String changeType, String label, List<FieldChange> fields) {
}
