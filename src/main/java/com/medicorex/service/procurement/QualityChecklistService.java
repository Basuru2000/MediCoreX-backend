package com.medicorex.service.procurement;

import com.medicorex.dto.*;
import com.medicorex.entity.*;
import com.medicorex.exception.BusinessException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class QualityChecklistService {

    private final QualityChecklistTemplateRepository templateRepository;
    private final QualityCheckItemRepository checkItemRepository;
    private final GoodsReceiptChecklistRepository receiptChecklistRepository;
    private final ChecklistAnswerRepository answerRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final UserRepository userRepository;

    /**
     * Get all active templates
     */
    public List<QualityChecklistTemplateDTO> getAllActiveTemplates() {
        return templateRepository.findActiveTemplatesWithItems().stream()
                .map(this::convertTemplateToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get default template
     */
    public QualityChecklistTemplateDTO getDefaultTemplate() {
        QualityChecklistTemplate template = templateRepository.findByIsDefaultTrue()
                .orElseThrow(() -> new ResourceNotFoundException("Default quality checklist template not found"));
        return convertTemplateToDTO(template);
    }

    /**
     * Get template by ID with items
     */
    public QualityChecklistTemplateDTO getTemplateById(Long id) {
        QualityChecklistTemplate template = templateRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quality Checklist Template", "id", id));
        return convertTemplateToDTO(template);
    }

    /**
     * Submit checklist for goods receipt
     */
    public GoodsReceiptChecklistDTO submitChecklist(ChecklistSubmissionDTO submissionDTO) {
        log.info("Submitting quality checklist for receipt ID: {}", submissionDTO.getReceiptId());

        // Validate goods receipt exists
        GoodsReceipt receipt = goodsReceiptRepository.findById(submissionDTO.getReceiptId())
                .orElseThrow(() -> new ResourceNotFoundException("Goods Receipt", "id", submissionDTO.getReceiptId()));

        // Check if checklist already exists
        if (receiptChecklistRepository.existsByReceiptId(submissionDTO.getReceiptId())) {
            throw new BusinessException("Quality checklist already submitted for this receipt");
        }

        // Get template
        QualityChecklistTemplate template = templateRepository.findByIdWithItems(submissionDTO.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Quality Checklist Template", "id", submissionDTO.getTemplateId()));

        // Get current user
        User currentUser = getCurrentUser();

        // Create checklist
        GoodsReceiptChecklist checklist = new GoodsReceiptChecklist();
        checklist.setReceipt(receipt);
        checklist.setTemplate(template);
        checklist.setTemplateName(template.getName());
        checklist.setCompletedBy(currentUser);
        checklist.setInspectorNotes(submissionDTO.getInspectorNotes());

        // Process answers and determine overall result
        int totalChecks = 0;
        int passedChecks = 0;
        int failedChecks = 0;
        int mandatoryFailures = 0;

        for (ChecklistSubmissionDTO.AnswerSubmission answerSubmission : submissionDTO.getAnswers()) {
            QualityCheckItem checkItem = checkItemRepository.findById(answerSubmission.getCheckItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Quality Check Item", "id", answerSubmission.getCheckItemId()));

            ChecklistAnswer answer = new ChecklistAnswer();
            answer.setCheckItem(checkItem);
            answer.setCheckDescription(checkItem.getCheckDescription());
            answer.setAnswer(answerSubmission.getAnswer());
            answer.setRemarks(answerSubmission.getRemarks());

            // Determine compliance
            boolean isCompliant = determineCompliance(checkItem, answerSubmission.getAnswer());
            answer.setIsCompliant(isCompliant);

            checklist.addAnswer(answer);

            totalChecks++;
            if (isCompliant) {
                passedChecks++;
            } else {
                failedChecks++;
                if (checkItem.getIsMandatory()) {
                    mandatoryFailures++;
                }
            }
        }

        // Determine overall result
        String overallResult;
        if (mandatoryFailures > 0) {
            overallResult = "FAIL";
        } else if (failedChecks == 0) {
            overallResult = "PASS";
        } else {
            overallResult = "CONDITIONAL";
        }

        checklist.setOverallResult(overallResult);

        // Save checklist
        GoodsReceiptChecklist savedChecklist = receiptChecklistRepository.save(checklist);
        log.info("Quality checklist submitted successfully. Result: {}", overallResult);

        return convertChecklistToDTO(savedChecklist);
    }

    /**
     * Get checklist for receipt
     */
    public GoodsReceiptChecklistDTO getChecklistByReceiptId(Long receiptId) {
        GoodsReceiptChecklist checklist = receiptChecklistRepository.findByReceiptIdWithAnswers(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Quality checklist not found for receipt ID: " + receiptId));
        return convertChecklistToDTO(checklist);
    }

    /**
     * Check if checklist exists for receipt
     */
    public boolean checklistExistsForReceipt(Long receiptId) {
        return receiptChecklistRepository.existsByReceiptId(receiptId);
    }

    /**
     * Determine compliance based on check type and answer
     */
    private boolean determineCompliance(QualityCheckItem checkItem, String answer) {
        if (checkItem.getCheckType().equals("YES_NO")) {
            // For YES_NO type, compare with expected value
            if (checkItem.getExpectedValue() != null) {
                return answer.equalsIgnoreCase(checkItem.getExpectedValue());
            }
            // Default: YES is compliant
            return answer.equalsIgnoreCase("YES");
        } else if (checkItem.getCheckType().equals("PASS_FAIL")) {
            return answer.equalsIgnoreCase("PASS");
        }
        // For TEXT and NUMERIC, assume compliant unless explicitly marked as fail
        return !answer.equalsIgnoreCase("FAIL") && !answer.equalsIgnoreCase("NO");
    }

    /**
     * Convert template entity to DTO
     */
    private QualityChecklistTemplateDTO convertTemplateToDTO(QualityChecklistTemplate template) {
        List<QualityCheckItemDTO> itemDTOs = template.getItems().stream()
                .sorted((a, b) -> a.getItemOrder().compareTo(b.getItemOrder()))
                .map(this::convertItemToDTO)
                .collect(Collectors.toList());

        return QualityChecklistTemplateDTO.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .category(template.getCategory())
                .isActive(template.getIsActive())
                .isDefault(template.getIsDefault())
                .createdById(template.getCreatedBy() != null ? template.getCreatedBy().getId() : null)
                .createdByName(template.getCreatedBy() != null ? template.getCreatedBy().getFullName() : null)
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .items(itemDTOs)
                .itemCount(itemDTOs.size())
                .build();
    }

    /**
     * Convert check item entity to DTO
     */
    private QualityCheckItemDTO convertItemToDTO(QualityCheckItem item) {
        return QualityCheckItemDTO.builder()
                .id(item.getId())
                .templateId(item.getTemplate().getId())
                .itemOrder(item.getItemOrder())
                .checkDescription(item.getCheckDescription())
                .checkType(item.getCheckType())
                .isMandatory(item.getIsMandatory())
                .expectedValue(item.getExpectedValue())
                .notes(item.getNotes())
                .build();
    }

    /**
     * Convert checklist entity to DTO
     */
    private GoodsReceiptChecklistDTO convertChecklistToDTO(GoodsReceiptChecklist checklist) {
        List<ChecklistAnswerDTO> answerDTOs = checklist.getAnswers().stream()
                .map(this::convertAnswerToDTO)
                .collect(Collectors.toList());

        long passedChecks = answerDTOs.stream().filter(ChecklistAnswerDTO::getIsCompliant).count();
        long failedChecks = answerDTOs.stream().filter(a -> !a.getIsCompliant()).count();

        return GoodsReceiptChecklistDTO.builder()
                .id(checklist.getId())
                .receiptId(checklist.getReceipt().getId())
                .receiptNumber(checklist.getReceipt().getReceiptNumber())
                .templateId(checklist.getTemplate().getId())
                .templateName(checklist.getTemplateName())
                .completedById(checklist.getCompletedBy().getId())
                .completedByName(checklist.getCompletedBy().getFullName())
                .completedAt(checklist.getCompletedAt())
                .overallResult(checklist.getOverallResult())
                .inspectorNotes(checklist.getInspectorNotes())
                .answers(answerDTOs)
                .totalChecks(answerDTOs.size())
                .passedChecks((int) passedChecks)
                .failedChecks((int) failedChecks)
                .build();
    }

    /**
     * Convert answer entity to DTO
     */
    private ChecklistAnswerDTO convertAnswerToDTO(ChecklistAnswer answer) {
        return ChecklistAnswerDTO.builder()
                .id(answer.getId())
                .checklistId(answer.getChecklist().getId())
                .checkItemId(answer.getCheckItem().getId())
                .checkDescription(answer.getCheckDescription())
                .answer(answer.getAnswer())
                .isCompliant(answer.getIsCompliant())
                .remarks(answer.getRemarks())
                .isMandatory(answer.getCheckItem().getIsMandatory())
                .build();
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
}