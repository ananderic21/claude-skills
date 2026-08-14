package dev.anand.claudeskills.dto;

/**
 * Result of a profile update. {@code auth} is non-null only when the username
 * changed: the JWT subject is the username, so the client must switch to a
 * freshly issued token.
 */
public record ProfileUpdateResponse(
        ProfileResponse profile,
        AuthResponse auth
) {
    @Override
    public String toString() {
        return "ProfileUpdateResponse[profile=" + profile + ", auth=" + auth + "]";
    }
}
