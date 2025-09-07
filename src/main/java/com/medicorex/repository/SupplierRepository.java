package com.medicorex.repository;

import com.medicorex.entity.Supplier;
import com.medicorex.entity.Supplier.SupplierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findByCode(String code);
    Optional<Supplier> findByName(String name);
    Boolean existsByCode(String code);
    Boolean existsByName(String name);
    List<Supplier> findByStatus(SupplierStatus status);
    Page<Supplier> findByStatus(SupplierStatus status, Pageable pageable);

    @Query("SELECT s FROM Supplier s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(s.code) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Supplier> searchSuppliers(String searchTerm, Pageable pageable);
}