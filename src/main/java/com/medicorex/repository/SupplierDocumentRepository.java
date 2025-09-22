package com.medicorex.repository;

import com.medicorex.entity.SupplierDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface SupplierDocumentRepository extends JpaRepository<SupplierDocument, Long> {

    List<SupplierDocument> findBySupplierId(Long supplierId);

    List<SupplierDocument> findBySupplierIdOrderByCreatedAtDesc(Long supplierId);

    @Query("SELECT d FROM SupplierDocument d WHERE d.expiryDate <= :date AND d.expiryDate IS NOT NULL")
    List<SupplierDocument> findExpiringDocuments(@Param("date") LocalDate date);

    @Query("SELECT d FROM SupplierDocument d WHERE d.supplier.id = :supplierId AND d.documentType = :type")
    List<SupplierDocument> findBySupplierIdAndType(@Param("supplierId") Long supplierId, @Param("type") String type);

    @Query("SELECT COUNT(d) FROM SupplierDocument d WHERE d.supplier.id = :supplierId")
    Long countBySupplierIdCustom(@Param("supplierId") Long supplierId);

    List<SupplierDocument> findByExpiryDateBefore(LocalDate date);

    @Query("SELECT d FROM SupplierDocument d WHERE d.expiryDate BETWEEN :startDate AND :endDate")
    List<SupplierDocument> findDocumentsExpiringBetween(@Param("startDate") LocalDate startDate,
                                                        @Param("endDate") LocalDate endDate);
}