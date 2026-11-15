package hr.algebra.sitelog.incident.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.repository.IncidentRepository;

@ExtendWith(MockitoExtension.class)
class IncidentStatsServiceTests {

	@Mock
	IncidentRepository incidents;

	@InjectMocks
	IncidentStatsService service;

	@Test
	void openIncidentCount_countsReportedAndUnderReviewOnly() {
		when(incidents.countByStatusIn(EnumSet.of(IncidentStatus.REPORTED, IncidentStatus.UNDER_REVIEW))).thenReturn(6L);

		assertThat(service.openIncidentCount()).isEqualTo(6L);
		assertThat(IncidentStatsService.OPEN).containsExactlyInAnyOrder(IncidentStatus.REPORTED,
				IncidentStatus.UNDER_REVIEW);
	}

}
