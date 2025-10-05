package com.medicorex.service.procurement;

import com.medicorex.dto.*;
import com.medicorex.entity.*;
import com.medicorex.exception.BusinessException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.*;
import com.medicorex.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoPOGenerationService {

    private final AutoPOConfigurationRepository configRepository;
    private final ProductRepository productRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderService purchaseOrderService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Scheduled job to generate POs automatically
     * Runs based on cron expression in configuration (default: 2 AM daily)
     */
    @Scheduled(cron = "${auto.po.schedule:0 0 2 * * ?}")
    @Transactional
    public void runAutoPOGeneration() {
        log.info("Starting automated PO generation job...");

        AutoPOConfiguration config = getConfigurationEntity();  // ← FIX: Get entity, not DTO

        if (!config.getEnabled()) {
            log.info("Auto PO generation is disabled. Skipping...");
            return;
        }

        AutoPOGenerationResultDTO result = generatePurchaseOrders();

        // Update configuration with run status
        updateConfigurationRunStatus(config, result);

        // Send notifications
        if (config.getNotificationEnabled() && result.getPosGenerated() > 0) {
            sendBatchSummaryNotification(config, result);
        }

        log.info("Auto PO generation completed. Generated {} POs", result.getPosGenerated());
    }

    /**
     * Manual trigger for PO generation (from frontend)
     */
    @Transactional
    public AutoPOGenerationResultDTO generatePurchaseOrdersManually() {
        log.info("Manual trigger for auto PO generation");

        AutoPOConfiguration config = getConfigurationEntity();  // ← FIX: Get entity, not DTO

        if (!config.getEnabled()) {
            throw new BusinessException("Auto PO generation is currently disabled");
        }

        AutoPOGenerationResultDTO result = generatePurchaseOrders();

        // Update configuration
        updateConfigurationRunStatus(config, result);

        return result;
    }

    /**
     * Core PO generation logic
     */
    private AutoPOGenerationResultDTO generatePurchaseOrders() {
        long startTime = System.currentTimeMillis();

        AutoPOGenerationResultDTO result = AutoPOGenerationResultDTO.builder()
                .success(false)
                .productsEvaluated(0)
                .lowStockProducts(0)
                .posGenerated(0)
                .totalValue(BigDecimal.ZERO)
                .build();

        try {
            AutoPOConfiguration config = getConfigurationEntity();  // ← FIX: Get entity, not DTO

            // STEP 1: Find low-stock products
            List<Product> lowStockProducts = findLowStockProducts();
            result.setProductsEvaluated(lowStockProducts.size());
            result.setLowStockProducts(lowStockProducts.size());

            if (lowStockProducts.isEmpty()) {
                log.info("No low-stock products found");
                result.setSuccess(true);
                result.setExecutionTime(calculateExecutionTime(startTime));
                return result;
            }

            log.info("Found {} low-stock products", lowStockProducts.size());

            // STEP 2: Group products by preferred supplier
            Map<Supplier, List<ProductOrderInfo>> productsBySupplier =
                    groupProductsBySupplier(lowStockProducts, config);

            if (productsBySupplier.isEmpty()) {
                result.getWarnings().add("No preferred suppliers found for low-stock products");
                result.setSuccess(true);
                result.setExecutionTime(calculateExecutionTime(startTime));
                return result;
            }

            // STEP 3: Generate PO for each supplier
            for (Map.Entry<Supplier, List<ProductOrderInfo>> entry : productsBySupplier.entrySet()) {
                try {
                    Supplier supplier = entry.getKey();
                    List<ProductOrderInfo> productInfos = entry.getValue();

                    PurchaseOrderDTO createdPO = createAutoPurchaseOrder(
                            supplier,
                            productInfos,
                            config
                    );

                    result.getGeneratedPoNumbers().add(createdPO.getPoNumber());
                    result.setPosGenerated(result.getPosGenerated() + 1);
                    result.setTotalValue(result.getTotalValue().add(createdPO.getTotalAmount()));

                    // Send individual PO notification
                    if (config.getNotificationEnabled()) {
                        sendPOGeneratedNotification(createdPO, config);
                    }

                } catch (Exception e) {
                    log.error("Failed to create PO for supplier: {}", entry.getKey().getName(), e);
                    result.getErrors().add("Failed for supplier " + entry.getKey().getName() + ": " + e.getMessage());
                }
            }

            result.setSuccess(result.getErrors().isEmpty());
            result.setExecutionTime(calculateExecutionTime(startTime));

        } catch (Exception e) {
            log.error("Auto PO generation failed", e);
            result.setSuccess(false);
            result.getErrors().add("Generation failed: " + e.getMessage());
            result.setExecutionTime(calculateExecutionTime(startTime));
        }

        return result;
    }

    /**
     * Find products below minimum stock level
     */
    private List<Product> findLowStockProducts() {
        // Query products where current quantity <= minimum stock level
        return productRepository.findAll().stream()
                .filter(p -> p.getQuantity() != null && p.getMinStock() != null)
                .filter(p -> p.getQuantity() <= p.getMinStock())
                .collect(Collectors.toList());
    }

    /**
     * Group low-stock products by their preferred supplier
     */
    private Map<Supplier, List<ProductOrderInfo>> groupProductsBySupplier(
            List<Product> products,
            AutoPOConfiguration config) {

        Map<Supplier, List<ProductOrderInfo>> productsBySupplier = new HashMap<>();

        for (Product product : products) {
            // Find preferred supplier for this product
            // ← FIX: Use correct method name
            List<SupplierProduct> supplierProducts = supplierProductRepository
                    .findActiveByProductId(product.getId());

            Optional<SupplierProduct> preferredSupplier = supplierProducts.stream()
                    .filter(SupplierProduct::getIsPreferred)
                    .filter(SupplierProduct::getIsActive)
                    .filter(sp -> sp.getSupplier().getStatus() == Supplier.SupplierStatus.ACTIVE)
                    .findFirst();

            if (preferredSupplier.isEmpty() && config.getOnlyPreferredSuppliers()) {
                log.warn("No preferred supplier for product: {}. Skipping.", product.getName());
                continue;
            }

            // If no preferred supplier and config allows, use any active supplier
            SupplierProduct chosenSupplierProduct = preferredSupplier.orElseGet(() ->
                    supplierProducts.stream()
                            .filter(SupplierProduct::getIsActive)
                            .filter(sp -> sp.getSupplier().getStatus() == Supplier.SupplierStatus.ACTIVE)
                            .findFirst()
                            .orElse(null)
            );

            if (chosenSupplierProduct == null) {
                log.warn("No active supplier found for product: {}", product.getName());
                continue;
            }

            Supplier supplier = chosenSupplierProduct.getSupplier();

            // Calculate order quantity: (minStock * reorderMultiplier) - currentQuantity
            int targetQuantity = (int) (product.getMinStock() * config.getReorderMultiplier().doubleValue());
            int orderQuantity = Math.max(
                    targetQuantity - product.getQuantity(),
                    chosenSupplierProduct.getMinOrderQuantity()
            );

            ProductOrderInfo orderInfo = new ProductOrderInfo(
                    product,
                    chosenSupplierProduct,
                    orderQuantity
            );

            productsBySupplier.computeIfAbsent(supplier, k -> new ArrayList<>()).add(orderInfo);
        }

        return productsBySupplier;
    }

    /**
     * Create automated purchase order
     */
    private PurchaseOrderDTO createAutoPurchaseOrder(
            Supplier supplier,
            List<ProductOrderInfo> productInfos,
            AutoPOConfiguration config) {

        log.info("Creating auto PO for supplier: {} with {} products",
                supplier.getName(), productInfos.size());

        // Build PO lines
        List<PurchaseOrderLineCreateDTO> lines = new ArrayList<>();

        for (ProductOrderInfo info : productInfos) {
            // ← FIX: Remove supplierProductId (not in DTO)
            PurchaseOrderLineCreateDTO line = PurchaseOrderLineCreateDTO.builder()
                    .productId(info.getProduct().getId())
                    .quantity(info.getOrderQuantity())
                    .unitPrice(info.getSupplierProduct().getUnitPrice())
                    .discountPercentage(info.getSupplierProduct().getDiscountPercentage())
                    .taxPercentage(BigDecimal.ZERO)
                    .notes("Auto-generated: Low stock replenishment")
                    .build();

            lines.add(line);
        }

        // Calculate expected delivery date
        LocalDate expectedDelivery = LocalDate.now()
                .plusDays(config.getDaysUntilDelivery());

        // Create PO
        PurchaseOrderCreateDTO createDTO = PurchaseOrderCreateDTO.builder()
                .supplierId(supplier.getId())
                .expectedDeliveryDate(expectedDelivery)
                .lines(lines)
                .notes("Auto-generated PO for low-stock replenishment - System Generated on " +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .taxAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .build();

        // Create the PO using existing service
        PurchaseOrderDTO createdPO = purchaseOrderService.createPurchaseOrder(createDTO);

        // Mark as auto-generated
        PurchaseOrder po = purchaseOrderRepository.findById(createdPO.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", createdPO.getId()));
        po.setAutoGenerated(true);
        purchaseOrderRepository.save(po);

        // Auto-approve if configured
        if (config.getAutoApprove()) {
            try {
                // ← FIX: Use correct method signature with PurchaseOrderApprovalDTO
                PurchaseOrderApprovalDTO approvalDTO = PurchaseOrderApprovalDTO.builder()
                        .comments("Auto-approved by system")
                        .build();

                purchaseOrderService.approvePurchaseOrder(createdPO.getId(), approvalDTO);
                log.info("Auto-approved PO: {}", createdPO.getPoNumber());
            } catch (Exception e) {
                log.error("Failed to auto-approve PO: {}", createdPO.getPoNumber(), e);
            }
        }

        log.info("Created auto PO: {} for supplier: {}", createdPO.getPoNumber(), supplier.getName());
        return createdPO;
    }

    /**
     * Get or create system user for auto-generation
     */
    private User getSystemUser() {
        // Try to find system user
        Optional<User> systemUser = userRepository.findByUsername("system");

        if (systemUser.isPresent()) {
            return systemUser.get();
        }

        // If not found, use the first admin user
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.UserRole.HOSPITAL_MANAGER)
                .findFirst()
                .orElseThrow(() -> new BusinessException("No admin user found for auto PO generation"));
    }

    /**
     * Send notification for individual PO
     */
    private void sendPOGeneratedNotification(PurchaseOrderDTO po, AutoPOConfiguration config) {
        // ← FIX: Use createNotificationFromTemplate method
        Map<String, String> params = new HashMap<>();
        params.put("poNumber", po.getPoNumber());
        params.put("itemCount", String.valueOf(po.getLines().size()));
        params.put("totalAmount", po.getTotalAmount().toString());

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("poId", po.getId());

        String[] roles = config.getNotifyRoles().split(",");
        for (String role : roles) {
            List<User> users = userRepository.findByRole(User.UserRole.valueOf(role.trim()));
            for (User user : users) {
                try {
                    notificationService.createNotificationFromTemplate(
                            user.getId(),
                            "AUTO_PO_GENERATED",
                            params,
                            actionData
                    );
                } catch (Exception e) {
                    log.error("Failed to send notification to user {}: {}", user.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Send batch summary notification
     */
    private void sendBatchSummaryNotification(AutoPOConfiguration config, AutoPOGenerationResultDTO result) {
        // ← FIX: Use createNotificationFromTemplate method
        Map<String, String> params = new HashMap<>();
        params.put("poCount", String.valueOf(result.getPosGenerated()));
        params.put("productCount", String.valueOf(result.getLowStockProducts()));
        params.put("totalValue", result.getTotalValue().toString());

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("generatedPoNumbers", result.getGeneratedPoNumbers());

        String[] roles = config.getNotifyRoles().split(",");
        for (String role : roles) {
            List<User> users = userRepository.findByRole(User.UserRole.valueOf(role.trim()));
            for (User user : users) {
                try {
                    notificationService.createNotificationFromTemplate(
                            user.getId(),
                            "AUTO_PO_BATCH_SUMMARY",
                            params,
                            actionData
                    );
                } catch (Exception e) {
                    log.error("Failed to send batch summary to user {}: {}", user.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Update configuration with last run status
     */
    private void updateConfigurationRunStatus(AutoPOConfiguration config, AutoPOGenerationResultDTO result) {
        config.setLastRunAt(LocalDateTime.now());
        config.setLastRunStatus(result.getSuccess() ? "SUCCESS" :
                (result.getPosGenerated() > 0 ? "PARTIAL" : "FAILED"));
        config.setLastRunDetails(buildRunDetailsJson(result));
        configRepository.save(config);
    }

    /**
     * Build JSON summary of run details
     */
    private String buildRunDetailsJson(AutoPOGenerationResultDTO result) {
        return String.format(
                "{\"posGenerated\":%d,\"productsEvaluated\":%d,\"lowStockProducts\":%d,\"totalValue\":%s,\"errors\":%d,\"warnings\":%d}",
                result.getPosGenerated(),
                result.getProductsEvaluated(),
                result.getLowStockProducts(),
                result.getTotalValue(),
                result.getErrors().size(),
                result.getWarnings().size()
        );
    }

    /**
     * Calculate execution time
     */
    private String calculateExecutionTime(long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        return String.format("%d ms", duration);
    }

    /**
     * Get current configuration DTO (for API responses)
     */
    public AutoPOConfigDTO getConfiguration() {
        AutoPOConfiguration config = configRepository.findLatestConfiguration()
                .orElseThrow(() -> new ResourceNotFoundException("Auto PO Configuration not found"));
        return convertToDTO(config);
    }

    /**
     * Get configuration entity (for internal use)
     * ← FIX: New method to get entity directly
     */
    private AutoPOConfiguration getConfigurationEntity() {
        return configRepository.findLatestConfiguration()
                .orElseThrow(() -> new ResourceNotFoundException("Auto PO Configuration not found"));
    }

    /**
     * Update configuration
     */
    @Transactional
    public AutoPOConfigDTO updateConfiguration(AutoPOConfigDTO configDTO) {
        log.info("Updating auto PO configuration");

        AutoPOConfiguration config = configRepository.findLatestConfiguration()
                .orElseThrow(() -> new ResourceNotFoundException("Auto PO Configuration not found"));

        config.setEnabled(configDTO.getEnabled());
        config.setScheduleCron(configDTO.getScheduleCron());
        config.setReorderMultiplier(configDTO.getReorderMultiplier());
        config.setDaysUntilDelivery(configDTO.getDaysUntilDelivery());
        config.setMinPoValue(configDTO.getMinPoValue());
        config.setOnlyPreferredSuppliers(configDTO.getOnlyPreferredSuppliers());
        config.setAutoApprove(configDTO.getAutoApprove());
        config.setNotificationEnabled(configDTO.getNotificationEnabled());
        config.setNotifyRoles(configDTO.getNotifyRoles());

        AutoPOConfiguration saved = configRepository.save(config);
        log.info("Auto PO configuration updated successfully");

        return convertToDTO(saved);
    }

    /**
     * Convert entity to DTO
     */
    private AutoPOConfigDTO convertToDTO(AutoPOConfiguration config) {
        return AutoPOConfigDTO.builder()
                .id(config.getId())
                .enabled(config.getEnabled())
                .scheduleCron(config.getScheduleCron())
                .reorderMultiplier(config.getReorderMultiplier())
                .daysUntilDelivery(config.getDaysUntilDelivery())
                .minPoValue(config.getMinPoValue())
                .onlyPreferredSuppliers(config.getOnlyPreferredSuppliers())
                .autoApprove(config.getAutoApprove())
                .notificationEnabled(config.getNotificationEnabled())
                .notifyRoles(config.getNotifyRoles())
                .lastRunAt(config.getLastRunAt())
                .lastRunStatus(config.getLastRunStatus())
                .lastRunDetails(config.getLastRunDetails())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    /**
     * Helper class for product ordering information
     */
    private static class ProductOrderInfo {
        private final Product product;
        private final SupplierProduct supplierProduct;
        private final int orderQuantity;

        public ProductOrderInfo(Product product, SupplierProduct supplierProduct, int orderQuantity) {
            this.product = product;
            this.supplierProduct = supplierProduct;
            this.orderQuantity = orderQuantity;
        }

        public Product getProduct() { return product; }
        public SupplierProduct getSupplierProduct() { return supplierProduct; }
        public int getOrderQuantity() { return orderQuantity; }
    }
}