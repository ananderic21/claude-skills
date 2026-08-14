package dev.anand.claudeskills.dto;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String username
) {
    public static AuthResponse bearer(String token, long expiresInSeconds, String username) {
        return new AuthResponse(token, "Bearer", expiresInSeconds, username);
    }
}
