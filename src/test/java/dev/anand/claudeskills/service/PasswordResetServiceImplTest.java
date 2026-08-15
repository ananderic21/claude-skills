package dev.anand.claudeskills.service;

import dev.anand.claudeskills.entity.PasswordResetToken;
import dev.anand.claudeskills.entity.User;
import dev.anand.claudeskills.exception.InvalidTokenException;
import dev.anand.claudeskills.repository.PasswordResetTokenRepository;
import dev.anand.claudeskills.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    @InjectMocks private PasswordResetServiceImpl service;

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(service, "expiryHours", 24L);
    }

    @Test
    void requestReset_knownEmail_invalidatesOldTokens_persistsNew_andEmailsLink() {
        User user = User.builder().id(7L).username("alice").email("alice@example.com").build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        service.requestReset("alice@example.com");

        verify(tokenRepository).invalidateActiveTokens(7L);

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        PasswordResetToken token = saved.getValue();
        assertThat(token.getUserId()).isEqualTo(7L);
        assertThat(token.isUsed()).isFalse();
        assertThat(token.getTokenHash()).hasSize(64); // SHA-256 hex — raw token is never stored
        // Expiry is ~24h out (not the raw creation instant).
        assertThat(token.getExpiresAt()).isAfter(Instant.now().plus(23, ChronoUnit.HOURS));

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq("alice@example.com"), link.capture(), eq(24L));
        assertThat(link.getValue()).startsWith("http://localhost:5173/reset-password?token=");
    }

    @Test
    void requestReset_unknownEmail_doesNothingAndDoesNotLeak() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.requestReset("ghost@example.com");

        verify(tokenRepository, never()).save(any());
        verify(tokenRepository, never()).invalidateActiveTokens(anyLong());
        verifyNoInteractions(emailService);
    }

    @Test
    void resetPassword_validToken_encodesNewPassword_andMarksTokenUsed() {
        PasswordResetToken token = usableToken(7L);
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        User user = User.builder().id(7L).username("alice").password("old-hash").build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-secret-pw")).thenReturn("new-hash");

        service.resetPassword("raw-token-value", "new-secret-pw");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(userRepository).save(user);
        assertThat(token.isUsed()).isTrue();
        verify(tokenRepository).save(token);
    }

    @Test
    void resetPassword_unknownToken_throws() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("bogus", "new-secret-pw"))
                .isInstanceOf(InvalidTokenException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_expiredToken_throws() {
        PasswordResetToken token = usableToken(7L);
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword("raw", "new-secret-pw"))
                .isInstanceOf(InvalidTokenException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_alreadyUsedToken_throws() {
        PasswordResetToken token = usableToken(7L);
        token.setUsed(true);
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword("raw", "new-secret-pw"))
                .isInstanceOf(InvalidTokenException.class);
        verify(userRepository, never()).save(any());
    }

    private PasswordResetToken usableToken(long userId) {
        return PasswordResetToken.builder()
                .userId(userId)
                .tokenHash("hash")
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .used(false)
                .build();
    }
}
