package com.medicorex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImportResultDTO {
    private int totalRows;
    private int successfulImports;
    private int failedImports;
    private List<ImportErrorDTO> errors;
}