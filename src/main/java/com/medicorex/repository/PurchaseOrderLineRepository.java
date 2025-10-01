package com.medicorex.repository;

import com.medicorex.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {

    List<PurchaseOrderLine> findByPurchaseOrderId(Long poId);

    @Query("SELECT pol FROM PurchaseOrderLine pol " +
            "WHERE pol.purchaseOrder.id = :poId " +
            "ORDER BY pol.id ASC")
    List<PurchaseOrderLine> findByPoIdOrdered(@Param("poId") Long poId);

    @Query("SELECT SUM(pol.quantity) FROM PurchaseOrderLine pol " +
            "WHERE pol.purchaseOrder.id = :poId")
    Integer sumQuantityByPoId(@Param("poId") Long poId);

    @Query("SELECT SUM(pol.receivedQuantity) FROM PurchaseOrderLine pol " +
            "WHERE pol.purchaseOrder.id = :poId")
    Integer sumReceivedQuantityByPoId(@Param("poId") Long poId);
}