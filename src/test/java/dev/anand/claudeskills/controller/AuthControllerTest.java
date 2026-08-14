package dev.anand.claudeskills.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.anand.claudeskills.dto.AuthResponse;
import dev.anand.claudeskills.dto.LoginRequest;
import dev.anand.claudeskills.dto.RegisterRequest;
import dev.anand.claudeskills.exception.DuplicateResourceException;
import dev.anand.claudeskills.exception.GlobalExceptionHandler;
import dev.anand.claudeskills.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AuthResponse authResponse = AuthResponse.bearer("jwt-token", 28800L, "anand");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void register_withValidBody_returns201WithToken() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        RegisterRequest payload = new RegisterRequest("anand", "anand@example.com", "secret-pass-123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(28800))
                .andExpect(jsonPath("$.username").value("anand"));
    }

    @Test
    void register_withShortPassword_returns400AndNeverCallsService() throws Exception {
        RegisterRequest payload = new RegisterRequest("anand", "anand@example.com", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").value("Password must be between 8 and 72 characters"));

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    void register_withInvalidEmail_returns400() throws Exception {
        RegisterRequest payload = new RegisterRequest("anand", "not-an-email", "secret-pass-123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").value("Email must be valid"));
    }

    @Test
    void register_withTakenUsername_returns409WithErrorBody() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateResourceException("Username is already taken"));

        RegisterRequest payload = new RegisterRequest("anand", "anand@example.com", "secret-pass-123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Username is already taken"));
    }

    @Test
    void login_withValidCredentials_returns200WithToken() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        LoginRequest payload = new LoginRequest("anand", "secret-pass-123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.username").value("anand"));
    }

    @Test
    void login_withBadCredentials_returns401WithErrorBody() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest payload = new LoginRequest("anand", "wrong-password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }

    @Test
    void login_withBlankUsername_returns400AndNeverCallsService() throws Exception {
        LoginRequest payload = new LoginRequest("", "secret-pass-123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.username").value("Username is required"));

        verify(authService, never()).login(any(LoginRequest.class));
    }

    @Test
    void logout_withAuthenticatedUser_returns204AndCallsService() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .principal(new UsernamePasswordAuthenticationToken("anand", null)))
                .andExpect(status().isNoContent());

        verify(authService).logout("anand");
    }
}
