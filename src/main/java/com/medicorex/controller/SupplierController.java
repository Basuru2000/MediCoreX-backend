package com.medicorex.controller;

import com.medicorex.dto.*;
import com.medicorex.entity.SupplierDocument;
import com.medicorex.exception.BusinessException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.service.supplier.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<PageResponseDTO<SupplierDTO>> getAllSuppliers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {

        Sort.Direction direction = sortDirection.equalsIgnoreCase("DESC") ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        return ResponseEntity.ok(supplierService.getAllSuppliers(pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<PageResponseDTO<SupplierDTO>> searchSuppliers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(supplierService.searchSuppliers(query, pageable));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<List<SupplierDTO>> getActiveSuppliers() {
        return ResponseEntity.ok(supplierService.getActiveSuppliers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<SupplierDTO> getSupplierById(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getSupplierById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<SupplierDTO> createSupplier(@Valid @RequestBody SupplierCreateDTO createDTO) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierService.createSupplier(createDTO));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<SupplierDTO> updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody SupplierCreateDTO updateDTO) {
        return ResponseEntity.ok(supplierService.updateSupplier(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOSPITAL_MANAGER')")
    public ResponseEntity<Void> deleteSupplier(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<SupplierDTO> updateSupplierStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(supplierService.updateSupplierStatus(id, status));
    }

    @PostMapping("/{id}/contacts")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<SupplierContactDTO> addContact(
            @PathVariable Long id,
            @Valid @RequestBody SupplierContactDTO contactDTO) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierService.addContact(id, contactDTO));
    }

    @PutMapping("/contacts/{contactId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<SupplierContactDTO> updateContact(
            @PathVariable Long contactId,
            @Valid @RequestBody SupplierContactDTO contactDTO) {
        return ResponseEntity.ok(supplierService.updateContact(contactId, contactDTO));
    }

    @DeleteMapping("/contacts/{contactId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<Void> deleteContact(@PathVariable Long contactId) {
        supplierService.deleteContact(contactId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/documents")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<SupplierDocumentDTO> uploadDocument(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam("documentName") String documentName,
            @RequestParam(required = false) String expiryDate) {

        // Validate file
        if (file.isEmpty()) {
            throw new BusinessException("Please select a file to upload");
        }

        // Validate file size (5MB limit)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("File size must be less than 5MB");
        }

        // Validate file type
        String contentType = file.getContentType();
        List<String> allowedTypes = Arrays.asList(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "image/jpeg",
                "image/png"
        );

        if (!allowedTypes.contains(contentType)) {
            throw new BusinessException("Invalid file type. Allowed types: PDF, DOC, DOCX, JPG, PNG");
        }

        SupplierDocumentDTO document = supplierService.uploadDocument(id, file, documentType, documentName, expiryDate);
        return ResponseEntity.status(HttpStatus.CREATED).body(document);
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<List<SupplierDocumentDTO>> getSupplierDocuments(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getSupplierDocuments(id));
    }

    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long documentId) {
        supplierService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/documents/{documentId}/download")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PHARMACY_STAFF', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long documentId) {
        // Use service instead of direct repository access
        SupplierDocument document = supplierService.getDocumentById(documentId);

        try {
            Path filePath = Paths.get("." + document.getFilePath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                throw new ResourceNotFoundException("File", "path", document.getFilePath());
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + document.getDocumentName() + "\"")
                    .body(resource);

        } catch (Exception e) {
            throw new BusinessException("Could not download file: " + e.getMessage());
        }
    }

    @GetMapping("/documents/expiring")
    @PreAuthorize("hasAnyRole('HOSPITAL_MANAGER', 'PROCUREMENT_OFFICER')")
    public ResponseEntity<List<SupplierDocumentDTO>> getExpiringDocuments(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(supplierService.getExpiringDocuments(days));
    }
}