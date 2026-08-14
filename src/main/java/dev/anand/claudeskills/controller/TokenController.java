package dev.anand.claudeskills.controller;

import dev.anand.claudeskills.dto.AuthResponse;
import dev.anand.claudeskills.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth/token")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class TokenController {

    private final AuthService authService;

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(Principal principal) {
        return ResponseEntity.ok(authService.refreshToken(principal.getName()));
    }
}
