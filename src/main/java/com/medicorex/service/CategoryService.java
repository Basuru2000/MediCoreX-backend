package com.medicorex.service;

import com.medicorex.dto.CategoryDTO;
import com.medicorex.entity.Category;
import com.medicorex.exception.BusinessException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.CategoryRepository;
import com.medicorex.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public List<CategoryDTO> getAllCategories() {
        List<Category> categories = categoryRepository.findAll();
        return categories.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<CategoryDTO> getCategoryTree() {
        List<Category> rootCategories = categoryRepository.findByParentIsNull();
        return rootCategories.stream()
                .map(this::convertToDTOWithChildren)
                .collect(Collectors.toList());
    }

    public List<CategoryDTO> getRootCategories() {
        return categoryRepository.findByParentIsNull().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<CategoryDTO> getChildCategories(Long parentId) {
        return categoryRepository.findByParentId(parentId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public CategoryDTO getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        return convertToDTOWithChildren(category);
    }

    public CategoryDTO createCategory(CategoryDTO categoryDTO) {
        // Check if category name already exists
        if (categoryRepository.existsByName(categoryDTO.getName())) {
            throw new BusinessException("Category with name '" + categoryDTO.getName() + "' already exists");
        }

        // Validate parent category if specified
        Category parent = null;
        if (categoryDTO.getParentId() != null) {
            parent = categoryRepository.findById(categoryDTO.getParentId())
                    .orElseThrow(() -> new BusinessException("Parent category not found"));

            // Prevent circular reference
            if (parent.getId().equals(categoryDTO.getId())) {
                throw new BusinessException("Category cannot be its own parent");
            }
        }

        Category category = new Category();
        category.setName(categoryDTO.getName());
        category.setDescription(categoryDTO.getDescription());
        category.setParent(parent);

        Category savedCategory = categoryRepository.save(category);
        return convertToDTO(savedCategory);
    }

    public CategoryDTO updateCategory(Long id, CategoryDTO categoryDTO) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        // Check if new name conflicts with another category
        if (!category.getName().equals(categoryDTO.getName()) &&
                categoryRepository.existsByName(categoryDTO.getName())) {
            throw new BusinessException("Category with name '" + categoryDTO.getName() + "' already exists");
        }

        // Validate parent category if specified
        if (categoryDTO.getParentId() != null) {
            // Prevent setting itself as parent
            if (categoryDTO.getParentId().equals(id)) {
                throw new BusinessException("Category cannot be its own parent");
            }

            // Prevent circular reference
            if (isDescendant(id, categoryDTO.getParentId())) {
                throw new BusinessException("Cannot set a descendant category as parent");
            }

            Category parent = categoryRepository.findById(categoryDTO.getParentId())
                    .orElseThrow(() -> new BusinessException("Parent category not found"));
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        category.setName(categoryDTO.getName());
        category.setDescription(categoryDTO.getDescription());

        Category updatedCategory = categoryRepository.save(category);
        return convertToDTO(updatedCategory);
    }

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        // Check if category has products
        Long productCount = productRepository.countByCategoryId(id);
        if (productCount > 0) {
            throw new BusinessException("Cannot delete category with " + productCount + " products. Please reassign or delete the products first.");
        }

        // Check if category has children
        if (categoryRepository.existsByParentId(id)) {
            throw new BusinessException("Cannot delete category with subcategories. Please delete or reassign subcategories first.");
        }

        categoryRepository.deleteById(id);
    }

    private boolean isDescendant(Long categoryId, Long potentialDescendantId) {
        List<Category> descendants = categoryRepository.findAllDescendants(categoryId);
        return descendants.stream().anyMatch(c -> c.getId().equals(potentialDescendantId));
    }

    private String buildCategoryPath(Category category) {
        List<String> path = new ArrayList<>();
        Category current = category;

        while (current != null) {
            path.add(0, current.getName());
            current = current.getParent();
        }

        return String.join(" > ", path);
    }

    private int getCategoryLevel(Category category) {
        int level = 0;
        Category current = category.getParent();

        while (current != null) {
            level++;
            current = current.getParent();
        }

        return level;
    }

    private CategoryDTO convertToDTO(Category category) {
        Long productCount = productRepository.countByCategoryId(category.getId());

        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .parentName(category.getParent() != null ? category.getParent().getName() : null)
                .productCount(productCount)
                .level(getCategoryLevel(category))
                .fullPath(buildCategoryPath(category))
                .build();
    }

    private CategoryDTO convertToDTOWithChildren(Category category) {
        CategoryDTO dto = convertToDTO(category);

        // Recursively convert children
        List<CategoryDTO> childrenDTOs = category.getChildren().stream()
                .map(this::convertToDTOWithChildren)
                .collect(Collectors.toList());

        dto.setChildren(childrenDTOs);
        return dto;
    }
}