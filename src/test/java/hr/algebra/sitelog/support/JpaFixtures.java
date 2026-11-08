package hr.algebra.sitelog.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.dailylog.domain.CrewAssignment;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.user.domain.AppUser;
import jakarta.persistence.EntityManager;

/**
 * Small JPA fixtures for repository tests. Lookups use the natural keys of the V4 demo data
 * (user e-mail, site code, crew name, site + log date); new rows get fresh natural keys so they
 * never collide with the demo data. Everything runs in the test transaction and is rolled back.
 */
public final class JpaFixtures {

	public static final String MANAGER = "manager@sitelog.test";

	public static final String MANAGER_SPLIT = "manager.split@sitelog.test";

	public static final String SUPERVISOR = "supervisor@sitelog.test";

	public static final String INSPECTOR = "inspector@sitelog.test";

	private final EntityManager em;

	public JpaFixtures(EntityManager em) {
		this.em = em;
	}

	public AppUser user(String email) {
		return em.createQuery("select u from AppUser u where u.email = :email", AppUser.class)
			.setParameter("email", email)
			.getSingleResult();
	}

	public Site site(String code) {
		return em.createQuery("select s from Site s where s.code = :code", Site.class)
			.setParameter("code", code)
			.getSingleResult();
	}

	public Crew crew(String name) {
		return em.createQuery("select c from Crew c where c.name = :name", Crew.class)
			.setParameter("name", name)
			.getSingleResult();
	}

	public DailyLog dailyLog(String siteCode, String date) {
		return em.createQuery("select d from DailyLog d where d.site.code = :code and d.logDate = :date",
				DailyLog.class)
			.setParameter("code", siteCode)
			.setParameter("date", LocalDate.parse(date))
			.getSingleResult();
	}

	/** A new site of {@value #MANAGER} without logs or incidents. */
	public Site newSite() {
		Site site = new Site("T-" + UUID.randomUUID().toString().substring(0, 8), "Test site", "Test street 1",
				LocalDate.parse("2026-01-01"), user(MANAGER));
		em.persist(site);
		return site;
	}

	public Crew newCrew(boolean active) {
		Crew crew = new Crew("Crew " + UUID.randomUUID(), "Test d.o.o.", "Formwork");
		crew.setActive(active);
		em.persist(crew);
		return crew;
	}

	public CrewAssignment assignment(Crew crew, String hours) {
		return new CrewAssignment(crew, new BigDecimal(hours), 2, "Task");
	}

	/** Writes pending changes and empties the persistence context, so the next read hits the database. */
	public void flushAndClear() {
		em.flush();
		em.clear();
	}

	/** Hibernate statistics, cleared: {@code getPrepareStatementCount()} then counts only what follows. */
	public Statistics freshStatistics() {
		Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		return statistics;
	}

}
