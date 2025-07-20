package com.medicorex.service;

import com.medicorex.dto.*;
import com.medicorex.entity.Product;
import com.medicorex.repository.CategoryRepository;
import com.medicorex.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Generate comprehensive stock valuation report
     */
    public StockValuationDTO generateStockValuationReport() {
        List<Product> allProducts = productRepository.findAll();

        // Calculate total inventory value
        BigDecimal totalInventoryValue = calculateTotalInventoryValue(allProducts);

        // Get stock status breakdown
        Map<String, Long> stockStatusCount = getStockStatusCount(allProducts);
        Map<String, BigDecimal> stockStatusValue = getStockStatusValue(allProducts);

        // Get expiring products analysis
        List<Product> expiringProducts = getExpiringProducts(allProducts, 30);
        Long expiringProductsCount = (long) expiringProducts.size();
        BigDecimal expiringProductsValue = calculateTotalValue(expiringProducts);

        // Get low stock analysis
        List<Product> lowStockProducts = getLowStockProducts(allProducts);
        Long lowStockProductsCount = (long) lowStockProducts.size();
        BigDecimal lowStockProductsValue = calculateTotalValue(lowStockProducts);

        // Get category valuations
        List<CategoryValuationDTO> categoryValuations = generateCategoryValuations(allProducts, totalInventoryValue);

        // Get top products by value
        List<ProductValuationDTO> topProducts = getTopProductsByValue(allProducts, totalInventoryValue, 10);

        return StockValuationDTO.builder()
                .totalInventoryValue(totalInventoryValue)
                .totalProducts((long) allProducts.size())
                .totalCategories((long) categoryRepository.count())
                .totalQuantity(allProducts.stream().mapToLong(p -> p.getQuantity()).sum())
                .stockStatusCount(stockStatusCount)
                .stockStatusValue(stockStatusValue)
                .expiringProductsCount(expiringProductsCount)
                .expiringProductsValue(expiringProductsValue)
                .lowStockProductsCount(lowStockProductsCount)
                .lowStockProductsValue(lowStockProductsValue)
                .categoryValuations(categoryValuations)
                .topProductsByValue(topProducts)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Get valuation summary cards for dashboard
     */
    public List<ValuationSummaryDTO> getValuationSummary() {
        List<Product> allProducts = productRepository.findAll();
        BigDecimal totalValue = calculateTotalInventoryValue(allProducts);

        List<ValuationSummaryDTO> summaries = new ArrayList<>();

        // Total Inventory Value
        summaries.add(ValuationSummaryDTO.builder()
                .label("Total Inventory Value")
                .description("Total value of all products in stock")
                .value(totalValue)
                .count((long) allProducts.size())
                .trend("STABLE")
                .percentageChange(BigDecimal.ZERO)
                .color("#1976d2")
                .icon("inventory")
                .build());

        // Low Stock Value
        List<Product> lowStockProducts = getLowStockProducts(allProducts);
        BigDecimal lowStockValue = calculateTotalValue(lowStockProducts);
        summaries.add(ValuationSummaryDTO.builder()
                .label("Low Stock Value")
                .description("Value of products below minimum stock level")
                .value(lowStockValue)
                .count((long) lowStockProducts.size())
                .trend("DOWN")
                .percentageChange(calculatePercentage(lowStockValue, totalValue))
                .color("#f57c00")
                .icon("warning")
                .build());

        // Expiring Products Value
        List<Product> expiringProducts = getExpiringProducts(allProducts, 30);
        BigDecimal expiringValue = calculateTotalValue(expiringProducts);
        summaries.add(ValuationSummaryDTO.builder()
                .label("Expiring Products Value")
                .description("Value of products expiring within 30 days")
                .value(expiringValue)
                .count((long) expiringProducts.size())
                .trend("UP")
                .percentageChange(calculatePercentage(expiringValue, totalValue))
                .color("#d32f2f")
                .icon("schedule")
                .build());

        // Average Product Value
        BigDecimal avgValue = totalValue.divide(BigDecimal.valueOf(allProducts.size()), 2, RoundingMode.HALF_UP);
        summaries.add(ValuationSummaryDTO.builder()
                .label("Average Product Value")
                .description("Average value per product type")
                .value(avgValue)
                .count((long) allProducts.size())
                .trend("STABLE")
                .percentageChange(BigDecimal.ZERO)
                .color("#388e3c")
                .icon("analytics")
                .build());

        return summaries;
    }

    /**
     * Get product-wise valuation with pagination
     */
    public List<ProductValuationDTO> getProductValuation(String sortBy, String sortDirection) {
        List<Product> products = productRepository.findAll();
        BigDecimal totalValue = calculateTotalInventoryValue(products);

        List<ProductValuationDTO> valuations = products.stream()
                .map(product -> convertToProductValuationDTO(product, totalValue))
                .collect(Collectors.toList());

        // Sort based on parameters
        Comparator<ProductValuationDTO> comparator = getComparator(sortBy);
        if ("DESC".equalsIgnoreCase(sortDirection)) {
            comparator = comparator.reversed();
        }

        return valuations.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    /**
     * Get category-wise valuation breakdown
     */
    public List<CategoryValuationDTO> getCategoryValuation() {
        List<Product> allProducts = productRepository.findAll();
        BigDecimal totalValue = calculateTotalInventoryValue(allProducts);
        return generateCategoryValuations(allProducts, totalValue);
    }

    // Helper methods
    private BigDecimal calculateTotalInventoryValue(List<Product> products) {
        return products.stream()
                .map(p -> p.getUnitPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalValue(List<Product> products) {
        return products.stream()
                .map(p -> p.getUnitPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Long> getStockStatusCount(List<Product> products) {
        Map<String, Long> statusCount = new HashMap<>();
        statusCount.put("NORMAL", products.stream()
                .filter(p -> p.getQuantity() > p.getMinStockLevel())
                .count());
        statusCount.put("LOW", products.stream()
                .filter(p -> p.getQuantity() > 0 && p.getQuantity() <= p.getMinStockLevel())
                .count());
        statusCount.put("OUT_OF_STOCK", products.stream()
                .filter(p -> p.getQuantity() == 0)
                .count());
        return statusCount;
    }

    private Map<String, BigDecimal> getStockStatusValue(List<Product> products) {
        Map<String, BigDecimal> statusValue = new HashMap<>();

        statusValue.put("NORMAL", products.stream()
                .filter(p -> p.getQuantity() > p.getMinStockLevel())
                .map(p -> p.getUnitPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        statusValue.put("LOW", products.stream()
                .filter(p -> p.getQuantity() > 0 && p.getQuantity() <= p.getMinStockLevel())
                .map(p -> p.getUnitPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        statusValue.put("OUT_OF_STOCK", BigDecimal.ZERO);

        return statusValue;
    }

    private List<Product> getExpiringProducts(List<Product> products, int daysAhead) {
        LocalDate threshold = LocalDate.now().plusDays(daysAhead);
        return products.stream()
                .filter(p -> p.getExpiryDate() != null && p.getExpiryDate().isBefore(threshold))
                .collect(Collectors.toList());
    }

    private List<Product> getLowStockProducts(List<Product> products) {
        return products.stream()
                .filter(p -> p.getQuantity() > 0 && p.getQuantity() <= p.getMinStockLevel())
                .collect(Collectors.toList());
    }

    private List<CategoryValuationDTO> generateCategoryValuations(List<Product> products, BigDecimal totalValue) {
        Map<Long, List<Product>> productsByCategory = products.stream()
                .collect(Collectors.groupingBy(p -> p.getCategory().getId()));

        return productsByCategory.entrySet().stream()
                .map(entry -> {
                    Long categoryId = entry.getKey();
                    List<Product> categoryProducts = entry.getValue();

                    BigDecimal categoryValue = calculateTotalValue(categoryProducts);
                    Long totalQuantity = categoryProducts.stream()
                            .mapToLong(Product::getQuantity)
                            .sum();

                    return CategoryValuationDTO.builder()
                            .categoryId(categoryId)
                            .categoryName(categoryProducts.get(0).getCategory().getName())
                            .productCount((long) categoryProducts.size())
                            .totalQuantity(totalQuantity)
                            .totalValue(categoryValue)
                            .percentageOfTotal(calculatePercentage(categoryValue, totalValue))
                            .averageProductValue(categoryValue.divide(
                                    BigDecimal.valueOf(categoryProducts.size()), 2, RoundingMode.HALF_UP))
                            .build();
                })
                .sorted((a, b) -> b.getTotalValue().compareTo(a.getTotalValue()))
                .collect(Collectors.toList());
    }

    private List<ProductValuationDTO> getTopProductsByValue(List<Product> products, BigDecimal totalValue, int limit) {
        return products.stream()
                .map(product -> convertToProductValuationDTO(product, totalValue))
                .sorted((a, b) -> b.getTotalValue().compareTo(a.getTotalValue()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private ProductValuationDTO convertToProductValuationDTO(Product product, BigDecimal totalInventoryValue) {
        BigDecimal productValue = product.getUnitPrice().multiply(BigDecimal.valueOf(product.getQuantity()));
        String stockStatus = determineStockStatus(product);
        boolean isExpiringSoon = isProductExpiringSoon(product);

        return ProductValuationDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .code(product.getCode())
                .categoryName(product.getCategory().getName())
                .quantity(product.getQuantity())
                .unitPrice(product.getUnitPrice())
                .totalValue(productValue)
                .stockStatus(stockStatus)
                .expiryDate(product.getExpiryDate())
                .isExpiringSoon(isExpiringSoon)
                .percentageOfTotalValue(calculatePercentage(productValue, totalInventoryValue))
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
        return product.getExpiryDate().isBefore(LocalDate.now().plusDays(30));
    }

    private BigDecimal calculatePercentage(BigDecimal value, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(BigDecimal.valueOf(100))
                .divide(total, 2, RoundingMode.HALF_UP);
    }

    private Comparator<ProductValuationDTO> getComparator(String sortBy) {
        switch (sortBy.toLowerCase()) {
            case "name":
                return Comparator.comparing(ProductValuationDTO::getName);
            case "quantity":
                return Comparator.comparing(ProductValuationDTO::getQuantity);
            case "unitprice":
                return Comparator.comparing(ProductValuationDTO::getUnitPrice);
            case "totalvalue":
            default:
                return Comparator.comparing(ProductValuationDTO::getTotalValue);
        }
    }
}