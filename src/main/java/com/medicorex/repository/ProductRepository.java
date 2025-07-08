package com.medicorex.repository;

import com.medicorex.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByCode(String code);
    List<Product> findByCategory_Id(Long categoryId);
    List<Product> findByQuantityLessThan(Integer quantity);
}