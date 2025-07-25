package com.medicorex.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BarcodeScanDTO {
    @NotBlank(message = "Barcode image is required")
    private String barcodeImage; // Base64 encoded image
}