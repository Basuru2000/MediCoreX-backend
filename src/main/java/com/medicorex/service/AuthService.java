package com.medicorex.service;

import com.medicorex.dto.LoginRequest;
import com.medicorex.dto.LoginResponse;
import com.medicorex.dto.RegisterRequest;
import com.medicorex.dto.UserDTO;
import com.medicorex.entity.User;
import com.medicorex.repository.UserRepository;
import com.medicorex.security.CustomUserDetails;
import com.medicorex.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public LoginResponse login(LoginRequest loginRequest) {
        try {
            System.out.println("=== Login attempt for user: " + loginRequest.getUsername());

            // Check if user exists
            User userCheck = userRepository.findByUsername(loginRequest.getUsername())
                    .orElse(null);

            if (userCheck != null) {
                System.out.println("=== User found in database");
                System.out.println("=== User ID: " + userCheck.getId());
                System.out.println("=== User role: " + userCheck.getRole());
                System.out.println("=== User active: " + userCheck.getActive());
                System.out.println("=== Password starts with $2a: " + userCheck.getPassword().startsWith("$2a"));
            } else {
                System.out.println("=== User NOT found in database");
            }

            // Log the password details for debugging
            System.out.println("=== Password length: " + loginRequest.getPassword().length());
            System.out.println("=== Password value: " + loginRequest.getPassword());

            // Create authentication token
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
            );
            System.out.println("=== Authentication token created: " + authToken);

            // Attempt authentication
            Authentication authentication = authenticationManager.authenticate(authToken);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            String jwt = jwtUtils.generateToken(userDetails);
            User user = userDetails.getUser();

            System.out.println("=== User authenticated successfully");
            System.out.println("=== User role: " + user.getRole());
            System.out.println("=== User active: " + user.getActive());
            System.out.println("=== JWT token generated: " + (jwt != null ? "YES" : "NO"));
            System.out.println("=== Token length: " + (jwt != null ? jwt.length() : 0));

            LoginResponse response = LoginResponse.builder()
                    .token(jwt)
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .role(user.getRole().name())
                    .build();

            System.out.println("=== Login response created successfully");
            return response;

        } catch (Exception e) {
            System.out.println("=== Login failed with exception: " + e.getClass().getName());
            System.out.println("=== Error message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public UserDTO register(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new RuntimeException("Username is already taken!");
        }

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("Email is already in use!");
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setEmail(registerRequest.getEmail());
        user.setFullName(registerRequest.getFullName());
        user.setRole(registerRequest.getRole());
        user.setActive(true);

        User savedUser = userRepository.save(user);
        return convertToDTO(savedUser);
    }

    private UserDTO convertToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
