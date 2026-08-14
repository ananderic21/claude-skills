package dev.anand.claudeskills.controller;

import dev.anand.claudeskills.dto.AuthResponse;
import dev.anand.claudeskills.exception.GlobalExceptionHandler;
import dev.anand.claudeskills.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TokenControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private TokenController tokenController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tokenController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void refresh_withAuthenticatedUser_returns200WithFreshToken() throws Exception {
        when(authService.refreshToken("anand"))
                .thenReturn(AuthResponse.bearer("fresh-jwt-token", 28800L, "anand"));

        mockMvc.perform(post("/api/auth/token/refresh")
                        .principal(new UsernamePasswordAuthenticationToken("anand", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fresh-jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("anand"));
    }

    @Test
    void refresh_whenUserNoLongerExists_returns401() throws Exception {
        when(authService.refreshToken("ghost"))
                .thenThrow(new UsernameNotFoundException("User not found: ghost"));

        mockMvc.perform(post("/api/auth/token/refresh")
                        .principal(new UsernamePasswordAuthenticationToken("ghost", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("User not found: ghost"));
    }
}
