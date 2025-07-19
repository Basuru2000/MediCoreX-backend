package com.medicorex.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CategoryDTO {
    private Long id;

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private Long parentId;
    private String parentName;
    private List<CategoryDTO> children;
    private Long productCount; // Number of products in this category
    private Integer level; // Hierarchy level (0 for root, 1 for first level, etc.)
    private String fullPath; // Full path like "Parent > Child > SubChild"
}