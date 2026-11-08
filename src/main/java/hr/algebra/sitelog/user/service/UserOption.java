package hr.algebra.sitelog.user.service;

/** One entry of a user select box (e.g. the site manager of a site). */
public record UserOption(Long id, String fullName, String email) {
}
