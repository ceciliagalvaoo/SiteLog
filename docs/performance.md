---
title: Performance
sidebar_position: 6
description: N+1 query on the site's daily log list - how it was reproduced, the SQL before and after the fix, and the measurement method. Draft (M2); the index measurement with EXPLAIN ANALYZE follows in M4.
---

# Performance

:::info Status (2026-11-07, milestone M2 - draft)
This page records the **N+1 problem** of the daily log list, measured on the M2 code. The second
measurement (indexes for the compliance report, `EXPLAIN ANALYZE` before and after on a large
seed) is added in milestone M4.
:::

## 1. N+1 on the site's daily log list

### Where it appears

The site detail page has a *Daily logs* tab: one row per log with its date, status, weather and,
per row, the number of work entries, the number of crews and the total crew hours. Those three
figures live in the children of the aggregate `DailyLog -> WorkEntry -> CrewAssignment -> Crew`.

Every association is `LAZY` and `open-in-view` is off
([ADR-0001](./adr/0001-persistence-model.md), item 5). The natural first implementation loads a
page of `DailyLog` entities and lets the page walk `log.workEntries` -> `entry.assignments` ->
`assignment.crew` to count and sum. Each collection access is a separate query: **one query for
the page, then one per log, then one per work entry** - the classic N+1 (here even N+M+1).

### How it was reproduced

Test `DailyLogListQueryCountTests` (`src/test/java/hr/algebra/sitelog/performance/`), run against
PostgreSQL 17 in Testcontainers with `hibernate.generate_statistics=true`:

- data: one site, **30 daily logs**, 2 work entries per log, 2 crew rows per entry;
- request: first page of 20 logs, sorted by date descending (what the tab shows);
- metric: `Statistics.getPrepareStatementCount()` - JDBC statements prepared by Hibernate while
  building the page; the SQL itself was captured with `logging.level.org.hibernate.SQL=debug`.

### Before: entity page + lazy collections - 64 statements

Repository method `findBySite_Id(siteId, pageable)` (site and signer fetched with an entity
graph), then the loop over the children:

```sql
-- 1x  the page (site and signer are joined, so no N+1 on the to-one side)
select dl1_0.id, dl1_0.log_date, ..., s1_0.code, s1_0.name, ...
from daily_log dl1_0 left join app_user sob1_0 on sob1_0.id = dl1_0.signed_off_by
  join site s1_0 on s1_0.id = dl1_0.site_id
where dl1_0.site_id = ? order by dl1_0.log_date desc fetch first ? rows only   -- page 0: no offset
-- 1x  total for the pager
select count(*) from daily_log dl1_0 where dl1_0.site_id = ?
-- 20x one per log on the page
select we1_0.daily_log_id, we1_0.id, we1_0.description, ... from work_entry we1_0
where we1_0.daily_log_id = ? order by we1_0.start_time, we1_0.id
-- 40x one per work entry
select a1_0.work_entry_id, a1_0.id, a1_0.crew_id, a1_0.hours_worked, ... from crew_assignment a1_0
where a1_0.work_entry_id = ? order by a1_0.id
-- 2x  one per distinct crew (later ones come from the persistence context)
select c1_0.id, c1_0.active, c1_0.company, c1_0.name, c1_0.trade from crew c1_0 where c1_0.id = ?
```

**1 + 1 + 20 + 40 + 2 = 64 statements** for one page. The count grows linearly with the page size
and with the number of work entries per log.

### After: one aggregate query - 2 statements

The list does not need the child entities, only figures computed from them. Repository method
`findSummariesBySiteId(siteId, pageable)` returns a record projection (`DailyLogSummary`) computed
by the database with `GROUP BY`:

```sql
-- 1x  the page with the per-row figures
select dl1_0.id, dl1_0.log_date, dl1_0.status, dl1_0.weather, dl1_0.revision,
       count(distinct we1_0.id), count(distinct a1_0.crew_id), sum(a1_0.hours_worked)
from daily_log dl1_0
left join work_entry we1_0 on dl1_0.id = we1_0.daily_log_id
left join crew_assignment a1_0 on we1_0.id = a1_0.work_entry_id
where dl1_0.site_id = ?
group by dl1_0.id, dl1_0.log_date, dl1_0.status, dl1_0.weather, dl1_0.revision
order by dl1_0.log_date desc fetch first ? rows only   -- page 0: no offset
-- 1x  total for the pager
select count(dl1_0.id) from daily_log dl1_0 where dl1_0.site_id = ?
```

**2 statements**, independent of the page size and of the number of children.
`DailyLogService.listForSite` uses this query; the test asserts `before >= 62` and `after == 2`.

| | Statements for a page of 20 logs | Grows with |
|---|---|---|
| Before (`findBySite_Id` + lazy children) | **64** | logs on the page + work entries on the page |
| After (`findSummariesBySiteId`, `GROUP BY` projection) | **2** | constant |

### Why this fix and not another

| Option | Why not chosen |
|---|---|
| `EAGER` on the collections | Loads children everywhere, also where nobody needs them; still N+1 unless every query fetch-joins. |
| `join fetch` of both collections on the page query | Pagination with collection fetch joins is done **in memory** by Hibernate (HHH90003004 warning: the whole result is loaded, then cut), and it still transfers every child row only to count them. |
| `@BatchSize` / `default_batch_fetch_size` | Reduces 60 queries to a few `IN (...)` queries, but still loads entities that the list only counts. Kept in mind for pages that really show the children. |
| **Aggregate projection (chosen)** | The database counts and sums; no child entity is loaded; constant number of statements. |

The detail page, which **does** show every work entry and crew row, uses the opposite technique:
a single `select distinct ... join fetch` of the whole aggregate (`DailyLogRepository.findDetailById`,
both collections are `Set`s to avoid duplicated rows and `MultipleBagFetchException`).

### Related finding

While wiring the services, the query used to lock the log for child changes
(`findForChildUpdateById`, `OPTIMISTIC_FORCE_INCREMENT`) also fetch-joined the site. Hibernate
applies the lock mode to every fetched entity, and `Site` has no `@Version`, so every call failed.
The fetch join was removed; the site is loaded lazily inside the transaction when needed.
