package dev.anand.claudeskills.service;

import dev.anand.claudeskills.dto.AuthResponse;
import dev.anand.claudeskills.dto.LoginRequest;
import dev.anand.claudeskills.dto.RegisterRequest;
import dev.anand.claudeskills.entity.User;
import dev.anand.claudeskills.exception.DuplicateResourceException;
import dev.anand.claudeskills.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("anand")
                .email("anand@example.com")
                .password("$2a$10$hashedpassword")
                .role("USER")
                .build();
    }

    @Test
    void register_withNewUser_encodesPasswordAndReturnsToken() {
        RegisterRequest request = new RegisterRequest("anand", "anand@example.com", "secret-pass-123");
        when(userRepository.existsByUsername("anand")).thenReturn(false);
        when(userRepository.existsByEmail("anand@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret-pass-123")).thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateToken("anand", "USER")).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(28800L);

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(28800L);
        assertThat(response.username()).isEqualTo("anand");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$10$hashedpassword");
        assertThat(captor.getValue().getRole()).isEqualTo("USER");
    }

    @Test
    void register_withTakenUsername_throwsConflictAndNeverSaves() {
        RegisterRequest request = new RegisterRequest("anand", "anand@example.com", "secret-pass-123");
        when(userRepository.existsByUsername("anand")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Username is already taken");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_withTakenEmail_throwsConflictAndNeverSaves() {
        RegisterRequest request = new RegisterRequest("anand", "anand@example.com", "secret-pass-123");
        when(userRepository.existsByUsername("anand")).thenReturn(false);
        when(userRepository.existsByEmail("anand@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Email is already registered");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_withValidCredentials_authenticatesAndReturnsToken() {
        LoginRequest request = new LoginRequest("anand", "secret-pass-123");
        when(userRepository.findByUsername("anand")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("anand", "USER")).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(28800L);

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("anand");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_withBadCredentials_propagatesExceptionAndNeverIssuesToken() {
        LoginRequest request = new LoginRequest("anand", "wrong-password");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void refreshToken_withExistingUser_returnsFreshToken() {
        when(userRepository.findByUsername("anand")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("anand", "USER")).thenReturn("fresh-jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(28800L);

        AuthResponse response = authService.refreshToken("anand");

        assertThat(response.token()).isEqualTo("fresh-jwt-token");
        assertThat(response.username()).isEqualTo("anand");
    }

    @Test
    void refreshToken_withMissingUser_throwsAndNeverIssuesToken() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);

        verify(jwtService, never()).generateToken(any(), any());
    }
}
