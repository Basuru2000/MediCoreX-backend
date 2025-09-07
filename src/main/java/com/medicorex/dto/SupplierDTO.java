package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDTO {
    private Long id;
    private String code;
    private String name;
    private String taxId;
    private String registrationNumber;
    private String website;
    private String email;
    private String phone;
    private String fax;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String status;
    private BigDecimal rating;
    private String paymentTerms;
    private BigDecimal creditLimit;
    private String notes;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SupplierContactDTO> contacts;
    private List<SupplierDocumentDTO> documents;
}