package com.medicorex.service.supplier;

import com.medicorex.dto.*;
import com.medicorex.entity.*;
import com.medicorex.exception.BusinessException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SupplierProductService {

    private final SupplierProductRepository supplierProductRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public PageResponseDTO<SupplierProductDTO> getSupplierCatalog(Long supplierId, Pageable pageable) {
        // Verify supplier exists
        if (!supplierRepository.existsById(supplierId)) {
            throw new ResourceNotFoundException("Supplier", "id", supplierId);
        }

        Page<SupplierProduct> catalogPage = supplierProductRepository.findBySupplierId(supplierId, pageable);
        return convertToPageResponse(catalogPage);
    }

    public List<SupplierProductDTO> getProductSuppliers(Long productId) {
        // Verify product exists
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product", "id", productId);
        }

        List<SupplierProduct> suppliers = supplierProductRepository.findActiveByProductId(productId);
        return suppliers.stream()
                .map(this::convertToDTO)
                .sorted(Comparator.comparing(SupplierProductDTO::getIsPreferred).reversed()
                        .thenComparing(SupplierProductDTO::getUnitPrice))
                .collect(Collectors.toList());
    }

    public SupplierProductDTO addProductToSupplier(Long supplierId, SupplierProductCreateDTO createDTO) {
        // Verify supplier exists
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", supplierId));

        // Verify product exists
        Product product = productRepository.findById(createDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", createDTO.getProductId()));

        // Check if already exists
        if (supplierProductRepository.existsBySupplierIdAndProductId(supplierId, createDTO.getProductId())) {
            throw new BusinessException("Product already exists in supplier catalog");
        }

        // Create new supplier product
        SupplierProduct supplierProduct = new SupplierProduct();
        supplierProduct.setSupplier(supplier);
        supplierProduct.setProduct(product);
        supplierProduct.setSupplierProductCode(createDTO.getSupplierProductCode());
        supplierProduct.setSupplierProductName(createDTO.getSupplierProductName());
        supplierProduct.setUnitPrice(createDTO.getUnitPrice());
        supplierProduct.setCurrency(createDTO.getCurrency());
        supplierProduct.setDiscountPercentage(createDTO.getDiscountPercentage());
        supplierProduct.setBulkDiscountPercentage(createDTO.getBulkDiscountPercentage());
        supplierProduct.setBulkQuantityThreshold(createDTO.getBulkQuantityThreshold());
        supplierProduct.setLeadTimeDays(createDTO.getLeadTimeDays());
        supplierProduct.setMinOrderQuantity(createDTO.getMinOrderQuantity());
        supplierProduct.setMaxOrderQuantity(createDTO.getMaxOrderQuantity());
        supplierProduct.setIsPreferred(createDTO.getIsPreferred());
        supplierProduct.setIsActive(createDTO.getIsActive());
        supplierProduct.setNotes(createDTO.getNotes());
        supplierProduct.setLastPriceUpdate(LocalDate.now());

        // If setting as preferred, unset other preferred suppliers for this product
        if (Boolean.TRUE.equals(createDTO.getIsPreferred())) {
            List<SupplierProduct> existingPreferred = supplierProductRepository.findActiveByProductId(createDTO.getProductId());
            existingPreferred.stream()
                    .filter(sp -> Boolean.TRUE.equals(sp.getIsPreferred()))
                    .forEach(sp -> {
                        sp.setIsPreferred(false);
                        supplierProductRepository.save(sp);
                    });
        }

        SupplierProduct saved = supplierProductRepository.save(supplierProduct);
        log.info("Added product {} to supplier {} catalog", product.getName(), supplier.getName());

        return convertToDTO(saved);
    }

    public SupplierProductDTO updateSupplierProduct(Long id, SupplierProductCreateDTO updateDTO) {
        SupplierProduct supplierProduct = supplierProductRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SupplierProduct", "id", id));

        // Track if price changed
        boolean priceChanged = !supplierProduct.getUnitPrice().equals(updateDTO.getUnitPrice());

        // Update fields
        supplierProduct.setSupplierProductCode(updateDTO.getSupplierProductCode());
        supplierProduct.setSupplierProductName(updateDTO.getSupplierProductName());
        supplierProduct.setUnitPrice(updateDTO.getUnitPrice());
        supplierProduct.setCurrency(updateDTO.getCurrency());
        supplierProduct.setDiscountPercentage(updateDTO.getDiscountPercentage());
        supplierProduct.setBulkDiscountPercentage(updateDTO.getBulkDiscountPercentage());
        supplierProduct.setBulkQuantityThreshold(updateDTO.getBulkQuantityThreshold());
        supplierProduct.setLeadTimeDays(updateDTO.getLeadTimeDays());
        supplierProduct.setMinOrderQuantity(updateDTO.getMinOrderQuantity());
        supplierProduct.setMaxOrderQuantity(updateDTO.getMaxOrderQuantity());
        supplierProduct.setNotes(updateDTO.getNotes());
        supplierProduct.setIsActive(updateDTO.getIsActive());

        if (priceChanged) {
            supplierProduct.setLastPriceUpdate(LocalDate.now());
        }

        // Handle preferred supplier change
        if (Boolean.TRUE.equals(updateDTO.getIsPreferred()) && !Boolean.TRUE.equals(supplierProduct.getIsPreferred())) {
            // Unset other preferred suppliers for this product
            List<SupplierProduct> existingPreferred = supplierProductRepository.findActiveByProductId(supplierProduct.getProduct().getId());
            existingPreferred.stream()
                    .filter(sp -> !sp.getId().equals(id) && Boolean.TRUE.equals(sp.getIsPreferred()))
                    .forEach(sp -> {
                        sp.setIsPreferred(false);
                        supplierProductRepository.save(sp);
                    });
        }
        supplierProduct.setIsPreferred(updateDTO.getIsPreferred());

        SupplierProduct updated = supplierProductRepository.save(supplierProduct);
        log.info("Updated supplier product: {}", id);

        return convertToDTO(updated);
    }

    public void removeProductFromSupplier(Long id) {
        SupplierProduct supplierProduct = supplierProductRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SupplierProduct", "id", id));

        supplierProductRepository.delete(supplierProduct);
        log.info("Removed product from supplier catalog: {}", id);
    }

    public SupplierPriceComparisonDTO compareSupplierPrices(Long productId, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        List<SupplierProduct> suppliers = supplierProductRepository.findActiveByProductId(productId);

        if (suppliers.isEmpty()) {
            return SupplierPriceComparisonDTO.builder()
                    .productId(productId)
                    .productName(product.getName())
                    .productCode(product.getCode())
                    .currentStock(product.getQuantity())
                    .supplierOptions(new ArrayList<>())
                    .build();
        }

        // Calculate effective prices for each supplier
        List<SupplierPriceComparisonDTO.SupplierPriceOption> options = suppliers.stream()
                .map(sp -> {
                    BigDecimal effectivePrice = sp.getEffectivePrice(quantity != null ? quantity : sp.getMinOrderQuantity());
                    BigDecimal totalCost = effectivePrice.multiply(BigDecimal.valueOf(
                            quantity != null ? quantity : sp.getMinOrderQuantity()));

                    return SupplierPriceComparisonDTO.SupplierPriceOption.builder()
                            .supplierId(sp.getSupplier().getId())
                            .supplierName(sp.getSupplier().getName())
                            .supplierCode(sp.getSupplier().getCode())
                            .unitPrice(sp.getUnitPrice())
                            .currency(sp.getCurrency())
                            .effectivePrice(effectivePrice)
                            .leadTimeDays(sp.getLeadTimeDays())
                            .minOrderQuantity(sp.getMinOrderQuantity())
                            .isPreferred(sp.getIsPreferred())
                            .rating(sp.getSupplier().getRating())
                            .totalCost(totalCost)
                            .build();
                })
                .sorted(Comparator.comparing(SupplierPriceComparisonDTO.SupplierPriceOption::getIsPreferred).reversed()
                        .thenComparing(SupplierPriceComparisonDTO.SupplierPriceOption::getEffectivePrice))
                .collect(Collectors.toList());

        // Determine recommended supplier
        String recommendedSupplierId = null;
        String recommendationReason = null;

        if (!options.isEmpty()) {
            SupplierPriceComparisonDTO.SupplierPriceOption recommended = options.get(0);
            recommendedSupplierId = recommended.getSupplierId().toString();

            if (Boolean.TRUE.equals(recommended.getIsPreferred())) {
                recommendationReason = "Preferred supplier with competitive pricing";
            } else {
                recommendationReason = "Best price available";
            }
        }

        return SupplierPriceComparisonDTO.builder()
                .productId(productId)
                .productName(product.getName())
                .productCode(product.getCode())
                .currentStock(product.getQuantity())
                .supplierOptions(options)
                .recommendedSupplierId(recommendedSupplierId)
                .recommendationReason(recommendationReason)
                .build();
    }

    public void setPreferredSupplier(Long supplierId, Long productId) {
        SupplierProduct supplierProduct = supplierProductRepository.findBySupplierIdAndProductId(supplierId, productId)
                .orElseThrow(() -> new BusinessException("Supplier product relationship not found"));

        // Unset other preferred suppliers for this product
        List<SupplierProduct> existingPreferred = supplierProductRepository.findActiveByProductId(productId);
        existingPreferred.stream()
                .filter(sp -> Boolean.TRUE.equals(sp.getIsPreferred()))
                .forEach(sp -> {
                    sp.setIsPreferred(false);
                    supplierProductRepository.save(sp);
                });

        // Set this as preferred
        supplierProduct.setIsPreferred(true);
        supplierProductRepository.save(supplierProduct);

        log.info("Set supplier {} as preferred for product {}", supplierId, productId);
    }

    private SupplierProductDTO convertToDTO(SupplierProduct sp) {
        return SupplierProductDTO.builder()
                .id(sp.getId())
                .supplierId(sp.getSupplier().getId())
                .supplierName(sp.getSupplier().getName())
                .supplierCode(sp.getSupplier().getCode())
                .productId(sp.getProduct().getId())
                .productName(sp.getProduct().getName())
                .productCode(sp.getProduct().getCode())
                .supplierProductCode(sp.getSupplierProductCode())
                .supplierProductName(sp.getSupplierProductName())
                .unitPrice(sp.getUnitPrice())
                .currency(sp.getCurrency())
                .discountPercentage(sp.getDiscountPercentage())
                .bulkDiscountPercentage(sp.getBulkDiscountPercentage())
                .bulkQuantityThreshold(sp.getBulkQuantityThreshold())
                .leadTimeDays(sp.getLeadTimeDays())
                .minOrderQuantity(sp.getMinOrderQuantity())
                .maxOrderQuantity(sp.getMaxOrderQuantity())
                .isPreferred(sp.getIsPreferred())
                .isActive(sp.getIsActive())
                .lastPriceUpdate(sp.getLastPriceUpdate())
                .notes(sp.getNotes())
                .effectivePrice(sp.getEffectivePrice(sp.getMinOrderQuantity()))
                .build();
    }

    private PageResponseDTO<SupplierProductDTO> convertToPageResponse(Page<SupplierProduct> page) {
        List<SupplierProductDTO> content = page.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageResponseDTO.<SupplierProductDTO>builder()
                .content(content)
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}