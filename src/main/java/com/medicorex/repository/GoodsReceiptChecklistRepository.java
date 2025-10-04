package com.medicorex.repository;

import com.medicorex.entity.GoodsReceiptChecklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoodsReceiptChecklistRepository extends JpaRepository<GoodsReceiptChecklist, Long> {

    Optional<GoodsReceiptChecklist> findByReceiptId(Long receiptId);

    @Query("SELECT c FROM GoodsReceiptChecklist c LEFT JOIN FETCH c.answers WHERE c.receipt.id = :receiptId")
    Optional<GoodsReceiptChecklist> findByReceiptIdWithAnswers(@Param("receiptId") Long receiptId);

    List<GoodsReceiptChecklist> findByOverallResult(String overallResult);

    boolean existsByReceiptId(Long receiptId);
}