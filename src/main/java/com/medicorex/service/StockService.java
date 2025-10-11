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
import com.medicorex.service.ProductBatchService;
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
    private final ProductBatchService batchService;

    @Transactional
    public void adjustStock(StockAdjustmentDTO adjustmentDTO) {
        Product product = productRepository.findById(adjustmentDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", adjustmentDTO.getProductId()));

        // Validate stock adjustment
        int oldQuantity = product.getQuantity();
        int adjustmentQuantity = adjustmentDTO.getQuantity();
        int newQuantity = oldQuantity + adjustmentQuantity;

        log.info("Stock adjustment requested for product: {} | Current: {} | Adjustment: {} | New: {}",
                product.getName(), oldQuantity, adjustmentQuantity, newQuantity);

        if (newQuantity < 0) {
            throw new BusinessException("Insufficient stock. Current quantity: " + oldQuantity);
        }

        // === CRITICAL FIX: Handle batch consumption for stock reductions ===
        if (adjustmentQuantity < 0) {
            // Stock is being reduced - consume from batches using FIFO
            int quantityToConsume = Math.abs(adjustmentQuantity);

            try {
                log.info("Consuming {} units from batches using FIFO for product: {}",
                        quantityToConsume, product.getName());

                // Use batch service to consume stock via FIFO
                List<ProductBatchService.BatchConsumptionResult> consumptionResults =
                        batchService.consumeStock(
                                product.getId(),
                                quantityToConsume,
                                adjustmentDTO.getNotes() != null ?
                                        adjustmentDTO.getNotes() :
                                        "Stock adjustment - " + adjustmentDTO.getType()
                        );

                log.info("Successfully consumed stock from {} batch(es)", consumptionResults.size());

                // The batch service already updated product total quantity
                // Refresh product entity to get updated quantity
                product = productRepository.findById(product.getId()).orElseThrow();
                newQuantity = product.getQuantity();

            } catch (Exception e) {
                log.error("Batch consumption failed: {}", e.getMessage());
                throw new BusinessException("Failed to consume stock from batches: " + e.getMessage());
            }
        } else if (adjustmentQuantity > 0) {
            // Stock is being added - just update product total
            // Note: Actual batch creation should be done via goods receipt or manual batch creation
            product.setQuantity(newQuantity);
            product.setLastUpdated(LocalDateTime.now());
            product.setLastStockCheck(LocalDateTime.now());
            productRepository.save(product);

            log.info("Stock increased. New quantity: {}", newQuantity);
        }

        // Create stock transaction record for audit trail
        StockTransaction transaction = new StockTransaction();
        transaction.setProduct(product);
        transaction.setTransactionType(TransactionType.fromString(adjustmentDTO.getType()));
        transaction.setQuantity(Math.abs(adjustmentQuantity));
        transaction.setBalanceAfter(newQuantity);
        transaction.setType(adjustmentDTO.getType().toUpperCase());
        transaction.setReason(adjustmentDTO.getNotes() != null ?
                adjustmentDTO.getNotes() : "Stock adjustment");
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setReference(adjustmentDTO.getReference());
        transaction.setNotes(adjustmentDTO.getNotes());
        transaction.setPerformedBy(getCurrentUserId());
        transaction.setBeforeQuantity(oldQuantity);
        transaction.setAfterQuantity(newQuantity);
        transaction.setCreatedAt(LocalDateTime.now());
        stockTransactionRepository.save(transaction);

        log.info("Stock transaction recorded: ID {}", transaction.getId());

        // ===== NOTIFICATION TRIGGERS =====
        try {
            // 1. Send stock adjustment notification
            Map<String, String> params = new HashMap<>();
            params.put("productName", product.getName());
            params.put("adjustmentType", adjustmentDTO.getType().toLowerCase());
            params.put("quantity", String.valueOf(Math.abs(adjustmentQuantity)));
            params.put("newQuantity", String.valueOf(newQuantity));

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

            log.info("✅ Stock adjustment completed successfully for product: {}", product.getName());

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

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                !authentication.getName().equals("anonymousUser")) {
            try {
                String username = authentication.getName();
                User user = userRepository.findByUsername(username).orElse(null);
                if (user != null) {
                    return user.getId();
                }
            } catch (Exception e) {
                log.warn("Failed to get current user ID: {}", e.getMessage());
            }
        }

        // Fallback to system user or create if doesn't exist
        User systemUser = userRepository.findByUsername("SYSTEM").orElse(null);
        if (systemUser == null) {
            // Create system user if it doesn't exist
            systemUser = new User();
            systemUser.setUsername("SYSTEM");
            systemUser.setPassword("N/A");
            systemUser.setEmail("system@medicorex.com");
            systemUser.setFullName("System User");
            systemUser.setRole(User.UserRole.HOSPITAL_MANAGER); // Use enum instead of string
            systemUser.setActive(true);
            systemUser = userRepository.save(systemUser);
        }
        return systemUser.getId();
    }
}


// ========================================
// IMPORTANT: The pattern is always:
// ========================================
// 1. Services pass List<String> with role names
// 2. NotificationService.notifyUsersByRole() accepts List<String>
// 3. NotificationService internally converts String to UserRole enum
// 4. UserRepository.findByRoleIn() accepts List<UserRole> enum

// DO NOT pass UserRole enums from services to NotificationService
// DO NOT pass strings directly to UserRepository methods