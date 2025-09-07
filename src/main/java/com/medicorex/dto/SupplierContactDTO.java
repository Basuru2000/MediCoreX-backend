package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierContactDTO {
    private Long id;
    private Long supplierId;
    private String name;
    private String designation;
    private String email;
    private String phone;
    private String mobile;
    private Boolean isPrimary;
    private String notes;
}