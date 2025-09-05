package com.medicorex.controller;

import com.medicorex.dto.RegisterRequest;
import com.medicorex.dto.UserDTO;
import com.medicorex.dto.UserUpdateDTO;
import com.medicorex.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
// REMOVED class-level @PreAuthorize to allow method-level control
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    private final UserService userService;

    // READ operations - Allow both HOSPITAL_MANAGER and PHARMACY_STAFF
    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // WRITE operations - Only HOSPITAL_MANAGER
    @PostMapping
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody RegisterRequest registerRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(registerRequest));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO userUpdateDTO) {
        return ResponseEntity.ok(userService.updateUser(id, userUpdateDTO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<UserDTO> toggleUserStatus(@PathVariable Long id) {
        return ResponseEntity.ok(userService.toggleUserStatus(id));
    }
}