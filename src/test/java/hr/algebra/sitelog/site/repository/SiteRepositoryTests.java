package hr.algebra.sitelog.site.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.Hibernate;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.support.JpaFixtures;
import hr.algebra.sitelog.support.RepositoryTest;
import jakarta.persistence.EntityManager;

/** {@link SiteRepository}: ZG-01 and ZG-02 belong to manager@, ST-01 to manager.split@. */
@RepositoryTest
class SiteRepositoryTests {

	private final SiteRepository sites;
	private final JpaFixtures fixtures;

	@Autowired
	SiteRepositoryTests(SiteRepository sites, EntityManager em) {
		this.sites = sites;
		this.fixtures = new JpaFixtures(em);
	}

	@Test
	void findAllWithManager_fetchesManagerOnEveryRow() {
		fixtures.flushAndClear();
		Statistics statistics = fixtures.freshStatistics();

		Page<Site> page = sites.findAllWithManager(PageRequest.of(0, 10, Sort.by("code")));

		assertThat(page.getContent()).extracting(Site::getCode).containsExactly("ST-01", "ZG-01", "ZG-02");
		assertThat(page.getContent()).extracting(s -> s.getManager().getFullName())
			.containsExactly("Luka Matić", "Marko Kovačević", "Marko Kovačević");
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void searchWithManager_matchesCodeOrNameCaseInsensitively() {
		assertThat(sites.searchWithManager("zg", PageRequest.of(0, 10, Sort.by("code"))).getContent())
			.extracting(Site::getCode).containsExactly("ZG-01", "ZG-02");
		assertThat(sites.searchWithManager("HOTEL", PageRequest.of(0, 10)).getContent())
			.extracting(Site::getCode).containsExactly("ST-01");
		assertThat(sites.searchWithManager("no such site", PageRequest.of(0, 10)).getTotalElements()).isZero();
	}

	@Test
	void findByManagerId_returnsOnlyThatManagersSitesByName() {
		long managerId = fixtures.user(JpaFixtures.MANAGER).getId();

		assertThat(sites.findByManager_IdOrderByNameAsc(managerId)).extracting(Site::getName)
			.containsExactly("Office Building Radnička", "Residential Complex Lanište");
	}

	@Test
	void findWithManagerById_initializesManager() {
		long id = fixtures.site("ST-01").getId();
		fixtures.flushAndClear();

		Site site = sites.findWithManagerById(id).orElseThrow();

		assertThat(Hibernate.isInitialized(site.getManager())).isTrue();
		assertThat(site.getManager().getEmail()).isEqualTo(JpaFixtures.MANAGER_SPLIT);
		assertThat(sites.findWithManagerById(-1L)).isEmpty();
	}

	@Test
	void existsByCodeIgnoreCase_andExcludingOwnId_supportCreateAndEdit() {
		Site zg01 = fixtures.site("ZG-01");

		assertThat(sites.existsByCodeIgnoreCase("zg-01")).isTrue();
		assertThat(sites.existsByCodeIgnoreCase("ZG-99")).isFalse();
		assertThat(sites.existsByCodeIgnoreCaseAndIdNot("zg-01", zg01.getId())).isFalse();
		assertThat(sites.existsByCodeIgnoreCaseAndIdNot("zg-01", fixtures.site("ZG-02").getId())).isTrue();
	}

	@Test
	void existsByIdAndManagerId_isTrueOnlyForTheSitesOwnManager() {
		long zg01 = fixtures.site("ZG-01").getId();

		assertThat(sites.existsByIdAndManager_Id(zg01, fixtures.user(JpaFixtures.MANAGER).getId())).isTrue();
		assertThat(sites.existsByIdAndManager_Id(zg01, fixtures.user(JpaFixtures.MANAGER_SPLIT).getId())).isFalse();
	}

}
