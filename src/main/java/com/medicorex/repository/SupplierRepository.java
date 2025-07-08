package com.medicorex.repository;

import com.medicorex.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findByContactEmail(String email);
    Boolean existsByContactEmail(String email);
}