package com.medicorex.controller;

import com.medicorex.dto.*;
import com.medicorex.service.procurement.QualityChecklistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quality-checklists")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class QualityChecklistController {

    private final QualityChecklistService checklistService;

    /**
     * Get all active checklist templates
     */
    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<List<QualityChecklistTemplateDTO>> getAllActiveTemplates() {
        return ResponseEntity.ok(checklistService.getAllActiveTemplates());
    }

    /**
     * Get default template
     */
    @GetMapping("/templates/default")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<QualityChecklistTemplateDTO> getDefaultTemplate() {
        return ResponseEntity.ok(checklistService.getDefaultTemplate());
    }

    /**
     * Get template by ID
     */
    @GetMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<QualityChecklistTemplateDTO> getTemplateById(@PathVariable Long id) {
        return ResponseEntity.ok(checklistService.getTemplateById(id));
    }

    /**
     * Submit quality checklist
     */
    @PostMapping("/submit")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<GoodsReceiptChecklistDTO> submitChecklist(
            @Valid @RequestBody ChecklistSubmissionDTO submissionDTO) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(checklistService.submitChecklist(submissionDTO));
    }

    /**
     * Get checklist for receipt
     */
    @GetMapping("/receipt/{receiptId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<GoodsReceiptChecklistDTO> getChecklistByReceiptId(@PathVariable Long receiptId) {
        return ResponseEntity.ok(checklistService.getChecklistByReceiptId(receiptId));
    }

    /**
     * Check if checklist exists for receipt
     */
    @GetMapping("/receipt/{receiptId}/exists")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<Map<String, Boolean>> checkChecklistExists(@PathVariable Long receiptId) {
        boolean exists = checklistService.checklistExistsForReceipt(receiptId);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}