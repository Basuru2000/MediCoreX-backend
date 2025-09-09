package com.medicorex.controller;

import com.medicorex.dto.*;
import com.medicorex.service.supplier.SupplierProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/supplier-products")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class SupplierProductController {

    private final SupplierProductService supplierProductService;

    @GetMapping("/supplier/{supplierId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<PageResponseDTO<SupplierProductDTO>> getSupplierCatalog(
            @PathVariable Long supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(supplierProductService.getSupplierCatalog(supplierId, pageable));
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<List<SupplierProductDTO>> getProductSuppliers(@PathVariable Long productId) {
        return ResponseEntity.ok(supplierProductService.getProductSuppliers(productId));
    }

    @PostMapping("/supplier/{supplierId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<SupplierProductDTO> addProductToSupplier(
            @PathVariable Long supplierId,
            @Valid @RequestBody SupplierProductCreateDTO createDTO) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierProductService.addProductToSupplier(supplierId, createDTO));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<SupplierProductDTO> updateSupplierProduct(
            @PathVariable Long id,
            @Valid @RequestBody SupplierProductCreateDTO updateDTO) {

        return ResponseEntity.ok(supplierProductService.updateSupplierProduct(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<Void> removeProductFromSupplier(@PathVariable Long id) {
        supplierProductService.removeProductFromSupplier(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/compare/{productId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<SupplierPriceComparisonDTO> compareSupplierPrices(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer quantity) {

        return ResponseEntity.ok(supplierProductService.compareSupplierPrices(productId, quantity));
    }

    @PutMapping("/set-preferred/{supplierId}/{productId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<Void> setPreferredSupplier(
            @PathVariable Long supplierId,
            @PathVariable Long productId) {

        supplierProductService.setPreferredSupplier(supplierId, productId);
        return ResponseEntity.ok().build();
    }
}