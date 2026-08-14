package dev.anand.claudeskills.service;

import dev.anand.claudeskills.dto.ChangePasswordRequest;
import dev.anand.claudeskills.dto.ProfilePicture;
import dev.anand.claudeskills.dto.ProfileResponse;
import dev.anand.claudeskills.dto.ProfileUpdateResponse;
import dev.anand.claudeskills.dto.UpdateProfileRequest;
import org.springframework.web.multipart.MultipartFile;

public interface ProfileService {

    ProfileResponse getProfile(String username);

    ProfileUpdateResponse updateProfile(String username, UpdateProfileRequest request);

    void changePassword(String username, ChangePasswordRequest request);

    ProfileResponse updatePicture(String username, MultipartFile file);

    ProfilePicture getPicture(String username);
}
