package dev.anand.claudeskills.controller;

import dev.anand.claudeskills.dto.ChangePasswordRequest;
import dev.anand.claudeskills.dto.ProfilePicture;
import dev.anand.claudeskills.dto.ProfileResponse;
import dev.anand.claudeskills.dto.ProfileUpdateResponse;
import dev.anand.claudeskills.dto.UpdateProfileRequest;
import dev.anand.claudeskills.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(Principal principal) {
        return ResponseEntity.ok(profileService.getProfile(principal.getName()));
    }

    @PutMapping
    public ResponseEntity<ProfileUpdateResponse> updateProfile(Principal principal,
                                                               @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(profileService.updateProfile(principal.getName(), request));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(Principal principal,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(principal.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileResponse> uploadPicture(Principal principal,
                                                         @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(profileService.updatePicture(principal.getName(), file));
    }

    @GetMapping("/picture")
    public ResponseEntity<byte[]> getPicture(Principal principal) {
        ProfilePicture picture = profileService.getPicture(principal.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(picture.contentType()))
                .cacheControl(CacheControl.noCache())
                .body(picture.content());
    }
}
