package dev.anand.claudeskills.service;

public interface PasswordResetService {

    /**
     * Begin a password reset: if an account exists for the email, issue a fresh
     * single-use link and email it. Always returns without revealing whether the
     * address is registered.
     */
    void requestReset(String email);

    /**
     * Complete a password reset using the raw token from the emailed link.
     * Throws if the token is unknown, already used, or expired.
     */
    void resetPassword(String rawToken, String newPassword);
}
