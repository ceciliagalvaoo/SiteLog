package hr.algebra.sitelog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the full application against a real PostgreSQL (Testcontainers) and checks
 * the skeleton wiring: Flyway migrations, public health check, login required elsewhere.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SiteLogApplicationTests {

	private final Flyway flyway;
	private final MockMvc mockMvc;

	@Autowired
	SiteLogApplicationTests(Flyway flyway, MockMvc mockMvc) {
		this.flyway = flyway;
		this.mockMvc = mockMvc;
	}

	@Test
	void contextLoadsAndAppliesFlywayBaseline() {
		assertThat(flyway.info().current()).isNotNull();
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
		assertThat(flyway.info().pending()).isEmpty();
	}

	@Test
	void healthEndpointIsPublicAndUp() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void otherPagesRedirectToLogin() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

}
