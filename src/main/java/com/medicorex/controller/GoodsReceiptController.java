package com.medicorex.controller;

import com.medicorex.dto.*;
import com.medicorex.service.procurement.GoodsReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/goods-receipts")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class GoodsReceiptController {

    private final GoodsReceiptService goodsReceiptService;

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<GoodsReceiptDTO> createGoodsReceipt(
            @Valid @RequestBody GoodsReceiptCreateDTO createDTO) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(goodsReceiptService.createGoodsReceipt(createDTO));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<GoodsReceiptDTO> getReceiptById(@PathVariable Long id) {
        return ResponseEntity.ok(goodsReceiptService.getReceiptById(id));
    }

    @GetMapping("/number/{receiptNumber}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<GoodsReceiptDTO> getReceiptByNumber(@PathVariable String receiptNumber) {
        return ResponseEntity.ok(goodsReceiptService.getReceiptByNumber(receiptNumber));
    }

    @GetMapping("/purchase-order/{poId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<List<GoodsReceiptDTO>> getReceiptsByPurchaseOrder(@PathVariable Long poId) {
        return ResponseEntity.ok(goodsReceiptService.getReceiptsByPurchaseOrder(poId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<PageResponseDTO<GoodsReceiptDTO>> getAllReceipts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "receiptDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        return ResponseEntity.ok(goodsReceiptService.getAllReceipts(pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<PageResponseDTO<GoodsReceiptDTO>> searchReceipts(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receiptDate"));
        return ResponseEntity.ok(goodsReceiptService.searchReceipts(
                supplierId, search, startDate, endDate, pageable));
    }

    // ✨ PHASE 3.2: ACCEPT/REJECT ENDPOINTS

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<GoodsReceiptDTO> acceptGoodsReceipt(
            @PathVariable Long id,
            @RequestBody(required = false) GoodsReceiptAcceptDTO acceptDTO) {

        if (acceptDTO == null) {
            acceptDTO = new GoodsReceiptAcceptDTO();
        }

        return ResponseEntity.ok(goodsReceiptService.acceptGoodsReceipt(id, acceptDTO));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<GoodsReceiptDTO> rejectGoodsReceipt(
            @PathVariable Long id,
            @Valid @RequestBody GoodsReceiptRejectDTO rejectDTO) {

        return ResponseEntity.ok(goodsReceiptService.rejectGoodsReceipt(id, rejectDTO));
    }

    @GetMapping("/pending-approval")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<PageResponseDTO<GoodsReceiptDTO>> getPendingApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receiptDate"));
        return ResponseEntity.ok(goodsReceiptService.getPendingApprovals(pageable));
    }
}