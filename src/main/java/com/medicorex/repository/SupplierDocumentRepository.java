package com.medicorex.repository;

import com.medicorex.entity.SupplierDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface SupplierDocumentRepository extends JpaRepository<SupplierDocument, Long> {
    List<SupplierDocument> findBySupplierId(Long supplierId);
    List<SupplierDocument> findByExpiryDateBefore(LocalDate date);
}