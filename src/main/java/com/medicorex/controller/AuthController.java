package com.medicorex.controller;

import com.medicorex.dto.LoginRequest;
import com.medicorex.dto.LoginResponse;
import com.medicorex.dto.RegisterRequest;
import com.medicorex.dto.UserDTO;
import com.medicorex.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            System.out.println("=== AuthController - Login request received for: " + loginRequest.getUsername());
            LoginResponse response = authService.login(loginRequest);
            System.out.println("=== AuthController - Login successful, returning response");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("=== AuthController - Login failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody RegisterRequest registerRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(registerRequest));
    }
}