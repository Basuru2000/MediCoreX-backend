package com.medicorex.repository;

import com.medicorex.entity.StockTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {
    Page<StockTransaction> findByProductId(Long productId, Pageable pageable);

    List<StockTransaction> findByProductIdOrderByTransactionDateDesc(Long productId);

    Page<StockTransaction> findByTransactionDateBetween(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    List<StockTransaction> findByTypeAndTransactionDateBetween(
            StockTransaction.TransactionType type,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
}