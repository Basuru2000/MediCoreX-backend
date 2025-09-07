package com.medicorex.repository;

import com.medicorex.entity.SupplierContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SupplierContactRepository extends JpaRepository<SupplierContact, Long> {
    List<SupplierContact> findBySupplierId(Long supplierId);
    List<SupplierContact> findBySupplierIdAndIsPrimaryTrue(Long supplierId);
}