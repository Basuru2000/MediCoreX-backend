package com.medicorex.repository;

import com.medicorex.entity.SupplierContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SupplierContactRepository extends JpaRepository<SupplierContact, Long> {

    List<SupplierContact> findBySupplierIdAndIsPrimaryTrue(Long supplierId);

    List<SupplierContact> findBySupplierId(Long supplierId);

    @Query("SELECT c FROM SupplierContact c WHERE c.supplier.id = :supplierId ORDER BY c.isPrimary DESC, c.name ASC")
    List<SupplierContact> findBySupplierIdOrdered(@Param("supplierId") Long supplierId);

    boolean existsBySupplierIdAndEmail(Long supplierId, String email);
}