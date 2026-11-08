package hr.algebra.sitelog.crew.service;

import hr.algebra.sitelog.crew.domain.Crew;

/** One entry of a crew select box (crew assignment rows, affected crews). */
public record CrewOption(Long id, String name, String trade, boolean active) {

	static CrewOption of(Crew crew) {
		return new CrewOption(crew.getId(), crew.getName(), crew.getTrade(), crew.isActive());
	}

}
