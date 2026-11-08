package hr.algebra.sitelog.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import hr.algebra.sitelog.TestcontainersConfiguration;

/**
 * Repository slice test against the real schema: {@code @DataJpaTest} (JPA + Flyway, each test in
 * a rolled-back transaction) on a PostgreSQL 17 Testcontainer instead of an embedded database.
 * Flyway runs V1-V6, so the V4 demo data is available to every test.
 *
 * <p>Hibernate statistics are on so a test can count the SQL statements a query issues (proof
 * that a fetch join loads an aggregate in one statement). Every repository test class uses this
 * one annotation, so they all share one cached Spring context and one container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest(properties = {
		"spring.jpa.properties.hibernate.generate_statistics=true",
		"logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=warn" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
public @interface RepositoryTest {

}
