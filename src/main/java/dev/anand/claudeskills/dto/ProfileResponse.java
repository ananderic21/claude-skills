package dev.anand.claudeskills.dto;

import dev.anand.claudeskills.entity.User;

import java.time.Instant;

public record ProfileResponse(
        String username,
        String name,
        String email,
        boolean hasProfilePicture,
        Instant createdAt
) {
    public static ProfileResponse from(User user) {
        return new ProfileResponse(
                user.getUsername(),
                user.getName(),
                user.getEmail(),
                user.getProfilePicture() != null,
                user.getCreatedAt());
    }
}
