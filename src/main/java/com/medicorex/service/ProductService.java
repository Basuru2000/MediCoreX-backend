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
    private final BarcodeService barcodeService;

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

    public ProductDTO getProductByBarcode(String barcode) {
        Product product = productRepository.findByBarcode(barcode)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "barcode", barcode));
        return convertToDTO(product);
    }

    public ProductDTO createProduct(ProductCreateDTO productCreateDTO) {
        // Check if product code already exists
        if (productCreateDTO.getCode() != null &&
                productRepository.findByCode(productCreateDTO.getCode()).isPresent()) {
            throw new BusinessException("Product with code " + productCreateDTO.getCode() + " already exists");
        }

        // Check if barcode already exists
        if (productCreateDTO.getBarcode() != null && !productCreateDTO.getBarcode().trim().isEmpty() &&
                productRepository.findByBarcode(productCreateDTO.getBarcode()).isPresent()) {
            throw new BusinessException("Product with barcode " + productCreateDTO.getBarcode() + " already exists");
        }

        // Validate barcode format if provided
        if (productCreateDTO.getBarcode() != null && !productCreateDTO.getBarcode().trim().isEmpty()) {
            if (!barcodeService.isValidBarcode(productCreateDTO.getBarcode())) {
                throw new BusinessException("Invalid barcode format. Barcode must be alphanumeric and at least 8 characters long.");
            }
        }

        Category category = categoryRepository.findById(productCreateDTO.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", productCreateDTO.getCategoryId()));

        Product product = new Product();
        updateProductFromDTO(product, productCreateDTO, category);

        Product savedProduct = productRepository.save(product);

        // Generate barcode if not provided
        if (savedProduct.getBarcode() == null || savedProduct.getBarcode().trim().isEmpty()) {
            savedProduct.setBarcode(barcodeService.generateBarcode(savedProduct));
        }

        // Generate QR code
        try {
            String qrCode = barcodeService.generateQRCode(savedProduct);
            savedProduct.setQrCode(qrCode);
        } catch (Exception e) {
            // Log error but don't fail the product creation
            System.err.println("Failed to generate QR code: " + e.getMessage());
        }

        Product finalProduct = productRepository.save(savedProduct);
        return convertToDTO(finalProduct);
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

        // Check if new barcode conflicts with another product
        if (productCreateDTO.getBarcode() != null && !productCreateDTO.getBarcode().trim().isEmpty() &&
                !productCreateDTO.getBarcode().equals(product.getBarcode()) &&
                productRepository.findByBarcode(productCreateDTO.getBarcode()).isPresent()) {
            throw new BusinessException("Product with barcode " + productCreateDTO.getBarcode() + " already exists");
        }

        // Validate barcode format if provided
        if (productCreateDTO.getBarcode() != null && !productCreateDTO.getBarcode().trim().isEmpty()) {
            if (!barcodeService.isValidBarcode(productCreateDTO.getBarcode())) {
                throw new BusinessException("Invalid barcode format. Barcode must be alphanumeric and at least 8 characters long.");
            }
        }

        Category category = categoryRepository.findById(productCreateDTO.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", productCreateDTO.getCategoryId()));

        updateProductFromDTO(product, productCreateDTO, category);

        // Generate barcode if not provided and doesn't exist
        if ((product.getBarcode() == null || product.getBarcode().trim().isEmpty()) && product.getId() != null) {
            product.setBarcode(barcodeService.generateBarcode(product));
        }

        // Regenerate QR code with updated information
        try {
            String qrCode = barcodeService.generateQRCode(product);
            product.setQrCode(qrCode);
        } catch (Exception e) {
            // Log error but don't fail the product update
            System.err.println("Failed to generate QR code: " + e.getMessage());
        }

        Product updatedProduct = productRepository.save(product);
        return convertToDTO(updatedProduct);
    }

    public String generateBarcodeImage(String barcode) {
        try {
            return barcodeService.generateBarcodeImage(barcode);
        } catch (Exception e) {
            throw new BusinessException("Failed to generate barcode image: " + e.getMessage());
        }
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
                                (product.getBarcode() != null && product.getBarcode().toLowerCase().contains(query.toLowerCase())) ||
                                (product.getManufacturer() != null && product.getManufacturer().toLowerCase().contains(query.toLowerCase()))
                )
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private void updateProductFromDTO(Product product, ProductCreateDTO dto, Category category) {
        product.setName(dto.getName());
        product.setCode(dto.getCode());
        product.setBarcode(dto.getBarcode());
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
                .barcode(product.getBarcode())
                .qrCode(product.getQrCode())
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

        // Create PageResponseDTO without builder
        PageResponseDTO<ProductDTO> response = new PageResponseDTO<>();
        response.setContent(productDTOs);
        response.setPage(productPage.getNumber());
        response.setTotalPages(productPage.getTotalPages());
        response.setTotalElements(productPage.getTotalElements());
        response.setLast(productPage.isLast());

        return response;
    }
}