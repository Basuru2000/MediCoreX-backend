package com.medicorex.controller;

import com.medicorex.dto.*;
import com.medicorex.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ReportController {

    private final ReportService reportService;

    /**
     * Get comprehensive stock valuation report
     */
    @GetMapping("/stock-valuation")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<StockValuationDTO> getStockValuationReport() {
        StockValuationDTO report = reportService.generateStockValuationReport();
        return ResponseEntity.ok(report);
    }

    /**
     * Get valuation summary cards
     */
    @GetMapping("/valuation-summary")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<ValuationSummaryDTO>> getValuationSummary() {
        List<ValuationSummaryDTO> summary = reportService.getValuationSummary();
        return ResponseEntity.ok(summary);
    }

    /**
     * Get product-wise valuation
     */
    @GetMapping("/product-valuation")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<ProductValuationDTO>> getProductValuation(
            @RequestParam(defaultValue = "totalValue") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        List<ProductValuationDTO> valuations = reportService.getProductValuation(sortBy, sortDirection);
        return ResponseEntity.ok(valuations);
    }

    /**
     * Get category-wise valuation
     */
    @GetMapping("/category-valuation")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<CategoryValuationDTO>> getCategoryValuation() {
        List<CategoryValuationDTO> valuations = reportService.getCategoryValuation();
        return ResponseEntity.ok(valuations);
    }

    /**
     * Export stock valuation report as CSV
     */
    @GetMapping("/stock-valuation/export/csv")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<String> exportStockValuationCSV() {
        List<ProductValuationDTO> products = reportService.getProductValuation("name", "ASC");

        StringBuilder csv = new StringBuilder();
        // CSV Header
        csv.append("Product ID,Product Name,Code,Category,Quantity,Unit Price,Total Value,Stock Status,Expiry Date,% of Total\n");

        // CSV Data
        for (ProductValuationDTO product : products) {
            csv.append(String.format("%d,\"%s\",\"%s\",\"%s\",%d,%.2f,%.2f,%s,%s,%.2f%%\n",
                    product.getId(),
                    product.getName(),
                    product.getCode() != null ? product.getCode() : "",
                    product.getCategoryName(),
                    product.getQuantity(),
                    product.getUnitPrice(),
                    product.getTotalValue(),
                    product.getStockStatus(),
                    product.getExpiryDate() != null ? product.getExpiryDate().toString() : "",
                    product.getPercentageOfTotalValue()
            ));
        }

        String filename = "stock_valuation_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body(csv.toString());
    }

    /**
     * Export category valuation report as CSV
     */
    @GetMapping("/category-valuation/export/csv")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<String> exportCategoryValuationCSV() {
        List<CategoryValuationDTO> categories = reportService.getCategoryValuation();

        StringBuilder csv = new StringBuilder();
        // CSV Header
        csv.append("Category ID,Category Name,Product Count,Total Quantity,Total Value,% of Total,Average Product Value\n");

        // CSV Data
        for (CategoryValuationDTO category : categories) {
            csv.append(String.format("%d,\"%s\",%d,%d,%.2f,%.2f%%,%.2f\n",
                    category.getCategoryId(),
                    category.getCategoryName(),
                    category.getProductCount(),
                    category.getTotalQuantity(),
                    category.getTotalValue(),
                    category.getPercentageOfTotal(),
                    category.getAverageProductValue()
            ));
        }

        String filename = "category_valuation_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body(csv.toString());
    }
}