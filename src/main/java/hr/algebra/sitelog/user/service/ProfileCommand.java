package hr.algebra.sitelog.user.service;

/** What users change on their own profile: name and UI language (e-mail and role are the Admin's). */
public record ProfileCommand(String fullName, String preferredLanguage) {
}
