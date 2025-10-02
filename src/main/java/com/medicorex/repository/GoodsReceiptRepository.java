package com.medicorex.repository;

import com.medicorex.entity.GoodsReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

    Optional<GoodsReceipt> findByReceiptNumber(String receiptNumber);

    List<GoodsReceipt> findByPurchaseOrderId(Long poId);

    @Query("SELECT gr FROM GoodsReceipt gr " +
            "JOIN FETCH gr.purchaseOrder po " +
            "JOIN FETCH gr.supplier s " +
            "JOIN FETCH gr.receivedBy u " +
            "WHERE gr.id = :id")
    Optional<GoodsReceipt> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT gr FROM GoodsReceipt gr " +
            "WHERE (:supplierId IS NULL OR gr.supplier.id = :supplierId) " +
            "AND (:search IS NULL OR gr.receiptNumber LIKE %:search% OR gr.poNumber LIKE %:search%) " +
            "AND (:startDate IS NULL OR gr.receiptDate >= :startDate) " +
            "AND (:endDate IS NULL OR gr.receiptDate <= :endDate)")
    Page<GoodsReceipt> searchReceipts(
            @Param("supplierId") Long supplierId,
            @Param("search") String search,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    boolean existsByReceiptNumber(String receiptNumber);

    @Query("SELECT COUNT(gr) FROM GoodsReceipt gr WHERE gr.purchaseOrder.id = :poId")
    Long countByPurchaseOrderId(@Param("poId") Long poId);
}