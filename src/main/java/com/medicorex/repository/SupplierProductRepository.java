package com.medicorex.repository;

import com.medicorex.entity.SupplierProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierProductRepository extends JpaRepository<SupplierProduct, Long> {

    @Query("SELECT sp FROM SupplierProduct sp " +
            "JOIN FETCH sp.supplier s " +
            "JOIN FETCH sp.product p " +
            "WHERE sp.supplier.id = :supplierId")
    Page<SupplierProduct> findBySupplierId(@Param("supplierId") Long supplierId, Pageable pageable);

    @Query("SELECT sp FROM SupplierProduct sp " +
            "JOIN FETCH sp.supplier s " +
            "JOIN FETCH sp.product p " +
            "WHERE sp.product.id = :productId AND sp.isActive = true")
    List<SupplierProduct> findActiveByProductId(@Param("productId") Long productId);

    @Query("SELECT sp FROM SupplierProduct sp " +
            "WHERE sp.supplier.id = :supplierId AND sp.product.id = :productId")
    Optional<SupplierProduct> findBySupplierIdAndProductId(
            @Param("supplierId") Long supplierId,
            @Param("productId") Long productId
    );

    @Query("SELECT sp FROM SupplierProduct sp " +
            "JOIN FETCH sp.supplier s " +
            "JOIN FETCH sp.product p " +
            "WHERE sp.isPreferred = true AND sp.isActive = true")
    List<SupplierProduct> findPreferredSuppliers();

    @Query("SELECT COUNT(sp) FROM SupplierProduct sp " +
            "WHERE sp.product.id = :productId AND sp.isActive = true")
    Long countActiveSuppliersForProduct(@Param("productId") Long productId);

    boolean existsBySupplierIdAndProductId(Long supplierId, Long productId);
}