package com.medicorex.controller;

import com.medicorex.dto.*;
import com.medicorex.service.ProductService;
import com.medicorex.service.ProductExportService;
import com.medicorex.service.ProductImportService;
import com.medicorex.service.BarcodeService;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.exception.BarcodeDecodeException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ProductController {

    private final ProductService productService;
    private final ProductExportService exportService;
    private final ProductImportService importService;
    private final BarcodeService barcodeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<PageResponseDTO<ProductDTO>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {

        Sort.Direction direction = sortDirection.equalsIgnoreCase("DESC")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        return ResponseEntity.ok(productService.getAllProducts(pageable));
    }

    @GetMapping("/category/{categoryId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<PageResponseDTO<ProductDTO>> getProductsByCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(productService.getProductsByCategory(categoryId, pageable));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<List<ProductDTO>> getLowStockProducts() {
        return ResponseEntity.ok(productService.getLowStockProducts());
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<ProductDTO>> getExpiringProducts(
            @RequestParam(defaultValue = "30") int daysAhead) {
        return ResponseEntity.ok(productService.getExpiringProducts(daysAhead));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ProductDTO> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ProductDTO> getProductByCode(@PathVariable String code) {
        return ResponseEntity.ok(productService.getProductByCode(code));
    }

    @GetMapping("/barcode/{barcode}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ProductDTO> getProductByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(productService.getProductByBarcode(barcode));
    }

    @PostMapping("/barcode/scan")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<?> scanBarcode(@Valid @RequestBody BarcodeScanDTO scanDTO) {
        try {
            // First, try to decode the barcode
            String barcode = barcodeService.decodeBarcode(scanDTO.getBarcodeImage());

            // Try to find a product with this barcode
            try {
                ProductDTO product = productService.getProductByBarcode(barcode);
                // Return the product if found
                return ResponseEntity.ok(product);
            } catch (ResourceNotFoundException e) {
                // No product found, just return the barcode
                Map<String, String> response = new HashMap<>();
                response.put("barcode", barcode);
                response.put("message", "Barcode decoded successfully but no matching product found");
                return ResponseEntity.ok(response);
            }
        } catch (NotFoundException e) {
            // No barcode could be decoded
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "No barcode found in the image");
            errorResponse.put("message", "Please ensure the image contains a clear, readable barcode");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            // General error
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to process barcode");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/barcode/generate/{barcode}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<Map<String, String>> generateBarcodeImage(@PathVariable String barcode) {
        String barcodeImage = productService.generateBarcodeImage(barcode);
        return ResponseEntity.ok(Map.of("barcodeImage", barcodeImage));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<List<ProductDTO>> searchProducts(@RequestParam String query) {
        return ResponseEntity.ok(productService.searchProducts(query));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ProductDTO> createProduct(@Valid @RequestBody ProductCreateDTO productCreateDTO) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(productCreateDTO));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ProductDTO> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductCreateDTO productCreateDTO) {
        return ResponseEntity.ok(productService.updateProduct(id, productCreateDTO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Export products to CSV based on filter type
     */
    @GetMapping("/export/csv")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<byte[]> exportProductsCSV(@RequestParam(defaultValue = "all") String filter) {
        try {
            byte[] csvData = exportService.exportProductsToCSV(filter);

            String filename = String.format("products_%s_%s.csv",
                    filter.toLowerCase(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDispositionFormData("attachment", filename);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvData);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Export products to Excel based on filter type
     */
    @GetMapping("/export/excel")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<byte[]> exportProductsExcel(@RequestParam(defaultValue = "all") String filter) {
        try {
            byte[] excelData = exportService.exportProductsToExcel(filter);

            String filename = String.format("products_%s_%s.xlsx",
                    filter.toLowerCase(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", filename);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download import template
     */
    @GetMapping("/import/template")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        try {
            byte[] templateData = exportService.generateImportTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "product_import_template.xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(templateData);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Import products from file
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<ImportResultDTO> importProducts(@RequestParam("file") MultipartFile file) {
        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ImportResultDTO(0, 0, 0,
                                List.of(new ImportErrorDTO(0, "File is empty"))));
            }

            // Check file size (max 10MB)
            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .body(new ImportResultDTO(0, 0, 0,
                                List.of(new ImportErrorDTO(0, "File size exceeds 10MB limit"))));
            }

            ImportResultDTO result = importService.importProducts(file);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ImportResultDTO(0, 0, 0,
                            List.of(new ImportErrorDTO(0, e.getMessage()))));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(new ImportResultDTO(0, 0, 0,
                            List.of(new ImportErrorDTO(0, "File processing error: " + e.getMessage()))));
        }
    }
}