package hr.algebra.sitelog.incident.service;

import java.time.LocalDate;

import hr.algebra.sitelog.dailylog.domain.LogStatus;

/** A daily log offered in the incident form ("linked daily log", optional). */
public record DailyLogOption(Long id, LocalDate logDate, LogStatus status) {
}
