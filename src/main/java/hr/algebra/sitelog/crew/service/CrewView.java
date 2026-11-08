package hr.algebra.sitelog.crew.service;

import hr.algebra.sitelog.crew.domain.Crew;

/** A crew as shown in the crew list and loaded into the crew form. */
public record CrewView(Long id, String name, String company, String trade, boolean active) {

	static CrewView of(Crew crew) {
		return new CrewView(crew.getId(), crew.getName(), crew.getCompany(), crew.getTrade(), crew.isActive());
	}

}
