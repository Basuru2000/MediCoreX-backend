package com.medicorex.dto;

import com.medicorex.entity.User.UserRole;
import com.medicorex.entity.User.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserUpdateDTO {
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotNull(message = "Role is required")
    private UserRole role;

    private Gender gender;

    private String profileImageUrl;

    private Boolean active;
}