package com.medicorex.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierCreateDTO {
    @NotBlank(message = "Supplier name is required")
    @Size(max = 200, message = "Name cannot exceed 200 characters")
    private String name;

    @Size(max = 50, message = "Tax ID cannot exceed 50 characters")
    private String taxId;

    @Size(max = 100)
    private String registrationNumber;

    @Size(max = 255)
    private String website;

    @Email(message = "Invalid email format")
    @Size(max = 100)
    private String email;

    @Pattern(regexp = "^[+]?[0-9\\-\\s]+$", message = "Invalid phone format")
    @Size(max = 50)
    private String phone;

    @Size(max = 50)
    private String fax;

    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String state;

    @Size(max = 100)
    private String country;

    @Size(max = 20)
    private String postalCode;

    private String paymentTerms;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal creditLimit;

    private String notes;
}