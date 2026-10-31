---
title: Overview
sidebar_position: 1
slug: /
description: What SiteLog is, who uses it and where the documentation lives.
---

# SiteLog

SiteLog is a web application for construction sites. Site managers record **daily logs**
(work done, crews on site, hours), and **safety incidents** follow a controlled workflow
from report to sign-off and closure.

Individual project for the course *Rapid Development of Java Applications Using Programming
Frameworks* (Algebra Bernays University, 2026/27), topic P42 — Construction site logs and safety.

## The problem it addresses

A daily log must be **legally final after sign-off** and still **correctable through a
defensible process**. SiteLog locks a log when it is signed off; changing it afterwards
requires a reopen request with a reason, approved by a safety supervisor, and every change
is audited.

## Main capabilities

| Capability | Summary |
|---|---|
| Daily logs | Per site and day: work entries and the crews assigned to each entry (hours worked, worker count, task). Locks after sign-off; audited reopen with a reason. |
| Incidents | Severity `LOW` · `MEDIUM` · `HIGH` · `CRITICAL`. Higher severities require extra fields (root cause, witnesses, medical treatment, authority notified) and a safety supervisor sign-off. Corrective actions with assignee and due date. |
| Compliance report | Per site and period: incidents by severity and category, corrective actions open / completed / overdue, share completed on time. |
| AI incident draft | A free-text shift note is turned into a structured incident draft (severity, category, affected crews, immediate action). A supervisor must review and sign it before it becomes an incident. |
| Scheduled jobs | Daily: flag overdue corrective actions; remind managers about unsigned daily logs. |

Roles: `ADMIN`, `SITE_MANAGER`, `SAFETY_SUPERVISOR`, `INSPECTOR` — see
[Domain model › Roles and capabilities](./architecture/domain-model.md#roles-and-capabilities).

## Technology

Spring Boot 4.1 (Java 25) · Spring Security 7 · Spring Data JPA / Hibernate 7 · Thymeleaf ·
PostgreSQL + Flyway · springdoc-openapi 3.x · Spring AI 2.x with Anthropic Claude (product AI
feature) · JUnit 6, Mockito, Testcontainers · Docker Compose · GitHub Actions.

Hosting: **Render** (application) + **Supabase** (managed PostgreSQL). This documentation
site is published separately on GitHub Pages.

## Status

Milestone M1 (project setup and design). The features above are the planned scope and are
implemented in milestones M2–M4; each page states what already exists.

## Where to go next

- [Domain model](./architecture/domain-model.md) — entities, relations, roles, state machines.
- [ADR-0001 Persistence model](./adr/0001-persistence-model.md) — database, migrations, mapping choices.
- Source code and `README.md` (how to run): [github.com/ceciliagalvaoo/SiteLog](https://github.com/ceciliagalvaoo/SiteLog).
