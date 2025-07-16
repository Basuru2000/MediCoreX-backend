package com.medicorex.service;

import com.medicorex.dto.PageResponseDTO;
import com.medicorex.dto.StockAdjustmentDTO;
import com.medicorex.entity.Product;
import com.medicorex.entity.StockTransaction;
import com.medicorex.entity.User;
import com.medicorex.exception.BusinessException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.ProductRepository;
import com.medicorex.repository.StockTransactionRepository;
import com.medicorex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class StockService {

    private final ProductRepository productRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final UserRepository userRepository;

    public void adjustStock(StockAdjustmentDTO adjustmentDTO) {
        Product product = productRepository.findById(adjustmentDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", adjustmentDTO.getProductId()));

        // Validate stock adjustment
        int newQuantity = product.getQuantity() + adjustmentDTO.getQuantity();
        if (newQuantity < 0) {
            throw new BusinessException("Insufficient stock. Current stock: " + product.getQuantity() +
                    ", Requested reduction: " + Math.abs(adjustmentDTO.getQuantity()));
        }

        // Get current user
        User currentUser = getCurrentUser();

        // Update product quantity
        product.setQuantity(newQuantity);
        productRepository.save(product);

        // Create stock transaction record
        StockTransaction transaction = new StockTransaction();
        transaction.setProduct(product);
        transaction.setQuantity(adjustmentDTO.getQuantity());
        transaction.setBalanceAfter(newQuantity);
        transaction.setType(StockTransaction.TransactionType.valueOf(adjustmentDTO.getType()));
        transaction.setReason(adjustmentDTO.getReason());
        transaction.setReference(adjustmentDTO.getReference());
        transaction.setPerformedBy(currentUser);

        stockTransactionRepository.save(transaction);
    }

    public PageResponseDTO<StockTransaction> getStockTransactions(Long productId, Pageable pageable) {
        Page<StockTransaction> transactionPage;

        if (productId != null) {
            transactionPage = stockTransactionRepository.findByProductId(productId, pageable);
        } else {
            transactionPage = stockTransactionRepository.findAll(pageable);
        }

        return PageResponseDTO.<StockTransaction>builder()
                .content(transactionPage.getContent())
                .pageNumber(transactionPage.getNumber())
                .pageSize(transactionPage.getSize())
                .totalElements(transactionPage.getTotalElements())
                .totalPages(transactionPage.getTotalPages())
                .last(transactionPage.isLast())
                .first(transactionPage.isFirst())
                .build();
    }

    public List<StockTransaction> getProductStockHistory(Long productId) {
        return stockTransactionRepository.findByProductIdOrderByTransactionDateDesc(productId);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }
}