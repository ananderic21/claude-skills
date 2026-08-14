package dev.anand.claudeskills.service;

import dev.anand.claudeskills.dto.AuthResponse;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@Service
public class ProfileServiceImpl implements ProfileService {

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            MediaType.IMAGE_JPEG_VALUE, ".jpg",
            MediaType.IMAGE_PNG_VALUE, ".png",
            "image/webp", ".webp");

    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            ".jpg", MediaType.IMAGE_JPEG_VALUE,
            ".png", MediaType.IMAGE_PNG_VALUE,
            ".webp", "image/webp");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Path uploadsDir;

    public ProfileServiceImpl(UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              JwtService jwtService,
                              @Value("${app.uploads.dir:uploads}") String uploadsDir) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.uploadsDir = Path.of(uploadsDir);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String username) {
        return ProfileResponse.from(requireUser(username));
    }

    @Override
    @Transactional
    public ProfileUpdateResponse updateProfile(String username, UpdateProfileRequest request) {
        User user = requireUser(username);

        boolean usernameChanged = !request.username().equals(user.getUsername());
        if (usernameChanged && userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username is already taken");
        }
        if (!request.email().equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email is already registered");
        }

        user.setName(normalizeName(request.name()));
        user.setUsername(request.username());
        user.setEmail(request.email());
        User saved = userRepository.save(user);

        // The JWT subject is the username, so a rename invalidates the current
        // token's identity — hand the client a fresh one.
        AuthResponse auth = null;
        if (usernameChanged) {
            String token = jwtService.generateToken(saved.getUsername(), saved.getRole());
            auth = AuthResponse.bearer(token, jwtService.getExpirationSeconds(), saved.getUsername());
        }
        return new ProfileUpdateResponse(ProfileResponse.from(saved), auth);
    }

    @Override
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = requireUser(username);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public ProfileResponse updatePicture(String username, MultipartFile file) {
        User user = requireUser(username);
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Please select an image file");
        }
        String extension = EXTENSION_BY_CONTENT_TYPE.get(file.getContentType());
        if (extension == null) {
            throw new InvalidFileException("Only JPEG, PNG or WebP images are allowed");
        }

        // Filename is derived from the user id, never from client input
        String filename = "user-" + user.getId() + extension;
        try {
            Files.createDirectories(uploadsDir);
            deleteExistingPicture(user);
            Files.copy(file.getInputStream(), uploadsDir.resolve(filename),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store profile picture", e);
        }
        user.setProfilePicture(filename);
        return ProfileResponse.from(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public ProfilePicture getPicture(String username) {
        User user = requireUser(username);
        if (user.getProfilePicture() == null) {
            throw new ResourceNotFoundException("No profile picture uploaded");
        }
        Path path = uploadsDir.resolve(user.getProfilePicture());
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("No profile picture uploaded");
        }
        try {
            String extension = user.getProfilePicture()
                    .substring(user.getProfilePicture().lastIndexOf('.'));
            String contentType = CONTENT_TYPE_BY_EXTENSION
                    .getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            return new ProfilePicture(Files.readAllBytes(path), contentType);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read profile picture", e);
        }
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private void deleteExistingPicture(User user) throws IOException {
        if (user.getProfilePicture() != null) {
            Files.deleteIfExists(uploadsDir.resolve(user.getProfilePicture()));
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim();
    }
}
