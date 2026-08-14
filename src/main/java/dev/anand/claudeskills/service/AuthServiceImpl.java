package dev.anand.claudeskills.service;

import dev.anand.claudeskills.dto.AuthResponse;
import dev.anand.claudeskills.dto.LoginRequest;
import dev.anand.claudeskills.dto.RegisterRequest;
import dev.anand.claudeskills.entity.User;
import dev.anand.claudeskills.exception.DuplicateResourceException;
import dev.anand.claudeskills.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email is already registered");
        }
        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role("USER")
                .build();
        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved.getUsername(), saved.getRole());
        return AuthResponse.bearer(token, jwtService.getExpirationSeconds(), saved.getUsername());
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return AuthResponse.bearer(token, jwtService.getExpirationSeconds(), user.getUsername());
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return AuthResponse.bearer(token, jwtService.getExpirationSeconds(), user.getUsername());
    }

    @Override
    public void logout(String username) {
        // Stateless JWTs cannot be revoked server-side; this hook exists so the
        // logout time is recorded by the audit aspects.
    }
}
