package com.medicorex.service.procurement;

import com.medicorex.dto.*;
import com.medicorex.entity.*;
import com.medicorex.entity.PurchaseOrder.POStatus;
import com.medicorex.exception.BusinessException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.*;
import com.medicorex.service.NotificationService;
import com.medicorex.service.ProductBatchService;
import com.medicorex.service.supplier.SupplierMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GoodsReceiptService {

    private final GoodsReceiptRepository receiptRepository;
    private final GoodsReceiptLineRepository receiptLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository poLineRepository;
    private final ProductRepository productRepository;
    private final ProductBatchRepository batchRepository;
    private final ProductBatchService batchService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final StockTransactionRepository stockTransactionRepository;
    private final SupplierMetricsService supplierMetricsService;

    /**
     * Create goods receipt from purchase order
     */
    public GoodsReceiptDTO createGoodsReceipt(GoodsReceiptCreateDTO createDTO) {
        log.info("Creating goods receipt for PO ID: {}", createDTO.getPoId());

        // Validate PO exists and is in SENT or APPROVED status
        PurchaseOrder po = purchaseOrderRepository.findByIdWithDetails(createDTO.getPoId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order", "id", createDTO.getPoId()));

        if (po.getStatus() != POStatus.SENT && po.getStatus() != POStatus.APPROVED) {
            throw new BusinessException("Can only receive goods for SENT or APPROVED purchase orders");
        }

        // Create receipt
        GoodsReceipt receipt = new GoodsReceipt();
        receipt.setReceiptNumber(generateReceiptNumber());
        receipt.setPurchaseOrder(po);
        receipt.setPoNumber(po.getPoNumber());
        receipt.setReceiptDate(LocalDateTime.now());
        receipt.setReceivedBy(getCurrentUser());
        receipt.setSupplier(po.getSupplier());
        receipt.setSupplierName(po.getSupplier().getName());
        receipt.setStatus(GoodsReceipt.ReceiptStatus.RECEIVED);
        receipt.setNotes(createDTO.getNotes());

        // Process each line item
        for (GoodsReceiptLineCreateDTO lineDTO : createDTO.getLines()) {
            GoodsReceiptLine line = processReceiptLine(lineDTO, receipt, po);
            receipt.addLine(line);
        }

        // Save receipt
        GoodsReceipt savedReceipt = receiptRepository.save(receipt);
        log.info("Created goods receipt: {}", savedReceipt.getReceiptNumber());

        // Check if PO is fully received and update status
        updatePurchaseOrderStatus(po);

        // Send notifications
        sendReceiptNotifications(savedReceipt, po);

        return convertToDTO(savedReceipt);
    }

    /**
     * Process individual receipt line
     */
    private GoodsReceiptLine processReceiptLine(
            GoodsReceiptLineCreateDTO lineDTO,
            GoodsReceipt receipt,
            PurchaseOrder po) {

        // Get PO line
        PurchaseOrderLine poLine = poLineRepository.findById(lineDTO.getPoLineId())
                .orElseThrow(() -> new ResourceNotFoundException("PO Line", "id", lineDTO.getPoLineId()));

        // Validate PO line belongs to this PO
        if (!poLine.getPurchaseOrder().getId().equals(po.getId())) {
            throw new BusinessException("PO Line does not belong to this Purchase Order");
        }

        // Validate received quantity
        int alreadyReceived = poLine.getReceivedQuantity();
        int ordered = poLine.getQuantity();
        int receiving = lineDTO.getReceivedQuantity();

        if (alreadyReceived + receiving > ordered) {
            throw new BusinessException(
                    String.format("Cannot receive %d units. Ordered: %d, Already received: %d",
                            receiving, ordered, alreadyReceived)
            );
        }

        // Update PO line received quantity
        poLine.setReceivedQuantity(alreadyReceived + receiving);
        poLineRepository.save(poLine);

        // Create receipt line
        GoodsReceiptLine line = new GoodsReceiptLine();
        line.setReceipt(receipt);
        line.setPoLine(poLine);
        line.setProduct(poLine.getProduct());
        line.setProductName(poLine.getProductName());
        line.setProductCode(poLine.getProductCode());
        line.setOrderedQuantity(ordered);
        line.setReceivedQuantity(receiving);
        line.setBatchNumber(lineDTO.getBatchNumber());
        line.setExpiryDate(lineDTO.getExpiryDate());
        line.setManufactureDate(lineDTO.getManufactureDate());
        line.setUnitCost(poLine.getUnitPrice());
        line.setLineTotal(poLine.getUnitPrice().multiply(BigDecimal.valueOf(receiving)));
        line.setQualityNotes(lineDTO.getQualityNotes());

        return line;
    }

    /**
     * Update PO status if fully received
     */
    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        // Check if all lines are fully received
        boolean fullyReceived = po.getLines().stream()
                .allMatch(line -> line.getReceivedQuantity() >= line.getQuantity());

        if (fullyReceived && po.getStatus() != POStatus.RECEIVED) {
            po.setStatus(POStatus.RECEIVED);
            purchaseOrderRepository.save(po);
            log.info("PO {} marked as RECEIVED", po.getPoNumber());

            // Send notification for fully received PO
            sendFullyReceivedNotification(po);
        }
    }

    /**
     * Send receipt notifications
     */
    private void sendReceiptNotifications(GoodsReceipt receipt, PurchaseOrder po) {
        try {
            // Calculate total quantity
            int totalQuantity = receipt.getLines().stream()
                    .mapToInt(GoodsReceiptLine::getReceivedQuantity)
                    .sum();

            Map<String, String> params = new HashMap<>();
            params.put("receiptNumber", receipt.getReceiptNumber());
            params.put("poNumber", po.getPoNumber());
            params.put("supplierName", receipt.getSupplierName());
            params.put("totalQuantity", String.valueOf(totalQuantity));

            Map<String, Object> actionData = new HashMap<>();
            actionData.put("receiptId", receipt.getId());
            actionData.put("poId", po.getId());

            notificationService.createNotificationFromTemplate(
                    po.getCreatedBy().getId(),
                    "GOODS_RECEIVED",
                    params,
                    actionData
            );

            // Notify PO approver if different from creator
            if (po.getApprovedBy() != null &&
                    !po.getApprovedBy().getId().equals(po.getCreatedBy().getId())) {
                notificationService.createNotificationFromTemplate(
                        po.getApprovedBy().getId(),
                        "GOODS_RECEIVED",
                        params,
                        actionData
                );
            }

            log.info("Sent goods receipt notifications for {}", receipt.getReceiptNumber());
        } catch (Exception e) {
            log.error("Failed to send receipt notifications: {}", e.getMessage());
        }
    }

    /**
     * Send fully received notification
     */
    private void sendFullyReceivedNotification(PurchaseOrder po) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("poNumber", po.getPoNumber());

            Map<String, Object> actionData = new HashMap<>();
            actionData.put("poId", po.getId());

            notificationService.createNotificationFromTemplate(
                    po.getCreatedBy().getId(),
                    "PO_FULLY_RECEIVED",
                    params,
                    actionData
            );

            if (po.getApprovedBy() != null &&
                    !po.getApprovedBy().getId().equals(po.getCreatedBy().getId())) {
                notificationService.createNotificationFromTemplate(
                        po.getApprovedBy().getId(),
                        "PO_FULLY_RECEIVED",
                        params,
                        actionData
                );
            }
        } catch (Exception e) {
            log.error("Failed to send fully received notification: {}", e.getMessage());
        }
    }

    /**
     * Preview inventory impact before acceptance
     */
    @Transactional(readOnly = true)
    public List<StockLevelComparisonDTO> previewInventoryImpact(Long receiptId) {
        log.info("Previewing inventory impact for receipt ID: {}", receiptId);

        GoodsReceipt receipt = receiptRepository.findByIdWithDetails(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Goods Receipt", "id", receiptId));

        List<StockLevelComparisonDTO> comparisons = new ArrayList<>();

        for (GoodsReceiptLine line : receipt.getLines()) {
            Product product = line.getProduct();

            // Check if batch exists
            Optional<ProductBatch> existingBatch = batchRepository
                    .findByProductIdAndBatchNumber(product.getId(), line.getBatchNumber());

            // Calculate stock status using minStockLevel
            Integer projectedStock = product.getQuantity() + line.getReceivedQuantity();
            String stockStatus = "ADEQUATE";
            if (product.getMinStockLevel() != null) {
                if (projectedStock <= product.getMinStockLevel()) {
                    stockStatus = "LOW";
                } else if (projectedStock > product.getMinStockLevel() * 3) {
                    stockStatus = "OVERSTOCKED";
                }
            }

            StockLevelComparisonDTO comparison = StockLevelComparisonDTO.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .productCode(product.getCode())
                    .currentStock(product.getQuantity())
                    .incomingQuantity(line.getReceivedQuantity())
                    .projectedStock(projectedStock)
                    .batchNumber(line.getBatchNumber())
                    .willCreateNewBatch(!existingBatch.isPresent())
                    .existingBatchId(existingBatch.map(b -> b.getId().toString()).orElse(null))
                    .unitCost(line.getUnitCost())
                    .totalValue(line.getLineTotal())
                    .reorderLevel(product.getMinStockLevel())
                    .minStockLevel(product.getMinStockLevel())
                    .stockStatus(stockStatus)
                    .build();

            comparisons.add(comparison);
        }

        return comparisons;
    }

    /**
     * Accept goods receipt - Add to inventory with full integration
     */
    public GoodsReceiptDTO acceptGoodsReceipt(Long receiptId, GoodsReceiptAcceptDTO acceptDTO) {
        log.info("Accepting goods receipt ID: {}", receiptId);

        GoodsReceipt receipt = receiptRepository.findByIdWithDetails(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Goods Receipt", "id", receiptId));

        // Validate current status
        if (receipt.getAcceptanceStatus() != GoodsReceipt.AcceptanceStatus.PENDING_APPROVAL) {
            throw new BusinessException("Only pending receipts can be accepted. Current status: " +
                    receipt.getAcceptanceStatus());
        }

        User currentUser = getCurrentUser();

        // Update acceptance status
        receipt.setAcceptanceStatus(GoodsReceipt.AcceptanceStatus.ACCEPTED);
        receipt.setQualityCheckedBy(currentUser);
        receipt.setQualityCheckedAt(LocalDateTime.now());

        // Track metrics for supplier performance update
        int totalItemsReceived = 0;
        int totalItemsAccepted = 0;
        BigDecimal totalCost = BigDecimal.ZERO;

        // Now process each line and update inventory
        for (GoodsReceiptLine line : receipt.getLines()) {
            try {
                // STEP 1: Create or update product batch
                ProductBatch batch = batchService.createOrUpdateBatch(
                        ProductBatchCreateDTO.builder()
                                .productId(line.getProduct().getId())
                                .batchNumber(line.getBatchNumber())
                                .quantity(line.getReceivedQuantity())
                                .expiryDate(line.getExpiryDate())
                                .manufactureDate(line.getManufactureDate())
                                .costPerUnit(line.getUnitCost())
                                .supplierReference(receipt.getPoNumber())
                                .notes("Added from accepted receipt: " + receipt.getReceiptNumber() +
                                        (acceptDTO.getQualityNotes() != null ?
                                                " | Quality: " + acceptDTO.getQualityNotes() : ""))
                                .build()
                );

                // Link batch to receipt line
                line.setBatch(batch);

                // STEP 2: Create stock transaction for audit trail
                createStockTransaction(
                        line.getProduct(),
                        line.getReceivedQuantity(),
                        "GOODS_RECEIPT",
                        "Goods received from PO: " + receipt.getPoNumber() +
                                " | Receipt: " + receipt.getReceiptNumber() +
                                " | Batch: " + line.getBatchNumber(),
                        currentUser
                );

                // STEP 3: Update product average purchase price
                updateProductAverageCost(
                        line.getProduct(),
                        line.getReceivedQuantity(),
                        line.getUnitCost()
                );

                // Track for supplier metrics
                totalItemsReceived += line.getReceivedQuantity();
                totalItemsAccepted += line.getReceivedQuantity();
                totalCost = totalCost.add(line.getLineTotal());

                log.info("Inventory updated for product: {} | Batch: {} | Quantity: {}",
                        line.getProductName(), batch.getBatchNumber(), line.getReceivedQuantity());

            } catch (Exception e) {
                log.error("Failed to update inventory for line: {}", line.getId(), e);
                throw new BusinessException("Failed to update inventory for " + line.getProductName() +
                        ": " + e.getMessage());
            }
        }

        GoodsReceipt savedReceipt = receiptRepository.save(receipt);
        log.info("Goods receipt {} accepted and inventory updated", receipt.getReceiptNumber());

        // STEP 4: Update supplier metrics
        updateSupplierMetrics(receipt, totalItemsReceived, totalItemsAccepted, true);

        // Check if PO is fully received
        updatePurchaseOrderStatus(receipt.getPurchaseOrder());

        // Send acceptance notification
        sendAcceptanceNotification(savedReceipt);

        return convertToDTO(savedReceipt);
    }

    /**
     * Reject goods receipt - Do not add to inventory
     */
    public GoodsReceiptDTO rejectGoodsReceipt(Long receiptId, GoodsReceiptRejectDTO rejectDTO) {
        log.info("Rejecting goods receipt ID: {}", receiptId);

        GoodsReceipt receipt = receiptRepository.findByIdWithDetails(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Goods Receipt", "id", receiptId));

        // Validate current status
        if (receipt.getAcceptanceStatus() != GoodsReceipt.AcceptanceStatus.PENDING_APPROVAL) {
            throw new BusinessException("Only pending receipts can be rejected. Current status: " +
                    receipt.getAcceptanceStatus());
        }

        User currentUser = getCurrentUser();

        // Update rejection status
        receipt.setAcceptanceStatus(GoodsReceipt.AcceptanceStatus.REJECTED);
        receipt.setRejectionReason(rejectDTO.getRejectionReason());
        receipt.setQualityCheckedBy(currentUser);
        receipt.setQualityCheckedAt(LocalDateTime.now());

        GoodsReceipt savedReceipt = receiptRepository.save(receipt);
        log.info("Goods receipt {} rejected. Reason: {}",
                receipt.getReceiptNumber(), rejectDTO.getRejectionReason());

        // Update PO line received quantities (reduce back)
        for (GoodsReceiptLine line : receipt.getLines()) {
            PurchaseOrderLine poLine = line.getPoLine();
            poLine.setReceivedQuantity(poLine.getReceivedQuantity() - line.getReceivedQuantity());
            poLineRepository.save(poLine);
        }

        // Update supplier metrics for rejection
        int totalItemsReceived = receipt.getLines().stream()
                .mapToInt(GoodsReceiptLine::getReceivedQuantity)
                .sum();
        updateSupplierMetrics(receipt, totalItemsReceived, 0, false);

        // Send rejection notification
        sendRejectionNotification(savedReceipt, rejectDTO.getNotifySupplier());

        return convertToDTO(savedReceipt);
    }

    /**
     * Create stock transaction for audit trail
     */
    private void createStockTransaction(Product product, Integer quantity,
                                        String type, String reason, User user) {
        try {
            StockTransaction transaction = new StockTransaction();
            transaction.setProduct(product);
            transaction.setType(type);
            transaction.setTransactionType(TransactionType.PURCHASE);
            transaction.setQuantity(quantity);
            transaction.setBalanceAfter(product.getQuantity());
            transaction.setReason(reason);
            transaction.setPerformedBy(user.getId()); // Pass user ID, not User object
            transaction.setTransactionDate(LocalDateTime.now());

            stockTransactionRepository.save(transaction);
            log.info("Stock transaction logged: {} units for product {}", quantity, product.getName());

        } catch (Exception e) {
            log.error("Failed to create stock transaction: {}", e.getMessage());
            // Don't fail the entire operation if transaction logging fails
        }
    }

    /**
     * Update product average purchase price
     */
    private void updateProductAverageCost(Product product, Integer newQuantity, BigDecimal newCost) {
        try {
            if (newCost == null || newCost.compareTo(BigDecimal.ZERO) <= 0) {
                return; // Skip if cost is not provided or invalid
            }

            // Calculate weighted average
            Integer currentQty = product.getQuantity() - newQuantity; // Stock before this receipt
            BigDecimal currentCost = product.getPurchasePrice() != null ?
                    product.getPurchasePrice() : BigDecimal.ZERO;

            if (currentQty > 0 && currentCost.compareTo(BigDecimal.ZERO) > 0) {
                // Weighted average formula
                BigDecimal totalOldValue = currentCost.multiply(BigDecimal.valueOf(currentQty));
                BigDecimal totalNewValue = newCost.multiply(BigDecimal.valueOf(newQuantity));
                BigDecimal totalValue = totalOldValue.add(totalNewValue);
                BigDecimal totalQuantity = BigDecimal.valueOf(currentQty + newQuantity);

                // Use RoundingMode.HALF_UP instead of deprecated constant
                BigDecimal averageCost = totalValue.divide(totalQuantity, 2, RoundingMode.HALF_UP);
                product.setPurchasePrice(averageCost);
            } else {
                // First purchase or no existing cost
                product.setPurchasePrice(newCost);
            }

            productRepository.save(product);
            log.info("Updated average cost for product {}: {}", product.getName(), product.getPurchasePrice());

        } catch (Exception e) {
            log.error("Failed to update product average cost: {}", e.getMessage());
            // Don't fail the entire operation
        }
    }

    /**
     * Update supplier performance metrics
     */
    private void updateSupplierMetrics(GoodsReceipt receipt, int itemsReceived,
                                       int itemsAccepted, boolean onTime) {
        try {
            Long supplierId = receipt.getSupplier().getId();

            // Update quality metrics
            supplierMetricsService.updateQualityMetrics(supplierId, itemsReceived, itemsAccepted);

            // Update delivery metrics
            supplierMetricsService.updateDeliveryMetrics(supplierId, onTime);

            log.info("Updated supplier metrics for supplier ID: {}", supplierId);

        } catch (Exception e) {
            log.error("Failed to update supplier metrics: {}", e.getMessage());
            // Don't fail the entire operation
        }
    }

    /**
     * Send acceptance notification
     */
    private void sendAcceptanceNotification(GoodsReceipt receipt) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("receiptNumber", receipt.getReceiptNumber());
            params.put("poNumber", receipt.getPoNumber());
            params.put("supplierName", receipt.getSupplierName());
            params.put("totalQuantity", String.valueOf(
                    receipt.getLines().stream()
                            .mapToInt(GoodsReceiptLine::getReceivedQuantity)
                            .sum()
            ));

            notificationService.createNotificationFromTemplate(
                    receipt.getReceivedBy().getId(),
                    "GOODS_RECEIPT_ACCEPTED",
                    params,
                    null
            );

            log.info("Acceptance notification sent for receipt: {}", receipt.getReceiptNumber());

        } catch (Exception e) {
            log.error("Failed to send acceptance notification: {}", e.getMessage());
        }
    }

    /**
     * Send rejection notification
     */
    private void sendRejectionNotification(GoodsReceipt receipt, Boolean notifySupplier) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("receiptNumber", receipt.getReceiptNumber());
            params.put("poNumber", receipt.getPoNumber());
            params.put("supplierName", receipt.getSupplierName());
            params.put("rejectionReason", receipt.getRejectionReason());

            notificationService.createNotificationFromTemplate(
                    receipt.getReceivedBy().getId(),
                    "GOODS_RECEIPT_REJECTED",
                    params,
                    null
            );

            // TODO: In future, notify supplier if requested
            if (notifySupplier != null && notifySupplier) {
                log.info("Supplier notification requested for rejected receipt: {}",
                        receipt.getReceiptNumber());
            }

            log.info("Rejection notification sent for receipt: {}", receipt.getReceiptNumber());

        } catch (Exception e) {
            log.error("Failed to send rejection notification: {}", e.getMessage());
        }
    }

    /**
     * Get goods receipt by ID
     */
    @Transactional(readOnly = true)
    public GoodsReceiptDTO getReceiptById(Long id) {
        GoodsReceipt receipt = receiptRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goods Receipt", "id", id));
        return convertToDTO(receipt);
    }

    /**
     * Get goods receipt by receipt number
     */
    @Transactional(readOnly = true)
    public GoodsReceiptDTO getReceiptByNumber(String receiptNumber) {
        GoodsReceipt receipt = receiptRepository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Goods Receipt", "receiptNumber", receiptNumber));
        return convertToDTO(receipt);
    }

    /**
     * Get all receipts for a PO
     */
    @Transactional(readOnly = true)
    public List<GoodsReceiptDTO> getReceiptsByPurchaseOrder(Long poId) {
        List<GoodsReceipt> receipts = receiptRepository.findByPurchaseOrderId(poId);
        return receipts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all goods receipts with pagination
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<GoodsReceiptDTO> getAllReceipts(Pageable pageable) {
        Page<GoodsReceipt> receiptPage = receiptRepository.findAll(pageable);
        return convertToPageResponse(receiptPage);
    }

    /**
     * Get pending approval receipts
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<GoodsReceiptDTO> getPendingApprovals(Pageable pageable) {
        Page<GoodsReceipt> receiptPage = receiptRepository.findPendingApprovals(pageable);

        List<GoodsReceiptDTO> dtos = receiptPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageResponseDTO.<GoodsReceiptDTO>builder()
                .content(dtos)
                .page(receiptPage.getNumber())
                .size(receiptPage.getSize())
                .totalElements(receiptPage.getTotalElements())
                .totalPages(receiptPage.getTotalPages())
                .last(receiptPage.isLast())
                .build();
    }

    /**
     * Search goods receipts
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<GoodsReceiptDTO> searchReceipts(
            Long supplierId, String search,
            LocalDateTime startDate, LocalDateTime endDate,
            Pageable pageable) {

        Page<GoodsReceipt> receiptPage = receiptRepository.searchReceipts(
                supplierId, search, startDate, endDate, pageable);
        return convertToPageResponse(receiptPage);
    }

    /**
     * Generate unique receipt number
     */
    private String generateReceiptNumber() {
        String prefix = "GR";
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(8);

        String receiptNumber;
        int attempt = 0;
        do {
            receiptNumber = String.format("%s-%s-%s", prefix, datePart, timestamp);
            if (attempt > 0) {
                receiptNumber += "-" + attempt;
            }
            attempt++;
        } while (receiptRepository.existsByReceiptNumber(receiptNumber));

        return receiptNumber;
    }

    /**
     * Get current user
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    /**
     * Convert entity to DTO
     */
    private GoodsReceiptDTO convertToDTO(GoodsReceipt receipt) {
        List<GoodsReceiptLineDTO> lineDTOs = receipt.getLines().stream()
                .map(this::convertLineToDTO)
                .collect(Collectors.toList());

        Integer totalQuantity = lineDTOs.stream()
                .mapToInt(GoodsReceiptLineDTO::getReceivedQuantity)
                .sum();

        return GoodsReceiptDTO.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .poId(receipt.getPurchaseOrder().getId())
                .poNumber(receipt.getPoNumber())
                .receiptDate(receipt.getReceiptDate())
                .receivedById(receipt.getReceivedBy().getId())
                .receivedByName(receipt.getReceivedBy().getFullName())
                .supplierId(receipt.getSupplier().getId())
                .supplierName(receipt.getSupplierName())
                .status(receipt.getStatus())
                .acceptanceStatus(receipt.getAcceptanceStatus())
                .rejectionReason(receipt.getRejectionReason())
                .qualityCheckedById(receipt.getQualityCheckedBy() != null ?
                        receipt.getQualityCheckedBy().getId() : null)
                .qualityCheckedByName(receipt.getQualityCheckedBy() != null ?
                        receipt.getQualityCheckedBy().getFullName() : null)
                .qualityCheckedAt(receipt.getQualityCheckedAt())
                .notes(receipt.getNotes())
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .lines(lineDTOs)
                .totalQuantity(totalQuantity)
                .build();
    }

    /**
     * Convert line entity to DTO
     */
    private GoodsReceiptLineDTO convertLineToDTO(GoodsReceiptLine line) {
        return GoodsReceiptLineDTO.builder()
                .id(line.getId())
                .receiptId(line.getReceipt() != null ? line.getReceipt().getId() : null)
                .poLineId(line.getPoLine().getId())
                .productId(line.getProduct().getId())
                .productName(line.getProductName())
                .productCode(line.getProductCode())
                .orderedQuantity(line.getOrderedQuantity())
                .receivedQuantity(line.getReceivedQuantity())
                .batchId(line.getBatch() != null ? line.getBatch().getId() : null)
                .batchNumber(line.getBatchNumber())
                .expiryDate(line.getExpiryDate())
                .manufactureDate(line.getManufactureDate())
                .unitCost(line.getUnitCost())
                .lineTotal(line.getLineTotal())
                .qualityNotes(line.getQualityNotes())
                .build();
    }

    /**
     * Convert page to DTO response
     */
    private PageResponseDTO<GoodsReceiptDTO> convertToPageResponse(Page<GoodsReceipt> receiptPage) {
        List<GoodsReceiptDTO> content = receiptPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageResponseDTO.<GoodsReceiptDTO>builder()
                .content(content)
                .page(receiptPage.getNumber())
                .size(receiptPage.getSize())
                .totalElements(receiptPage.getTotalElements())
                .totalPages(receiptPage.getTotalPages())
                .last(receiptPage.isLast())
                .build();
    }
}