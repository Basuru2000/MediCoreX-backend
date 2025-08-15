package com.medicorex.repository;

import com.medicorex.entity.StockTransaction;
import com.medicorex.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {

    // Find by product ID with pagination
    Page<StockTransaction> findByProductIdOrderByTransactionDateDesc(Long productId, Pageable pageable);

    // Find all with ordering
    Page<StockTransaction> findAllByOrderByTransactionDateDesc(Pageable pageable);

    // Find by product ID
    List<StockTransaction> findByProductId(Long productId);

    // Count by product ID - ADD THIS METHOD
    Long countByProductId(Long productId);

    // Find by transaction type
    List<StockTransaction> findByTransactionType(TransactionType transactionType);

    // Find transactions in date range
    List<StockTransaction> findByTransactionDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    // Find by reference
    List<StockTransaction> findByReference(String reference);

    // Custom query for transaction summary
    @Query("SELECT st FROM StockTransaction st WHERE st.product.id = :productId " +
            "AND st.transactionDate BETWEEN :startDate AND :endDate " +
            "ORDER BY st.transactionDate DESC")
    List<StockTransaction> findProductTransactionsInDateRange(
            @Param("productId") Long productId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // Count transactions by type
    @Query("SELECT COUNT(st) FROM StockTransaction st WHERE st.transactionType = :type")
    Long countByTransactionType(@Param("type") TransactionType type);
}