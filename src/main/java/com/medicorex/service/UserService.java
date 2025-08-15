package com.medicorex.service;

import com.medicorex.dto.RegisterRequest;
import com.medicorex.dto.UserDTO;
import com.medicorex.dto.UserUpdateDTO;
import com.medicorex.entity.User;
import com.medicorex.exception.BusinessException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;
    private final NotificationService notificationService;

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return convertToDTO(user);
    }

    /**
     * Find user by username
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    /**
     * Find user by username and return as DTO
     */
    public UserDTO findUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        return convertToDTO(user);
    }

    @Transactional
    public UserDTO createUser(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new BusinessException("Username is already taken!");
        }

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new BusinessException("Email is already in use!");
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setEmail(registerRequest.getEmail());
        user.setFullName(registerRequest.getFullName());
        user.setRole(registerRequest.getRole());
        user.setGender(registerRequest.getGender() != null ? registerRequest.getGender() : User.Gender.NOT_SPECIFIED);
        user.setProfileImageUrl(registerRequest.getProfileImageUrl());
        user.setActive(true);

        User savedUser = userRepository.save(user);

        // ===== NOTIFICATION TRIGGER =====
        try {
            // Notify all managers about new user registration
            Map<String, String> params = new HashMap<>();
            params.put("username", savedUser.getUsername());
            params.put("role", savedUser.getRole().toString());

            List<String> managerRoles = Arrays.asList("HOSPITAL_MANAGER");
            notificationService.notifyUsersByRole(managerRoles, "USER_REGISTERED", params,
                    Map.of("userId", savedUser.getId()));

            log.info("User registration notification sent for: {}", savedUser.getUsername());
        } catch (Exception e) {
            log.error("Failed to send user registration notification: {}", e.getMessage());
            // Don't fail the registration for notification errors
        }

        return convertToDTO(savedUser);
    }

    public UserDTO updateUser(Long id, UserUpdateDTO userUpdateDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Check if email is being changed and already exists
        if (!user.getEmail().equals(userUpdateDTO.getEmail()) &&
                userRepository.existsByEmail(userUpdateDTO.getEmail())) {
            throw new BusinessException("Email is already in use!");
        }

        // ✅ ADD notification if role changed
        if (!user.getRole().equals(userUpdateDTO.getRole())) {
            // Notify user about role change
            Map<String, String> params = new HashMap<>();
            params.put("role", userUpdateDTO.getRole().toString());

            notificationService.createNotificationFromTemplate(
                    id, "USER_ROLE_CHANGED", params, null
            );
        }

        // If profile image is being changed, delete the old one
        if (userUpdateDTO.getProfileImageUrl() != null &&
                !userUpdateDTO.getProfileImageUrl().equals(user.getProfileImageUrl()) &&
                user.getProfileImageUrl() != null) {
            fileService.deleteFile(user.getProfileImageUrl());
        }

        user.setEmail(userUpdateDTO.getEmail());
        user.setFullName(userUpdateDTO.getFullName());
        user.setRole(userUpdateDTO.getRole());
        user.setGender(userUpdateDTO.getGender() != null ? userUpdateDTO.getGender() : User.Gender.NOT_SPECIFIED);
        user.setProfileImageUrl(userUpdateDTO.getProfileImageUrl());
        if (userUpdateDTO.getActive() != null) {
            user.setActive(userUpdateDTO.getActive());
        }

        User updatedUser = userRepository.save(user);
        return convertToDTO(updatedUser);
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Prevent deleting the last active admin
        if (user.getRole() == User.UserRole.HOSPITAL_MANAGER) {
            long adminCount = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == User.UserRole.HOSPITAL_MANAGER && u.getActive())
                    .count();
            if (adminCount <= 1) {
                throw new BusinessException("Cannot delete the last active Hospital Manager");
            }
        }

        // Delete profile image if exists
        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
            fileService.deleteFile(user.getProfileImageUrl());
        }

        userRepository.deleteById(id);
    }

    @Transactional
    public UserDTO toggleUserStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Prevent deactivating the last active admin
        if (user.getRole() == User.UserRole.HOSPITAL_MANAGER && user.getActive()) {
            long activeAdminCount = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == User.UserRole.HOSPITAL_MANAGER && u.getActive())
                    .count();
            if (activeAdminCount <= 1) {
                throw new BusinessException("Cannot deactivate the last active Hospital Manager");
            }
        }

        user.setActive(!user.getActive());
        User updatedUser = userRepository.save(user);

        // ===== NOTIFICATION TRIGGER =====
        try {
            Map<String, String> params = new HashMap<>();
            params.put("username", updatedUser.getUsername());

            String templateCode = updatedUser.getActive() ? "USER_ACTIVATED" : "USER_DEACTIVATED";

            // Notify managers
            List<String> managerRoles = Arrays.asList("HOSPITAL_MANAGER");
            notificationService.notifyUsersByRole(managerRoles, templateCode, params,
                    Map.of("userId", updatedUser.getId()));

            // Also notify the affected user if they're being deactivated
            if (!updatedUser.getActive()) {
                notificationService.createNotificationFromTemplate(
                        updatedUser.getId(),
                        "USER_DEACTIVATED",
                        params,
                        null
                );
            }
        } catch (Exception e) {
            log.error("Failed to send user status change notification: {}", e.getMessage());
        }

        return convertToDTO(updatedUser);
    }

    private UserDTO convertToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .gender(user.getGender())
                .profileImageUrl(user.getProfileImageUrl())
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}