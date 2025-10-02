package com.medicorex.repository;

import com.medicorex.entity.GoodsReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLine, Long> {

    List<GoodsReceiptLine> findByReceiptId(Long receiptId);

    @Query("SELECT grl FROM GoodsReceiptLine grl " +
            "WHERE grl.receipt.id = :receiptId " +
            "ORDER BY grl.id ASC")
    List<GoodsReceiptLine> findByReceiptIdOrdered(@Param("receiptId") Long receiptId);

    @Query("SELECT SUM(grl.receivedQuantity) FROM GoodsReceiptLine grl " +
            "WHERE grl.receipt.id = :receiptId")
    Integer sumReceivedQuantityByReceiptId(@Param("receiptId") Long receiptId);
}