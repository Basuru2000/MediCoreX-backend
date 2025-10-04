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
    @Transactional
    public GoodsReceiptDTO createGoodsReceipt(GoodsReceiptCreateDTO createDTO) {
        log.info("Creating goods receipt for PO: {}", createDTO.getPoId());

        // Validate and fetch PO
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(createDTO.getPoId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order", "id", createDTO.getPoId()));

        // Validate PO status - allow SENT or PARTIALLY_RECEIVED
        if (purchaseOrder.getStatus() != PurchaseOrder.POStatus.SENT &&
                purchaseOrder.getStatus() != PurchaseOrder.POStatus.PARTIALLY_RECEIVED) {
            throw new BusinessException("Cannot create receipt. Purchase order status must be SENT or PARTIALLY_RECEIVED. Current status: " + purchaseOrder.getStatus());
        }

        // Get current user
        User currentUser = getCurrentUser();

        // Create goods receipt
        GoodsReceipt receipt = new GoodsReceipt();
        receipt.setReceiptNumber(generateReceiptNumber());
        receipt.setPurchaseOrder(purchaseOrder);
        receipt.setPoNumber(purchaseOrder.getPoNumber());
        receipt.setReceiptDate(LocalDateTime.now());
        receipt.setReceivedBy(currentUser);
        receipt.setSupplier(purchaseOrder.getSupplier());
        receipt.setSupplierName(purchaseOrder.getSupplier().getName());
        receipt.setStatus(GoodsReceipt.ReceiptStatus.RECEIVED);
        receipt.setAcceptanceStatus(GoodsReceipt.AcceptanceStatus.PENDING_APPROVAL);
        receipt.setNotes(createDTO.getNotes());

        // Process receipt lines with partial receipt support
        int totalOrderedQuantity = 0;
        int totalReceivedQuantity = 0;

        for (GoodsReceiptLineCreateDTO lineDTO : createDTO.getLines()) {
            PurchaseOrderLine poLine = purchaseOrder.getLines().stream()
                    .filter(l -> l.getId().equals(lineDTO.getPoLineId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase Order Line", "id", lineDTO.getPoLineId()));

            // ENHANCED VALIDATION FOR PARTIAL RECEIPTS
            int currentReceived = poLine.getReceivedQuantity() != null ? poLine.getReceivedQuantity() : 0;
            int newReceivingQty = lineDTO.getReceivedQuantity();
            int totalAfterReceipt = currentReceived + newReceivingQty;

            // Validate: cannot receive more than ordered
            if (totalAfterReceipt > poLine.getQuantity()) {
                throw new BusinessException(String.format(
                        "Cannot receive %d units of %s. Ordered: %d, Already received: %d, Remaining: %d",
                        newReceivingQty, poLine.getProductName(), poLine.getQuantity(),
                        currentReceived, poLine.getRemainingQuantity()
                ));
            }

            // Validate: must receive at least 1 item
            if (newReceivingQty <= 0) {
                throw new BusinessException("Received quantity must be greater than 0 for " + poLine.getProductName());
            }

            totalOrderedQuantity += poLine.getQuantity();
            totalReceivedQuantity += newReceivingQty;

            // Create batch with proper error handling
            ProductBatch batch;
            try {
                batch = batchService.createOrUpdateBatch(
                        ProductBatchCreateDTO.builder()
                                .productId(poLine.getProduct().getId())
                                .batchNumber(lineDTO.getBatchNumber())
                                .quantity(newReceivingQty)
                                .expiryDate(lineDTO.getExpiryDate())
                                .manufactureDate(lineDTO.getManufactureDate())
                                .costPerUnit(poLine.getUnitPrice())
                                .supplierReference(purchaseOrder.getPoNumber())
                                .notes("Received via receipt: " + receipt.getReceiptNumber())
                                .build()
                );
            } catch (Exception e) {
                log.error("Failed to create/update batch for product {}: {}",
                        poLine.getProductName(), e.getMessage(), e);
                throw new BusinessException(
                        String.format("Failed to create batch '%s' for product '%s': %s. Please use a unique batch number for each product.",
                                lineDTO.getBatchNumber(),
                                poLine.getProductName(),
                                e.getMessage())
                );
            }

            // Create receipt line
            GoodsReceiptLine receiptLine = new GoodsReceiptLine();
            receiptLine.setPoLine(poLine);
            receiptLine.setProduct(poLine.getProduct());
            receiptLine.setProductName(poLine.getProductName());
            receiptLine.setProductCode(poLine.getProductCode());
            receiptLine.setOrderedQuantity(poLine.getQuantity());
            receiptLine.setReceivedQuantity(newReceivingQty);
            receiptLine.setBatch(batch);
            receiptLine.setBatchNumber(batch.getBatchNumber());
            receiptLine.setExpiryDate(batch.getExpiryDate());
            receiptLine.setManufactureDate(batch.getManufactureDate());
            receiptLine.setUnitCost(poLine.getUnitPrice());
            receiptLine.setLineTotal(poLine.getUnitPrice().multiply(BigDecimal.valueOf(newReceivingQty)));
            receiptLine.setQualityNotes(lineDTO.getQualityNotes());

            receipt.addLine(receiptLine);

            // UPDATE PO LINE WITH NEW QUANTITIES
            poLine.receiveQuantity(newReceivingQty);

            log.info("Line {}: Ordered={}, PreviouslyReceived={}, NowReceiving={}, NewTotal={}, Remaining={}",
                    poLine.getProductName(), poLine.getQuantity(), currentReceived,
                    newReceivingQty, poLine.getReceivedQuantity(), poLine.getRemainingQuantity());
        }

        // Save receipt
        GoodsReceipt savedReceipt = receiptRepository.save(receipt);

        // UPDATE PO STATUS BASED ON FULFILLMENT
        updatePurchaseOrderStatus(purchaseOrder);

        // Save updated PO
        purchaseOrderRepository.save(purchaseOrder);

        log.info("Goods receipt created successfully: {}. PO Status updated to: {}",
                savedReceipt.getReceiptNumber(), purchaseOrder.getStatus());

        // Create notification for partial receipt
        if (purchaseOrder.getStatus() == PurchaseOrder.POStatus.PARTIALLY_RECEIVED) {
            try {
                Map<String, String> params = new HashMap<>();
                params.put("receiptNumber", savedReceipt.getReceiptNumber());
                params.put("poNumber", purchaseOrder.getPoNumber());
                params.put("receivedQuantity", String.valueOf(totalReceivedQuantity));
                params.put("totalQuantity", String.valueOf(totalOrderedQuantity));
                params.put("percentage", String.valueOf(totalOrderedQuantity > 0 ? (totalReceivedQuantity * 100 / totalOrderedQuantity) : 0));

                Map<String, Object> actionData = new HashMap<>();
                actionData.put("receiptId", savedReceipt.getId());
                actionData.put("poId", purchaseOrder.getId());

                notificationService.createNotificationFromTemplate(
                        purchaseOrder.getCreatedBy().getId(),
                        "PO_PARTIALLY_RECEIVED",
                        params,
                        actionData
                );
            } catch (Exception e) {
                log.error("Failed to send partial receipt notification: {}", e.getMessage());
            }
        }

        return convertToDTO(savedReceipt);
    }

    /**
     * UPDATE PO STATUS BASED ON FULFILLMENT PERCENTAGE
     */
    private void updatePurchaseOrderStatus(PurchaseOrder purchaseOrder) {
        int totalOrdered = 0;
        int totalReceived = 0;
        int totalRemaining = 0;

        for (PurchaseOrderLine line : purchaseOrder.getLines()) {
            totalOrdered += line.getQuantity();
            totalReceived += line.getReceivedQuantity();
            totalRemaining += line.getRemainingQuantity();
        }

        PurchaseOrder.POStatus oldStatus = purchaseOrder.getStatus();
        PurchaseOrder.POStatus newStatus;

        if (totalRemaining == 0 && totalReceived == totalOrdered) {
            // Fully received
            newStatus = PurchaseOrder.POStatus.RECEIVED;
        } else if (totalReceived > 0 && totalRemaining > 0) {
            // Partially received
            newStatus = PurchaseOrder.POStatus.PARTIALLY_RECEIVED;
        } else {
            // No change - keep existing status
            return;
        }

        if (oldStatus != newStatus) {
            purchaseOrder.setStatus(newStatus);
            log.info("PO {} status changed: {} -> {}. Progress: {}/{} items ({}%)",
                    purchaseOrder.getPoNumber(), oldStatus, newStatus,
                    totalReceived, totalOrdered,
                    (totalOrdered > 0 ? (totalReceived * 100 / totalOrdered) : 0));
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
            int newReceivedQty = poLine.getReceivedQuantity() - line.getReceivedQuantity();
            int newRemainingQty = poLine.getQuantity() - newReceivedQty;

            poLine.setReceivedQuantity(newReceivedQty);
            poLine.setRemainingQuantity(newRemainingQty);
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
            transaction.setPerformedBy(user.getId());
            transaction.setTransactionDate(LocalDateTime.now());

            stockTransactionRepository.save(transaction);
            log.info("Stock transaction logged: {} units for product {}", quantity, product.getName());

        } catch (Exception e) {
            log.error("Failed to create stock transaction: {}", e.getMessage());
        }
    }

    /**
     * Update product average purchase price
     */
    private void updateProductAverageCost(Product product, Integer newQuantity, BigDecimal newCost) {
        try {
            if (newCost == null || newCost.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            Integer currentQty = product.getQuantity() - newQuantity;
            BigDecimal currentCost = product.getPurchasePrice() != null ?
                    product.getPurchasePrice() : BigDecimal.ZERO;

            if (currentQty > 0 && currentCost.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalOldValue = currentCost.multiply(BigDecimal.valueOf(currentQty));
                BigDecimal totalNewValue = newCost.multiply(BigDecimal.valueOf(newQuantity));
                BigDecimal totalValue = totalOldValue.add(totalNewValue);
                BigDecimal totalQuantity = BigDecimal.valueOf(currentQty + newQuantity);

                BigDecimal averageCost = totalValue.divide(totalQuantity, 2, RoundingMode.HALF_UP);
                product.setPurchasePrice(averageCost);
            } else {
                product.setPurchasePrice(newCost);
            }

            productRepository.save(product);
            log.info("Updated average cost for product {}: {}", product.getName(), product.getPurchasePrice());

        } catch (Exception e) {
            log.error("Failed to update product average cost: {}", e.getMessage());
        }
    }

    /**
     * Update supplier performance metrics
     */
    private void updateSupplierMetrics(GoodsReceipt receipt, int itemsReceived,
                                       int itemsAccepted, boolean onTime) {
        try {
            Long supplierId = receipt.getSupplier().getId();
            supplierMetricsService.updateQualityMetrics(supplierId, itemsReceived, itemsAccepted);
            supplierMetricsService.updateDeliveryMetrics(supplierId, onTime);
            log.info("Updated supplier metrics for supplier ID: {}", supplierId);
        } catch (Exception e) {
            log.error("Failed to update supplier metrics: {}", e.getMessage());
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