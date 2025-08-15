package com.medicorex.service;

import com.medicorex.dto.PageResponseDTO;
import com.medicorex.dto.StockAdjustmentDTO;
import com.medicorex.entity.Product;
import com.medicorex.entity.StockTransaction;
import com.medicorex.entity.TransactionType;
import com.medicorex.entity.User;
import com.medicorex.exception.BusinessException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.ProductRepository;
import com.medicorex.repository.StockTransactionRepository;
import com.medicorex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StockService {

    private final ProductRepository productRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public void adjustStock(StockAdjustmentDTO adjustmentDTO) {
        Product product = productRepository.findById(adjustmentDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", adjustmentDTO.getProductId()));

        // Validate stock adjustment
        int oldQuantity = product.getQuantity();
        int newQuantity = oldQuantity + adjustmentDTO.getQuantity();

        if (newQuantity < 0) {
            throw new BusinessException("Insufficient stock. Current quantity: " + oldQuantity);
        }

        // Update product quantity
        product.setQuantity(newQuantity);
        product.setLastUpdated(LocalDateTime.now());
        product.setLastStockCheck(LocalDateTime.now());
        productRepository.save(product);

        // Create stock transaction record
        StockTransaction transaction = new StockTransaction();
        transaction.setProduct(product);
        transaction.setTransactionType(TransactionType.fromString(adjustmentDTO.getType()));
        transaction.setQuantity(Math.abs(adjustmentDTO.getQuantity()));
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setReference(adjustmentDTO.getReference());
        transaction.setNotes(adjustmentDTO.getNotes());
        transaction.setPerformedBy(getCurrentUsername());
        transaction.setBeforeQuantity(oldQuantity);
        transaction.setAfterQuantity(newQuantity);
        transaction.setCreatedAt(LocalDateTime.now());
        stockTransactionRepository.save(transaction);

        // ===== NOTIFICATION TRIGGERS =====
        try {
            // 1. Send stock adjustment notification
            Map<String, String> params = new HashMap<>();
            params.put("productName", product.getName());
            params.put("adjustmentType", adjustmentDTO.getType().toLowerCase());
            params.put("quantity", String.valueOf(Math.abs(adjustmentDTO.getQuantity())));
            params.put("newQuantity", String.valueOf(newQuantity));

            // Notify relevant users
            List<String> roles = Arrays.asList("HOSPITAL_MANAGER", "PHARMACY_STAFF");
            notificationService.notifyUsersByRole(roles, "STOCK_ADJUSTED", params,
                    Map.of("productId", product.getId(), "transactionId", transaction.getId()));

            // 2. Check for low stock condition
            if (newQuantity > 0 && newQuantity <= product.getMinStock()) {
                Map<String, String> lowStockParams = new HashMap<>();
                lowStockParams.put("productName", product.getName());
                lowStockParams.put("quantity", String.valueOf(newQuantity));
                lowStockParams.put("minStock", String.valueOf(product.getMinStock()));

                notificationService.notifyUsersByRole(roles, "LOW_STOCK", lowStockParams,
                        Map.of("productId", product.getId()));
            }

            // 3. Check for out of stock condition
            if (newQuantity == 0) {
                Map<String, String> outOfStockParams = new HashMap<>();
                outOfStockParams.put("productName", product.getName());

                notificationService.notifyUsersByRole(roles, "OUT_OF_STOCK", outOfStockParams,
                        Map.of("productId", product.getId()));
            }

            log.info("Stock adjusted for product: {} by user: {}", product.getName(), getCurrentUsername());

        } catch (Exception e) {
            log.error("Failed to send stock adjustment notifications: {}", e.getMessage());
            // Don't fail the transaction for notification errors
        }
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<StockTransaction> getStockTransactions(Long productId, Pageable pageable) {
        Page<StockTransaction> transactions;

        if (productId != null) {
            transactions = stockTransactionRepository.findByProductIdOrderByTransactionDateDesc(productId, pageable);
        } else {
            transactions = stockTransactionRepository.findAllByOrderByTransactionDateDesc(pageable);
        }

        // Fixed: Explicitly specify the generic type
        PageResponseDTO<StockTransaction> response = new PageResponseDTO<>();
        response.setContent(transactions.getContent());
        response.setPage(transactions.getNumber());
        response.setTotalPages(transactions.getTotalPages());
        response.setTotalElements(transactions.getTotalElements());
        response.setLast(transactions.isLast());

        return response;
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return "SYSTEM";
        }
        return "SYSTEM";
    }
}