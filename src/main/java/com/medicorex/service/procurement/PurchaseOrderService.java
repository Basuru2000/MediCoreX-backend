package com.medicorex.service.procurement;

import com.medicorex.dto.*;
import com.medicorex.entity.*;
import com.medicorex.entity.PurchaseOrder.POStatus;
import com.medicorex.exception.BusinessException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.*;
import com.medicorex.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Create new purchase order
     */
    public PurchaseOrderDTO createPurchaseOrder(PurchaseOrderCreateDTO createDTO) {
        log.info("Creating new purchase order for supplier: {}", createDTO.getSupplierId());

        // Validate supplier exists
        Supplier supplier = supplierRepository.findById(createDTO.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", createDTO.getSupplierId()));

        if (supplier.getStatus() != Supplier.SupplierStatus.ACTIVE) {
            throw new BusinessException("Cannot create PO for inactive supplier");
        }

        // Create PO
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber(generatePoNumber());
        po.setSupplier(supplier);
        po.setOrderDate(LocalDateTime.now());
        po.setExpectedDeliveryDate(createDTO.getExpectedDeliveryDate());
        po.setStatus(POStatus.DRAFT);
        po.setTaxAmount(createDTO.getTaxAmount() != null ? createDTO.getTaxAmount() : BigDecimal.ZERO);
        po.setDiscountAmount(createDTO.getDiscountAmount() != null ? createDTO.getDiscountAmount() : BigDecimal.ZERO);
        po.setNotes(createDTO.getNotes());
        po.setCreatedBy(getCurrentUser());

        // Add line items
        for (PurchaseOrderLineCreateDTO lineDTO : createDTO.getLines()) {
            PurchaseOrderLine line = createLineFromDTO(lineDTO, supplier);
            po.addLine(line);
        }

        // Calculate totals
        po.calculateTotals();

        PurchaseOrder savedPo = purchaseOrderRepository.save(po);
        log.info("Created purchase order: {}", savedPo.getPoNumber());

        return convertToDTO(savedPo);
    }

    /**
     * Get purchase order by ID
     */
    @Transactional(readOnly = true)
    public PurchaseOrderDTO getPurchaseOrderById(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order", "id", id));
        return convertToDTO(po);
    }

    /**
     * Get purchase order by PO number
     */
    @Transactional(readOnly = true)
    public PurchaseOrderDTO getPurchaseOrderByNumber(String poNumber) {
        PurchaseOrder po = purchaseOrderRepository.findByPoNumber(poNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order", "poNumber", poNumber));
        return convertToDTO(po);
    }

    /**
     * Get all purchase orders with pagination
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<PurchaseOrderDTO> getAllPurchaseOrders(Pageable pageable) {
        Page<PurchaseOrder> poPage = purchaseOrderRepository.findAll(pageable);
        return convertToPageResponse(poPage);
    }

    /**
     * Search purchase orders with filters
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<PurchaseOrderDTO> searchPurchaseOrders(
            POStatus status, Long supplierId, String search, Pageable pageable) {

        Page<PurchaseOrder> poPage = purchaseOrderRepository.searchPurchaseOrders(
                status, supplierId, search, pageable);
        return convertToPageResponse(poPage);
    }

    /**
     * Update purchase order (only DRAFT status)
     */
    public PurchaseOrderDTO updatePurchaseOrder(Long id, PurchaseOrderCreateDTO updateDTO) {
        log.info("Updating purchase order: {}", id);

        PurchaseOrder po = purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order", "id", id));

        // Only allow updates for DRAFT status
        if (po.getStatus() != POStatus.DRAFT) {
            throw new BusinessException("Cannot update PO in " + po.getStatus() + " status");
        }

        // Validate supplier
        if (!po.getSupplier().getId().equals(updateDTO.getSupplierId())) {
            Supplier newSupplier = supplierRepository.findById(updateDTO.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", updateDTO.getSupplierId()));
            po.setSupplier(newSupplier);
        }

        // Update basic fields
        po.setExpectedDeliveryDate(updateDTO.getExpectedDeliveryDate());
        po.setTaxAmount(updateDTO.getTaxAmount() != null ? updateDTO.getTaxAmount() : BigDecimal.ZERO);
        po.setDiscountAmount(updateDTO.getDiscountAmount() != null ? updateDTO.getDiscountAmount() : BigDecimal.ZERO);
        po.setNotes(updateDTO.getNotes());

        // Clear and re-add lines
        po.getLines().clear();
        for (PurchaseOrderLineCreateDTO lineDTO : updateDTO.getLines()) {
            PurchaseOrderLine line = createLineFromDTO(lineDTO, po.getSupplier());
            po.addLine(line);
        }

        // Recalculate totals
        po.calculateTotals();

        PurchaseOrder updatedPo = purchaseOrderRepository.save(po);
        log.info("Updated purchase order: {}", updatedPo.getPoNumber());

        return convertToDTO(updatedPo);
    }

    /**
     * Delete purchase order (only DRAFT status)
     */
    public void deletePurchaseOrder(Long id) {
        log.info("Deleting purchase order: {}", id);

        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order", "id", id));

        if (po.getStatus() != POStatus.DRAFT) {
            throw new BusinessException("Cannot delete PO in " + po.getStatus() + " status");
        }

        purchaseOrderRepository.delete(po);
        log.info("Deleted purchase order: {}", po.getPoNumber());
    }

    /**
     * Update PO status
     */
    public PurchaseOrderDTO updateStatus(Long id, POStatus newStatus) {
        log.info("Updating PO {} status to {}", id, newStatus);

        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order", "id", id));

        // Validate status transition
        validateStatusTransition(po.getStatus(), newStatus);

        po.setStatus(newStatus);
        PurchaseOrder updatedPo = purchaseOrderRepository.save(po);

        log.info("Updated PO {} status to {}", po.getPoNumber(), newStatus);
        return convertToDTO(updatedPo);
    }

    /**
     * Get purchase order summary statistics
     */
    @Transactional(readOnly = true)
    public PurchaseOrderSummaryDTO getPurchaseOrderSummary() {
        List<PurchaseOrder> allPos = purchaseOrderRepository.findAll();

        return PurchaseOrderSummaryDTO.builder()
                .totalOrders((long) allPos.size())
                .draftOrders(purchaseOrderRepository.countByStatus(POStatus.DRAFT))
                .approvedOrders(purchaseOrderRepository.countByStatus(POStatus.APPROVED))
                .sentOrders(purchaseOrderRepository.countByStatus(POStatus.SENT))
                .receivedOrders(purchaseOrderRepository.countByStatus(POStatus.RECEIVED))
                .cancelledOrders(purchaseOrderRepository.countByStatus(POStatus.CANCELLED))
                .totalValue(allPos.stream()
                        .map(PurchaseOrder::getTotalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .pendingValue(allPos.stream()
                        .filter(po -> po.getStatus() == POStatus.DRAFT || po.getStatus() == POStatus.APPROVED)
                        .map(PurchaseOrder::getTotalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .build();
    }

    /**
     * Approve purchase order
     */
    public PurchaseOrderDTO approvePurchaseOrder(Long id, PurchaseOrderApprovalDTO approvalDTO) {
        log.info("Approving purchase order ID: {}", id);

        PurchaseOrder po = purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order", "id", id));

        // Validate current status
        if (po.getStatus() != POStatus.DRAFT) {
            throw new BusinessException("Only DRAFT purchase orders can be approved. Current status: " + po.getStatus());
        }

        // Get current user (manager)
        User currentUser = getCurrentUser();

        // Validate user has manager role
        if (!currentUser.getRole().equals(User.UserRole.HOSPITAL_MANAGER)) {
            throw new BusinessException("Only Hospital Managers can approve purchase orders");
        }

        // Update approval details
        po.setApprovedBy(currentUser);
        po.setApprovedDate(LocalDateTime.now());
        po.setStatus(POStatus.APPROVED);
        po.setRejectionComments(null); // Clear any previous rejection comments

        PurchaseOrder approvedPo = purchaseOrderRepository.save(po);
        log.info("Purchase order {} approved by {}", po.getPoNumber(), currentUser.getUsername());

        // Send notification to creator
        sendApprovalNotification(approvedPo, true, approvalDTO.getComments());

        return convertToDTO(approvedPo);
    }

    /**
     * Reject purchase order
     */
    public PurchaseOrderDTO rejectPurchaseOrder(Long id, PurchaseOrderApprovalDTO rejectionDTO) {
        log.info("Rejecting purchase order ID: {}", id);

        PurchaseOrder po = purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order", "id", id));

        // Validate current status
        if (po.getStatus() != POStatus.DRAFT) {
            throw new BusinessException("Only DRAFT purchase orders can be rejected. Current status: " + po.getStatus());
        }

        // Get current user (manager)
        User currentUser = getCurrentUser();

        // Validate user has manager role
        if (!currentUser.getRole().equals(User.UserRole.HOSPITAL_MANAGER)) {
            throw new BusinessException("Only Hospital Managers can reject purchase orders");
        }

        // Validate rejection comments are provided
        if (rejectionDTO.getComments() == null || rejectionDTO.getComments().trim().isEmpty()) {
            throw new BusinessException("Rejection comments are required");
        }

        // Update rejection details
        po.setApprovedBy(currentUser);
        po.setApprovedDate(LocalDateTime.now());
        po.setRejectionComments(rejectionDTO.getComments());
        po.setStatus(POStatus.CANCELLED);

        PurchaseOrder rejectedPo = purchaseOrderRepository.save(po);
        log.info("Purchase order {} rejected by {}", po.getPoNumber(), currentUser.getUsername());

        // Send notification to creator
        sendApprovalNotification(rejectedPo, false, rejectionDTO.getComments());

        return convertToDTO(rejectedPo);
    }

    /**
     * Get pending approval purchase orders (DRAFT status)
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<PurchaseOrderDTO> getPendingApprovals(Pageable pageable) {
        Page<PurchaseOrder> poPage = purchaseOrderRepository.findByStatus(POStatus.DRAFT, pageable);
        return convertToPageResponse(poPage);
    }

    /**
     * Request approval for purchase order (sends notification to managers)
     */
    public void requestApproval(Long id) {
        log.info("Requesting approval for purchase order ID: {}", id);

        PurchaseOrder po = purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order", "id", id));

        if (po.getStatus() != POStatus.DRAFT) {
            throw new BusinessException("Only DRAFT purchase orders can request approval");
        }

        // Send notification to all hospital managers
        sendApprovalRequestNotification(po);

        log.info("Approval request sent for PO: {}", po.getPoNumber());
    }

    /**
     * Send approval request notification to managers
     */
    private void sendApprovalRequestNotification(PurchaseOrder po) {
        try {
            log.info("Attempting to send approval request notifications for PO: {}", po.getPoNumber());

            // Get all hospital managers
            List<User> managers = userRepository.findByRole(User.UserRole.HOSPITAL_MANAGER);

            if (managers.isEmpty()) {
                log.warn("No hospital managers found to notify for PO: {}", po.getPoNumber());
                return;
            }

            log.info("Found {} hospital managers to notify", managers.size());

            for (User manager : managers) {
                try {
                    Map<String, String> params = new HashMap<>();
                    params.put("poNumber", po.getPoNumber());
                    params.put("supplierName", po.getSupplier().getName());
                    params.put("amount", po.getTotalAmount().toString());
                    params.put("createdBy", po.getCreatedBy().getFullName());

                    Map<String, Object> actionData = new HashMap<>();
                    actionData.put("poId", po.getId());
                    actionData.put("type", "PO_APPROVAL_REQUEST");

                    // Create notification for each manager
                    notificationService.createNotificationFromTemplate(
                            manager.getId(),
                            "PO_APPROVAL_REQUEST",
                            params,
                            actionData
                    );

                    log.info("✅ Approval request sent to manager: {}", manager.getUsername());

                } catch (Exception e) {
                    log.error("❌ Failed to send notification to manager {}: {}",
                            manager.getUsername(), e.getMessage());
                }
            }

            log.info("Approval request notifications completed for PO: {}", po.getPoNumber());

        } catch (Exception e) {
            log.error("❌ Failed to send approval request notifications for PO {}: {}",
                    po.getPoNumber(), e.getMessage(), e);
        }
    }

    /**
     * Send approval/rejection notification to PO creator
     */
    private void sendApprovalNotification(PurchaseOrder po, boolean approved, String comments) {
        try {
            log.info("Attempting to send {} notification for PO: {} to user ID: {}",
                    approved ? "approval" : "rejection", po.getPoNumber(), po.getCreatedBy().getId());

            Map<String, String> params = new HashMap<>();
            params.put("poNumber", po.getPoNumber());
            params.put("approverName", po.getApprovedBy().getFullName());
            params.put("status", approved ? "APPROVED" : "REJECTED");

            if (comments != null && !comments.isEmpty()) {
                params.put("comments", comments);
            }

            Map<String, Object> actionData = new HashMap<>();
            actionData.put("poId", po.getId());
            actionData.put("type", approved ? "PO_APPROVED" : "PO_REJECTED");

            String templateCode = approved ? "PO_APPROVED" : "PO_REJECTED";

            // Use the createNotificationFromTemplate method
            notificationService.createNotificationFromTemplate(
                    po.getCreatedBy().getId(),
                    templateCode,
                    params,
                    actionData
            );

            log.info("✅ {} notification sent successfully for PO: {} to user: {}",
                    approved ? "Approval" : "Rejection",
                    po.getPoNumber(),
                    po.getCreatedBy().getUsername());

        } catch (Exception e) {
            log.error("❌ Failed to send {} notification for PO {}: {}",
                    approved ? "approval" : "rejection",
                    po.getPoNumber(),
                    e.getMessage(), e);
            // Don't throw exception - notification failure shouldn't break the approval process
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Generate unique PO number
     */
    private String generatePoNumber() {
        String prefix = "PO-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";

        // Get all PO numbers with this prefix
        List<PurchaseOrder> existingPos = purchaseOrderRepository.findAll().stream()
                .filter(po -> po.getPoNumber().startsWith(prefix))
                .collect(Collectors.toList());

        // Find max sequence
        int maxSequence = 0;
        for (PurchaseOrder po : existingPos) {
            try {
                String sequencePart = po.getPoNumber().substring(prefix.length());
                int sequence = Integer.parseInt(sequencePart);
                if (sequence > maxSequence) {
                    maxSequence = sequence;
                }
            } catch (Exception e) {
                // Skip invalid PO numbers
                log.warn("Invalid PO number format: {}", po.getPoNumber());
            }
        }

        int nextSequence = maxSequence + 1;
        return prefix + String.format("%04d", nextSequence);
    }

    /**
     * Create line item from DTO
     */
    private PurchaseOrderLine createLineFromDTO(PurchaseOrderLineCreateDTO lineDTO, Supplier supplier) {
        Product product = productRepository.findById(lineDTO.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", lineDTO.getProductId()));

        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setProduct(product);
        line.setProductName(product.getName());
        line.setProductCode(product.getCode());
        line.setQuantity(lineDTO.getQuantity());
        line.setUnitPrice(lineDTO.getUnitPrice());
        line.setDiscountPercentage(lineDTO.getDiscountPercentage() != null ? lineDTO.getDiscountPercentage() : BigDecimal.ZERO);
        line.setTaxPercentage(lineDTO.getTaxPercentage() != null ? lineDTO.getTaxPercentage() : BigDecimal.ZERO);
        line.setNotes(lineDTO.getNotes());

        // Try to link to supplier product for reference
        supplierProductRepository.findBySupplierIdAndProductId(supplier.getId(), product.getId())
                .ifPresent(line::setSupplierProduct);

        line.calculateLineTotal();

        return line;
    }

    /**
     * Validate status transition
     */
    private void validateStatusTransition(POStatus currentStatus, POStatus newStatus) {
        // Define valid transitions
        switch (currentStatus) {
            case DRAFT:
                if (newStatus != POStatus.APPROVED && newStatus != POStatus.CANCELLED) {
                    throw new BusinessException("DRAFT can only transition to APPROVED or CANCELLED");
                }
                break;
            case APPROVED:
                if (newStatus != POStatus.SENT && newStatus != POStatus.CANCELLED) {
                    throw new BusinessException("APPROVED can only transition to SENT or CANCELLED");
                }
                break;
            case SENT:
                if (newStatus != POStatus.RECEIVED && newStatus != POStatus.CANCELLED) {
                    throw new BusinessException("SENT can only transition to RECEIVED or CANCELLED");
                }
                break;
            case RECEIVED:
                throw new BusinessException("Cannot change status of RECEIVED PO");
            case CANCELLED:
                throw new BusinessException("Cannot change status of CANCELLED PO");
        }
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
    private PurchaseOrderDTO convertToDTO(PurchaseOrder po) {
        List<PurchaseOrderLineDTO> lineDTOs = po.getLines().stream()
                .map(this::convertLineToDTO)
                .collect(Collectors.toList());

        Integer totalItems = lineDTOs.stream()
                .mapToInt(PurchaseOrderLineDTO::getQuantity)
                .sum();

        Integer totalReceived = lineDTOs.stream()
                .mapToInt(PurchaseOrderLineDTO::getReceivedQuantity)
                .sum();

        return PurchaseOrderDTO.builder()
                .id(po.getId())
                .poNumber(po.getPoNumber())
                .supplierId(po.getSupplier().getId())
                .supplierName(po.getSupplier().getName())
                .supplierCode(po.getSupplier().getCode())
                .orderDate(po.getOrderDate())
                .expectedDeliveryDate(po.getExpectedDeliveryDate())
                .status(po.getStatus())
                .subtotal(po.getSubtotal())
                .taxAmount(po.getTaxAmount())
                .discountAmount(po.getDiscountAmount())
                .totalAmount(po.getTotalAmount())
                .notes(po.getNotes())
                .createdById(po.getCreatedBy().getId())
                .createdByName(po.getCreatedBy().getFullName())
                .approvedById(po.getApprovedBy() != null ? po.getApprovedBy().getId() : null)
                .approvedByName(po.getApprovedBy() != null ? po.getApprovedBy().getFullName() : null)
                .approvedDate(po.getApprovedDate())
                .rejectionComments(po.getRejectionComments())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .lines(lineDTOs)
                .totalItems(totalItems)
                .totalReceived(totalReceived)
                .build();
    }

    /**
     * Convert line entity to DTO
     */
    private PurchaseOrderLineDTO convertLineToDTO(PurchaseOrderLine line) {
        return PurchaseOrderLineDTO.builder()
                .id(line.getId())
                .poId(line.getPurchaseOrder() != null ? line.getPurchaseOrder().getId() : null)
                .productId(line.getProduct().getId())
                .productName(line.getProductName())
                .productCode(line.getProductCode())
                .quantity(line.getQuantity())
                .unitPrice(line.getUnitPrice())
                .discountPercentage(line.getDiscountPercentage())
                .taxPercentage(line.getTaxPercentage())
                .lineTotal(line.getLineTotal())
                .receivedQuantity(line.getReceivedQuantity())
                .notes(line.getNotes())
                .build();
    }

    /**
     * Convert page to DTO response
     */
    private PageResponseDTO<PurchaseOrderDTO> convertToPageResponse(Page<PurchaseOrder> poPage) {
        List<PurchaseOrderDTO> content = poPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageResponseDTO.<PurchaseOrderDTO>builder()
                .content(content)
                .page(poPage.getNumber())
                .size(poPage.getSize())
                .totalElements(poPage.getTotalElements())
                .totalPages(poPage.getTotalPages())
                .last(poPage.isLast())
                .build();
    }
}