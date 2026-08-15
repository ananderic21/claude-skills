package dev.anand.claudeskills.service;

import dev.anand.claudeskills.entity.PasswordResetToken;
import dev.anand.claudeskills.entity.User;
import dev.anand.claudeskills.exception.InvalidTokenException;
import dev.anand.claudeskills.repository.PasswordResetTokenRepository;
import dev.anand.claudeskills.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Value("${app.password-reset.expiry-hours}")
    private long expiryHours;

    @Override
    @Transactional
    public void requestReset(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Do not reveal that the address is unknown — respond as if sent.
            log.debug("Password reset requested for unknown email {}", email);
            return;
        }

        // A new request supersedes any earlier still-valid link for this user.
        tokenRepository.invalidateActiveTokens(user.getId());

        String rawToken = generateToken();
        PasswordResetToken token = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plus(expiryHours, ChronoUnit.HOURS))
                .used(false)
                .build();
        tokenRepository.save(token);

        String resetLink = frontendBaseUrl + "/reset-password?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink, expiryHours);
        log.debug("Issued password reset token for user {} (expires in {}h)", user.getUsername(), expiryHours);
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidTokenException("This password reset link is invalid or has expired"));

        if (!token.isUsable()) {
            throw new InvalidTokenException("This password reset link is invalid or has expired");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException("This password reset link is invalid or has expired"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);
        log.debug("Password reset completed for user {}", user.getUsername());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is guaranteed present on every JVM.
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}
