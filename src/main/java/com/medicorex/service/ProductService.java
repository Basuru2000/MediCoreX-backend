package com.medicorex.service;

import com.medicorex.dto.PageResponseDTO;
import com.medicorex.dto.ProductCreateDTO;
import com.medicorex.dto.ProductDTO;
import com.medicorex.entity.Category;
import com.medicorex.entity.Product;
import com.medicorex.exception.BusinessException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.CategoryRepository;
import com.medicorex.repository.ProductRepository;
import com.medicorex.repository.StockTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final FileService fileService;

    public PageResponseDTO<ProductDTO> getAllProducts(Pageable pageable) {
        Page<Product> productPage = productRepository.findAll(pageable);
        return convertToPageResponse(productPage);
    }

    public PageResponseDTO<ProductDTO> getProductsByCategory(Long categoryId, Pageable pageable) {
        Page<Product> productPage = productRepository.findByCategoryId(categoryId, pageable);
        return convertToPageResponse(productPage);
    }

    public List<ProductDTO> getLowStockProducts() {
        return productRepository.findAll().stream()
                .filter(product -> product.getQuantity() <= product.getMinStockLevel())
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ProductDTO> getExpiringProducts(int daysAhead) {
        LocalDate expiryThreshold = LocalDate.now().plusDays(daysAhead);
        return productRepository.findAll().stream()
                .filter(product -> product.getExpiryDate() != null &&
                        product.getExpiryDate().isBefore(expiryThreshold))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public ProductDTO getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return convertToDTO(product);
    }

    public ProductDTO getProductByCode(String code) {
        Product product = productRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "code", code));
        return convertToDTO(product);
    }

    public ProductDTO createProduct(ProductCreateDTO productCreateDTO) {
        // Check if product code already exists
        if (productCreateDTO.getCode() != null &&
                productRepository.findByCode(productCreateDTO.getCode()).isPresent()) {
            throw new BusinessException("Product with code " + productCreateDTO.getCode() + " already exists");
        }

        Category category = categoryRepository.findById(productCreateDTO.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", productCreateDTO.getCategoryId()));

        Product product = new Product();
        updateProductFromDTO(product, productCreateDTO, category);

        Product savedProduct = productRepository.save(product);
        return convertToDTO(savedProduct);
    }

    public ProductDTO updateProduct(Long id, ProductCreateDTO productCreateDTO) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        // Check if new code conflicts with another product
        if (productCreateDTO.getCode() != null &&
                !productCreateDTO.getCode().equals(product.getCode()) &&
                productRepository.findByCode(productCreateDTO.getCode()).isPresent()) {
            throw new BusinessException("Product with code " + productCreateDTO.getCode() + " already exists");
        }

        Category category = categoryRepository.findById(productCreateDTO.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", productCreateDTO.getCategoryId()));

        updateProductFromDTO(product, productCreateDTO, category);

        Product updatedProduct = productRepository.save(product);
        return convertToDTO(updatedProduct);
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        // Check if product has stock transactions
        Long transactionCount = stockTransactionRepository.countByProductId(id);
        if (transactionCount > 0) {
            throw new BusinessException("Cannot delete product with " + transactionCount +
                    " stock transactions. This product has transaction history that must be preserved.");
        }

        // Delete associated image if exists
        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            fileService.deleteFile(product.getImageUrl());
        }

        productRepository.deleteById(id);
    }

    public List<ProductDTO> searchProducts(String query) {
        return productRepository.findAll().stream()
                .filter(product ->
                        product.getName().toLowerCase().contains(query.toLowerCase()) ||
                                (product.getCode() != null && product.getCode().toLowerCase().contains(query.toLowerCase())) ||
                                (product.getManufacturer() != null && product.getManufacturer().toLowerCase().contains(query.toLowerCase()))
                )
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private void updateProductFromDTO(Product product, ProductCreateDTO dto, Category category) {
        product.setName(dto.getName());
        product.setCode(dto.getCode());
        product.setDescription(dto.getDescription());
        product.setCategory(category);
        product.setQuantity(dto.getQuantity());
        product.setMinStockLevel(dto.getMinStockLevel());
        product.setUnit(dto.getUnit());
        product.setUnitPrice(dto.getUnitPrice());
        product.setExpiryDate(dto.getExpiryDate());
        product.setBatchNumber(dto.getBatchNumber());
        product.setManufacturer(dto.getManufacturer());
        product.setImageUrl(dto.getImageUrl());
    }

    private ProductDTO convertToDTO(Product product) {
        String stockStatus = determineStockStatus(product);
        boolean isExpiringSoon = isProductExpiringSoon(product);

        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .code(product.getCode())
                .description(product.getDescription())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .quantity(product.getQuantity())
                .minStockLevel(product.getMinStockLevel())
                .unit(product.getUnit())
                .unitPrice(product.getUnitPrice())
                .expiryDate(product.getExpiryDate())
                .batchNumber(product.getBatchNumber())
                .manufacturer(product.getManufacturer())
                .imageUrl(product.getImageUrl())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .stockStatus(stockStatus)
                .isExpiringSoon(isExpiringSoon)
                .build();
    }

    private String determineStockStatus(Product product) {
        if (product.getQuantity() == 0) {
            return "OUT_OF_STOCK";
        } else if (product.getQuantity() <= product.getMinStockLevel()) {
            return "LOW";
        } else {
            return "NORMAL";
        }
    }

    private boolean isProductExpiringSoon(Product product) {
        if (product.getExpiryDate() == null) {
            return false;
        }
        LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
        return product.getExpiryDate().isBefore(thirtyDaysFromNow);
    }

    private PageResponseDTO<ProductDTO> convertToPageResponse(Page<Product> productPage) {
        List<ProductDTO> productDTOs = productPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageResponseDTO.<ProductDTO>builder()
                .content(productDTOs)
                .pageNumber(productPage.getNumber())
                .pageSize(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .last(productPage.isLast())
                .first(productPage.isFirst())
                .build();
    }
}