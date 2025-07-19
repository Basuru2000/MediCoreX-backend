package com.medicorex.repository;

import com.medicorex.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);
    Boolean existsByName(String name);

    // Find all root categories (no parent)
    List<Category> findByParentIsNull();

    // Find all children of a category
    List<Category> findByParentId(Long parentId);

    // Check if a category has children
    Boolean existsByParentId(Long parentId);

    // Find all categories with their parent eagerly loaded
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent")
    List<Category> findAllWithParent();

    // Get all descendants of a category
    @Query(value = "WITH RECURSIVE category_tree AS (" +
            "SELECT * FROM categories WHERE id = :categoryId " +
            "UNION ALL " +
            "SELECT c.* FROM categories c " +
            "INNER JOIN category_tree ct ON c.parent_id = ct.id" +
            ") SELECT * FROM category_tree", nativeQuery = true)
    List<Category> findAllDescendants(@Param("categoryId") Long categoryId);
}