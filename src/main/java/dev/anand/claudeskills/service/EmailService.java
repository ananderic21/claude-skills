package dev.anand.claudeskills.service;

public interface EmailService {

    /**
     * Send the password-reset email to the given address. Implementations run
     * asynchronously and swallow/log failures, so callers never block on SMTP
     * and a mail outage never leaks whether an account exists.
     */
    void sendPasswordResetEmail(String to, String resetLink, long expiryHours);
}
