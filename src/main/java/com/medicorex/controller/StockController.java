package com.medicorex.controller;

import com.medicorex.dto.PageResponseDTO;
import com.medicorex.dto.StockAdjustmentDTO;
import com.medicorex.entity.StockTransaction;
import com.medicorex.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class StockController {

    private final StockService stockService;

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<Void> adjustStock(@Valid @RequestBody StockAdjustmentDTO adjustmentDTO) {
        stockService.adjustStock(adjustmentDTO);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<PageResponseDTO<StockTransaction>> getStockTransactions(
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));
        return ResponseEntity.ok(stockService.getStockTransactions(productId, pageable));
    }

    @GetMapping("/history/{productId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF')")
    public ResponseEntity<?> getProductHistory(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // Use the existing getStockTransactions method instead
        Pageable pageable = PageRequest.of(page, size, Sort.by("transactionDate").descending());
        PageResponseDTO<StockTransaction> history =
                stockService.getStockTransactions(productId, pageable);

        return ResponseEntity.ok(history);
    }
}