package dev.anand.claudeskills.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.anand.claudeskills.dto.AuthResponse;
import dev.anand.claudeskills.dto.ChangePasswordRequest;
import dev.anand.claudeskills.dto.ProfilePicture;
import dev.anand.claudeskills.dto.ProfileResponse;
import dev.anand.claudeskills.dto.ProfileUpdateResponse;
import dev.anand.claudeskills.dto.UpdateProfileRequest;
import dev.anand.claudeskills.exception.DuplicateResourceException;
import dev.anand.claudeskills.exception.GlobalExceptionHandler;
import dev.anand.claudeskills.exception.InvalidPasswordException;
import dev.anand.claudeskills.exception.ResourceNotFoundException;
import dev.anand.claudeskills.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private ProfileService profileService;

    @InjectMocks
    private ProfileController profileController;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Principal principal = new UsernamePasswordAuthenticationToken("anand", null);

    private final ProfileResponse profile = new ProfileResponse(
            "anand", "Anand K", "anand@example.com", false, Instant.parse("2026-08-14T00:00:00Z"));

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(profileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getProfile_returns200WithProfile() throws Exception {
        when(profileService.getProfile("anand")).thenReturn(profile);

        mockMvc.perform(get("/api/profile").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("anand"))
                .andExpect(jsonPath("$.name").value("Anand K"))
                .andExpect(jsonPath("$.email").value("anand@example.com"))
                .andExpect(jsonPath("$.hasProfilePicture").value(false));
    }

    @Test
    void updateProfile_withValidBody_returns200() throws Exception {
        when(profileService.updateProfile(eq("anand"), any(UpdateProfileRequest.class)))
                .thenReturn(new ProfileUpdateResponse(profile, null));

        UpdateProfileRequest payload =
                new UpdateProfileRequest("Anand K", "anand", "anand@example.com");

        mockMvc.perform(put("/api/profile")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.username").value("anand"))
                .andExpect(jsonPath("$.auth").isEmpty());
    }

    @Test
    void updateProfile_withUsernameChange_returnsFreshToken() throws Exception {
        AuthResponse auth = AuthResponse.bearer("fresh-token", 28800L, "anand2");
        ProfileResponse renamed = new ProfileResponse(
                "anand2", "Anand K", "anand@example.com", false, profile.createdAt());
        when(profileService.updateProfile(eq("anand"), any(UpdateProfileRequest.class)))
                .thenReturn(new ProfileUpdateResponse(renamed, auth));

        UpdateProfileRequest payload =
                new UpdateProfileRequest("Anand K", "anand2", "anand@example.com");

        mockMvc.perform(put("/api/profile")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.username").value("anand2"))
                .andExpect(jsonPath("$.auth.token").value("fresh-token"));
    }

    @Test
    void updateProfile_withInvalidEmail_returns400AndNeverCallsService() throws Exception {
        UpdateProfileRequest payload =
                new UpdateProfileRequest("Anand K", "anand", "not-an-email");

        mockMvc.perform(put("/api/profile")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").value("Email must be valid"));

        verify(profileService, never()).updateProfile(any(), any());
    }

    @Test
    void updateProfile_withTakenUsername_returns409() throws Exception {
        when(profileService.updateProfile(eq("anand"), any(UpdateProfileRequest.class)))
                .thenThrow(new DuplicateResourceException("Username is already taken"));

        UpdateProfileRequest payload =
                new UpdateProfileRequest("Anand K", "taken", "anand@example.com");

        mockMvc.perform(put("/api/profile")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Username is already taken"));
    }

    @Test
    void changePassword_withValidBody_returns204() throws Exception {
        ChangePasswordRequest payload =
                new ChangePasswordRequest("old-password", "new-password-123");

        mockMvc.perform(put("/api/profile/password")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNoContent());

        verify(profileService).changePassword(eq("anand"), any(ChangePasswordRequest.class));
    }

    @Test
    void changePassword_withWrongCurrentPassword_returns400() throws Exception {
        org.mockito.Mockito.doThrow(new InvalidPasswordException("Current password is incorrect"))
                .when(profileService).changePassword(eq("anand"), any(ChangePasswordRequest.class));

        ChangePasswordRequest payload =
                new ChangePasswordRequest("wrong", "new-password-123");

        mockMvc.perform(put("/api/profile/password")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Current password is incorrect"));
    }

    @Test
    void changePassword_withShortNewPassword_returns400AndNeverCallsService() throws Exception {
        ChangePasswordRequest payload = new ChangePasswordRequest("old-password", "short");

        mockMvc.perform(put("/api/profile/password")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.newPassword")
                        .value("New password must be between 8 and 72 characters"));

        verify(profileService, never()).changePassword(any(), any());
    }

    @Test
    void uploadPicture_withPngFile_returns200() throws Exception {
        ProfileResponse withPicture = new ProfileResponse(
                "anand", "Anand K", "anand@example.com", true, profile.createdAt());
        when(profileService.updatePicture(eq("anand"), any())).thenReturn(withPicture);

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/profile/picture").file(file).principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasProfilePicture").value(true));
    }

    @Test
    void getPicture_whenStored_returns200WithImageBytes() throws Exception {
        when(profileService.getPicture("anand"))
                .thenReturn(new ProfilePicture(new byte[]{9, 8, 7}, "image/png"));

        mockMvc.perform(get("/api/profile/picture").principal(principal))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[]{9, 8, 7}));
    }

    @Test
    void getPicture_whenNoneUploaded_returns404() throws Exception {
        when(profileService.getPicture("anand"))
                .thenThrow(new ResourceNotFoundException("No profile picture uploaded"));

        mockMvc.perform(get("/api/profile/picture").principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No profile picture uploaded"));
    }
}
