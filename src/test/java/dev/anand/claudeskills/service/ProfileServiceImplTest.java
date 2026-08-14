package dev.anand.claudeskills.service;

import dev.anand.claudeskills.dto.ChangePasswordRequest;
import dev.anand.claudeskills.dto.ProfilePicture;
import dev.anand.claudeskills.dto.ProfileResponse;
import dev.anand.claudeskills.dto.ProfileUpdateResponse;
import dev.anand.claudeskills.dto.UpdateProfileRequest;
import dev.anand.claudeskills.entity.User;
import dev.anand.claudeskills.exception.DuplicateResourceException;
import dev.anand.claudeskills.exception.InvalidFileException;
import dev.anand.claudeskills.exception.InvalidPasswordException;
import dev.anand.claudeskills.exception.ResourceNotFoundException;
import dev.anand.claudeskills.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @TempDir
    Path uploadsDir;

    private ProfileServiceImpl profileService;

    private User user;

    @BeforeEach
    void setUp() {
        profileService = new ProfileServiceImpl(
                userRepository, passwordEncoder, jwtService, uploadsDir.toString());
        user = User.builder()
                .id(1L)
                .username("anand")
                .email("anand@example.com")
                .password("encoded-password")
                .role("USER")
                .name("Anand K")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void getProfile_whenUserExists_returnsProfile() {
        when(userRepository.findByUsername("anand")).thenReturn(Optional.of(user));

        ProfileResponse profile = profileService.getProfile("anand");

        assertThat(profile.username()).isEqualTo("anand");
        assertThat(profile.name()).isEqualTo("Anand K");
        assertThat(profile.email()).isEqualTo("anand@example.com");
        assertThat(profile.hasProfilePicture()).isFalse();
    }

    @Test
    void getProfile_whenUserMissing_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfile("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void updateProfile_withSameUsername_savesAndReturnsNoNewToken() {
        when(userRepository.findByUsername("anand")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileUpdateResponse response = profileService.updateProfile("anand",
                new UpdateProfileRequest("New Name", "anand", "new@example.com"));

        assertThat(response.auth()).isNull();
        assertThat(response.profile().name()).isEqualTo("New Name");
        assertThat(response.profile().email()).isEqualTo("new@example.com");
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void updateProfile_withNewUsername_issuesFreshToken() {
        when(userRepository.findByUsername("anand")).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("anand2")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken("anand2", "USER")).thenReturn("fresh-token");
        when(jwtService.getExpirationSeconds()).thenReturn(28800L);

        ProfileUpdateResponse response = profileService.updateProfile("anand",
                new UpdateProfileRequest("Anand K", "anand2", "anand@example.com"));

        assertThat(response.auth()).isNotNull();
        assertThat(response.auth().token()).isEqualTo("fresh-token");
        assertThat(response.auth().username()).isEqualTo("anand2");
        assertThat(response.profile().username()).isEqualTo("anand2");
    }

    @Test
    void updateProfile_withTakenUsername_throwsConflictAndNeverSaves() {
        when(userRepository.findByUsername("anand")).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> profileService.updateProfile("anand",
                new UpdateProfileRequest("Anand K", "taken", "anand@example.com")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Username is already taken");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateProfile_withTakenEmail_throwsConflictAndNeverSaves() {
        when(userRepository.findByUsername("anand")).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> profileService.updateProfile("anand",
                new UpdateProfileRequest("Anand K", "anand", "taken@example.com")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Email is already registered");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_withCorrectCurrentPassword_encodesAndSaves() {
        when(userRepository.findByUsername("anand")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("new-password-123")).thenReturn("encoded-new");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        profileService.changePassword("anand",
                new ChangePasswordRequest("old-password", "new-password-123"));

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_withWrongCurrentPassword_throwsAndNeverSaves() {
        when(userRepository.findByUsername("anand")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> profileService.changePassword("anand",
                new ChangePasswordRequest("wrong", "new-password-123")))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("Current password is incorrect");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updatePicture_withPngFile_storesFileAndSavesFilename() throws Exception {
        when(userRepository.findByUsername("anand")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3});

        ProfileResponse response = profileService.updatePicture("anand", file);

        assertThat(response.hasProfilePicture()).isTrue();
        assertThat(user.getProfilePicture()).isEqualTo("user-1.png");
        assertThat(Files.readAllBytes(uploadsDir.resolve("user-1.png")))
                .containsExactly(1, 2, 3);
    }

    @Test
    void updatePicture_withUnsupportedType_throwsAndNeverSaves() {
        when(userRepository.findByUsername("anand")).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", new byte[]{1});

        assertThatThrownBy(() -> profileService.updatePicture("anand", file))
                .isInstanceOf(InvalidFileException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updatePicture_withEmptyFile_throws() {
        when(userRepository.findByUsername("anand")).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> profileService.updatePicture("anand", file))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void getPicture_whenStored_returnsBytesAndContentType() throws Exception {
        user.setProfilePicture("user-1.png");
        Files.write(uploadsDir.resolve("user-1.png"), new byte[]{9, 8, 7});
        when(userRepository.findByUsername("anand")).thenReturn(Optional.of(user));

        ProfilePicture picture = profileService.getPicture("anand");

        assertThat(picture.contentType()).isEqualTo("image/png");
        assertThat(picture.content()).containsExactly(9, 8, 7);
    }

    @Test
    void getPicture_whenNoneUploaded_throwsNotFound() {
        when(userRepository.findByUsername("anand")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> profileService.getPicture("anand"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
