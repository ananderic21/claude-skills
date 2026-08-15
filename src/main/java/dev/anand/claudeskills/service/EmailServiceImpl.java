package dev.anand.claudeskills.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private static final String SUBJECT = "Reset your Task Dashboard password";

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Override
    @Async
    public void sendPasswordResetEmail(String to, String resetLink, long expiryHours) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(SUBJECT);
            helper.setText(plainBody(resetLink, expiryHours), htmlBody(resetLink, expiryHours));

            mailSender.send(message);
            log.info("Password reset email sent to {}", to);
        } catch (MessagingException | MailException ex) {
            // Never rethrow: the caller returns a generic response regardless, and
            // this ERROR line (with stack trace) lands in logs/error.log for triage.
            log.error("Failed to send password reset email to {}: {}", to, ex.getMessage(), ex);
        }
    }

    private String plainBody(String resetLink, long expiryHours) {
        return """
                Hi,

                We received a request to reset the password for your Task Dashboard account.
                Click the link below to choose a new password:

                %s

                This link will expire in %d hours. If you didn't request a password reset,
                you can safely ignore this email — your password won't change.

                Thanks,
                The Task Dashboard Team
                """.formatted(resetLink, expiryHours);
    }

    private String htmlBody(String resetLink, long expiryHours) {
        return """
                <div style="font-family: Arial, sans-serif; color: #0f172a; line-height: 1.6;">
                  <h2 style="margin-bottom: 8px;">Reset your password</h2>
                  <p>Hi,</p>
                  <p>We received a request to reset the password for your <strong>Task Dashboard</strong> account.
                     Click the button below to choose a new password:</p>
                  <p style="margin: 24px 0;">
                    <a href="%s"
                       style="background:#0f172a;color:#ffffff;text-decoration:none;padding:12px 20px;border-radius:8px;font-weight:600;display:inline-block;">
                      Reset Password
                    </a>
                  </p>
                  <p>Or paste this link into your browser:<br>
                     <a href="%s">%s</a></p>
                  <p style="color:#475569;">This link will expire in <strong>%d hours</strong>. If you didn't request a
                     password reset, you can safely ignore this email — your password won't change.</p>
                  <p style="margin-top:24px;color:#475569;">Thanks,<br>The Task Dashboard Team</p>
                </div>
                """.formatted(resetLink, resetLink, resetLink, expiryHours);
    }
}
