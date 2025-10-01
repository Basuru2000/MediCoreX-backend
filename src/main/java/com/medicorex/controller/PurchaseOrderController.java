package com.medicorex.controller;

import com.medicorex.dto.*;
import com.medicorex.entity.PurchaseOrder.POStatus;
import com.medicorex.service.procurement.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<PurchaseOrderDTO> createPurchaseOrder(
            @Valid @RequestBody PurchaseOrderCreateDTO createDTO) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(purchaseOrderService.createPurchaseOrder(createDTO));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER', 'PHARMACY_STAFF')")
    public ResponseEntity<PurchaseOrderDTO> getPurchaseOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.getPurchaseOrderById(id));
    }

    @GetMapping("/number/{poNumber}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER', 'PHARMACY_STAFF')")
    public ResponseEntity<PurchaseOrderDTO> getPurchaseOrderByNumber(@PathVariable String poNumber) {
        return ResponseEntity.ok(purchaseOrderService.getPurchaseOrderByNumber(poNumber));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER', 'PHARMACY_STAFF')")
    public ResponseEntity<PageResponseDTO<PurchaseOrderDTO>> getAllPurchaseOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        return ResponseEntity.ok(purchaseOrderService.getAllPurchaseOrders(pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER', 'PHARMACY_STAFF')")
    public ResponseEntity<PageResponseDTO<PurchaseOrderDTO>> searchPurchaseOrders(
            @RequestParam(required = false) POStatus status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderDate"));
        return ResponseEntity.ok(purchaseOrderService.searchPurchaseOrders(
                status, supplierId, search, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<PurchaseOrderDTO> updatePurchaseOrder(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderCreateDTO updateDTO) {

        return ResponseEntity.ok(purchaseOrderService.updatePurchaseOrder(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<Void> deletePurchaseOrder(@PathVariable Long id) {
        purchaseOrderService.deletePurchaseOrder(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<PurchaseOrderDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam POStatus status) {

        return ResponseEntity.ok(purchaseOrderService.updateStatus(id, status));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<PurchaseOrderSummaryDTO> getPurchaseOrderSummary() {
        return ResponseEntity.ok(purchaseOrderService.getPurchaseOrderSummary());
    }
}