package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDocumentDTO {
    private Long id;
    private Long supplierId;
    private String documentType;
    private String documentName;
    private String filePath;
    private Long fileSize;
    private LocalDate expiryDate;
    private String uploadedBy;
}