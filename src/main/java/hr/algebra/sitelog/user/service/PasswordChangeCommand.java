package hr.algebra.sitelog.user.service;

/** Own password change: the current password proves it is the user, the confirmation catches typos. */
public record PasswordChangeCommand(String currentPassword, String newPassword, String confirmPassword) {
}
