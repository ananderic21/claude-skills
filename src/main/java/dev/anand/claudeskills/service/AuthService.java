package dev.anand.claudeskills.service;

import dev.anand.claudeskills.dto.AuthResponse;
import dev.anand.claudeskills.dto.LoginRequest;
import dev.anand.claudeskills.dto.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(String username);

    void logout(String username);
}
