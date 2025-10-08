package com.medicorex.service.supplier;

import com.medicorex.dto.*;
import com.medicorex.entity.Supplier;
import com.medicorex.entity.Supplier.SupplierStatus;
import com.medicorex.entity.SupplierContact;
import com.medicorex.entity.SupplierDocument;
import com.medicorex.entity.User;
import com.medicorex.exception.BusinessException;
import com.medicorex.exception.ResourceNotFoundException;
import com.medicorex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.medicorex.exception.FileStorageException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierContactRepository contactRepository;
    private final SupplierDocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public PageResponseDTO<SupplierDTO> getAllSuppliers(Pageable pageable) {
        Page<Supplier> supplierPage = supplierRepository.findAll(pageable);
        return convertToPageResponse(supplierPage);
    }

    public PageResponseDTO<SupplierDTO> searchSuppliers(String searchTerm, Pageable pageable) {
        Page<Supplier> supplierPage = supplierRepository.searchSuppliers(searchTerm, pageable);
        return convertToPageResponse(supplierPage);
    }

    public List<SupplierDTO> getActiveSuppliers() {
        return supplierRepository.findByStatus(SupplierStatus.ACTIVE).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public SupplierDTO getSupplierById(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", id));
        return convertToDTO(supplier);
    }

    public SupplierDTO createSupplier(SupplierCreateDTO createDTO) {
        // Check for duplicate name
        if (supplierRepository.existsByName(createDTO.getName())) {
            throw new BusinessException("Supplier with name '" + createDTO.getName() + "' already exists");
        }

        Supplier supplier = new Supplier();
        supplier.setCode(generateSupplierCode());
        supplier.setName(createDTO.getName());
        supplier.setTaxId(createDTO.getTaxId());
        supplier.setRegistrationNumber(createDTO.getRegistrationNumber());
        supplier.setWebsite(createDTO.getWebsite());
        supplier.setEmail(createDTO.getEmail());
        supplier.setPhone(createDTO.getPhone());
        supplier.setFax(createDTO.getFax());
        supplier.setAddressLine1(createDTO.getAddressLine1());
        supplier.setAddressLine2(createDTO.getAddressLine2());
        supplier.setCity(createDTO.getCity());
        supplier.setState(createDTO.getState());
        supplier.setCountry(createDTO.getCountry());
        supplier.setPostalCode(createDTO.getPostalCode());
        supplier.setPaymentTerms(createDTO.getPaymentTerms());
        supplier.setCreditLimit(createDTO.getCreditLimit());
        supplier.setNotes(createDTO.getNotes());
        supplier.setStatus(SupplierStatus.ACTIVE);

        // Set created by
        User currentUser = getCurrentUser();
        supplier.setCreatedBy(currentUser);

        Supplier savedSupplier = supplierRepository.save(supplier);
        log.info("Created new supplier: {} with code: {}", savedSupplier.getName(), savedSupplier.getCode());

        return convertToDTO(savedSupplier);
    }

    public SupplierDTO updateSupplier(Long id, SupplierCreateDTO updateDTO) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", id));

        // Check for duplicate name if name is being changed
        if (!supplier.getName().equals(updateDTO.getName()) &&
                supplierRepository.existsByName(updateDTO.getName())) {
            throw new BusinessException("Supplier with name '" + updateDTO.getName() + "' already exists");
        }

        supplier.setName(updateDTO.getName());
        supplier.setTaxId(updateDTO.getTaxId());
        supplier.setRegistrationNumber(updateDTO.getRegistrationNumber());
        supplier.setWebsite(updateDTO.getWebsite());
        supplier.setEmail(updateDTO.getEmail());
        supplier.setPhone(updateDTO.getPhone());
        supplier.setFax(updateDTO.getFax());
        supplier.setAddressLine1(updateDTO.getAddressLine1());
        supplier.setAddressLine2(updateDTO.getAddressLine2());
        supplier.setCity(updateDTO.getCity());
        supplier.setState(updateDTO.getState());
        supplier.setCountry(updateDTO.getCountry());
        supplier.setPostalCode(updateDTO.getPostalCode());
        supplier.setPaymentTerms(updateDTO.getPaymentTerms());
        supplier.setCreditLimit(updateDTO.getCreditLimit());
        supplier.setNotes(updateDTO.getNotes());

        Supplier updatedSupplier = supplierRepository.save(supplier);
        log.info("Updated supplier: {}", updatedSupplier.getName());

        return convertToDTO(updatedSupplier);
    }

    public void deleteSupplier(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", id));

        // Instead of hard delete, mark as INACTIVE
        supplier.setStatus(SupplierStatus.INACTIVE);
        supplierRepository.save(supplier);
        log.info("Marked supplier as inactive: {}", supplier.getName());
    }

    public SupplierDTO updateSupplierStatus(Long id, String status) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", id));

        try {
            SupplierStatus newStatus = SupplierStatus.valueOf(status.toUpperCase());
            supplier.setStatus(newStatus);
            Supplier updatedSupplier = supplierRepository.save(supplier);
            log.info("Updated supplier {} status to {}", supplier.getName(), newStatus);
            return convertToDTO(updatedSupplier);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid status: " + status);
        }
    }

    // Contact Management Methods
    public SupplierContactDTO addContact(Long supplierId, SupplierContactDTO contactDTO) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", supplierId));

        SupplierContact contact = new SupplierContact();
        contact.setSupplier(supplier);
        contact.setName(contactDTO.getName());
        contact.setDesignation(contactDTO.getDesignation());
        contact.setEmail(contactDTO.getEmail());
        contact.setPhone(contactDTO.getPhone());
        contact.setMobile(contactDTO.getMobile());
        contact.setIsPrimary(contactDTO.getIsPrimary());
        contact.setNotes(contactDTO.getNotes());

        // If this is marked as primary, unmark other primary contacts
        if (Boolean.TRUE.equals(contactDTO.getIsPrimary())) {
            List<SupplierContact> primaryContacts = contactRepository.findBySupplierIdAndIsPrimaryTrue(supplierId);
            primaryContacts.forEach(c -> c.setIsPrimary(false));
            contactRepository.saveAll(primaryContacts);
        }

        SupplierContact savedContact = contactRepository.save(contact);
        return convertContactToDTO(savedContact);
    }

    public SupplierContactDTO updateContact(Long contactId, SupplierContactDTO contactDTO) {
        SupplierContact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier Contact", "id", contactId));

        contact.setName(contactDTO.getName());
        contact.setDesignation(contactDTO.getDesignation());
        contact.setEmail(contactDTO.getEmail());
        contact.setPhone(contactDTO.getPhone());
        contact.setMobile(contactDTO.getMobile());
        contact.setIsPrimary(contactDTO.getIsPrimary());
        contact.setNotes(contactDTO.getNotes());

        SupplierContact updated = contactRepository.save(contact);
        log.info("Updated contact: {} for supplier: {}", updated.getName(), updated.getSupplier().getName());

        return convertContactToDTO(updated);
    }

    public void deleteContact(Long contactId) {
        contactRepository.deleteById(contactId);
    }

    // Document Management Methods
    public SupplierDocumentDTO uploadDocument(Long supplierId, MultipartFile file,
                                              String documentType, String documentName,
                                              String expiryDate) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", supplierId));
        try {
            // Create supplier documents directory if it doesn't exist
            Path supplierDocsPath = Paths.get(uploadDir, "suppliers", supplierId.toString())
                    .toAbsolutePath().normalize();
            Files.createDirectories(supplierDocsPath);
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

            // Save file
            Path targetLocation = supplierDocsPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            // Create database record
            SupplierDocument document = new SupplierDocument();
            document.setSupplier(supplier);
            document.setDocumentType(documentType);
            document.setDocumentName(documentName);
            document.setFilePath("/uploads/suppliers/" + supplierId + "/" + uniqueFilename);
            document.setFileSize(file.getSize());

            if (expiryDate != null && !expiryDate.isEmpty()) {
                document.setExpiryDate(LocalDate.parse(expiryDate));
            }

            document.setUploadedBy(getCurrentUser());

            SupplierDocument savedDocument = documentRepository.save(document);
            log.info("Document uploaded for supplier {}: {}", supplier.getName(), documentName);

            return convertDocumentToDTO(savedDocument);

        } catch (Exception e) {
            log.error("Failed to upload document: {}", e.getMessage());
            throw new BusinessException("Failed to upload document: " + e.getMessage());
        }
    }

    public ResponseEntity<Resource> downloadDocument(Long documentId) {
        try {
            SupplierDocument document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

            // Build file path
            Path filePath = Paths.get("." + document.getFilePath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                throw new FileStorageException("File not found: " + document.getDocumentName());
            }

            // Determine content type more accurately
            String contentType = "application/octet-stream";
            String fileName = document.getDocumentName();
            String fileExtension = "";

            if (fileName.contains(".")) {
                fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            }

            switch(fileExtension) {
                case "pdf":
                    contentType = "application/pdf";
                    break;
                case "doc":
                    contentType = "application/msword";
                    break;
                case "docx":
                    contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    break;
                case "xls":
                    contentType = "application/vnd.ms-excel";
                    break;
                case "xlsx":
                    contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                    break;
                case "jpg":
                case "jpeg":
                    contentType = "image/jpeg";
                    break;
                case "png":
                    contentType = "image/png";
                    break;
                case "txt":
                    contentType = "text/plain";
                    break;
            }

            // ✅ FIX: Ensure filename includes extension
            String downloadFileName = fileName;
            if (!fileName.contains(".") && !fileExtension.isEmpty()) {
                downloadFileName = fileName + "." + fileExtension;
            }

            // ✅ FIX: Set proper Content-Disposition with inline disposition for preview
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + downloadFileName + "\"")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(resource.contentLength()))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(resource);

        } catch (Exception e) {
            log.error("Error downloading document: {}", e.getMessage());
            throw new FileStorageException("Could not download file", e);
        }
    }

    public List<SupplierDocumentDTO> getExpiringDocuments(int daysAhead) {
        LocalDate expiryDate = LocalDate.now().plusDays(daysAhead);
        List<SupplierDocument> expiringDocs = documentRepository.findExpiringDocuments(expiryDate);

        return expiringDocs.stream()
                .map(this::convertDocumentToDTO)
                .collect(Collectors.toList());
    }

    public List<SupplierDocumentDTO> getDocumentsByType(Long supplierId, String documentType) {
        List<SupplierDocument> documents = documentRepository.findBySupplierIdAndType(supplierId, documentType);
        return documents.stream()
                .map(this::convertDocumentToDTO)
                .collect(Collectors.toList());
    }

    public SupplierDocument getDocumentById(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));
    }

    public void deleteDocument(Long documentId) {
        SupplierDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        // Delete file from disk
        try {
            Path filePath = Paths.get("." + document.getFilePath()).normalize();
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            log.error("Failed to delete file from disk: {}", e.getMessage());
        }

        // Delete database record
        documentRepository.delete(document);
        log.info("Deleted document: {}", document.getDocumentName());
    }

    public List<SupplierDocumentDTO> getSupplierDocuments(Long supplierId) {
        return documentRepository.findBySupplierId(supplierId).stream()
                .map(this::convertDocumentToDTO)
                .collect(Collectors.toList());
    }

    // Helper Methods
    private String generateSupplierCode() {
        long count = supplierRepository.count() + 1;
        String code;
        do {
            code = String.format("SUP%05d", count++);
        } while (supplierRepository.existsByCode(code));
        return code;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            return userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        }
        throw new BusinessException("No authenticated user found");
    }

    private SupplierDTO convertToDTO(Supplier supplier) {
        return SupplierDTO.builder()
                .id(supplier.getId())
                .code(supplier.getCode())
                .name(supplier.getName())
                .taxId(supplier.getTaxId())
                .registrationNumber(supplier.getRegistrationNumber())
                .website(supplier.getWebsite())
                .email(supplier.getEmail())
                .phone(supplier.getPhone())
                .fax(supplier.getFax())
                .addressLine1(supplier.getAddressLine1())
                .addressLine2(supplier.getAddressLine2())
                .city(supplier.getCity())
                .state(supplier.getState())
                .country(supplier.getCountry())
                .postalCode(supplier.getPostalCode())
                .status(supplier.getStatus().toString())
                .rating(supplier.getRating())
                .paymentTerms(supplier.getPaymentTerms())
                .creditLimit(supplier.getCreditLimit())
                .notes(supplier.getNotes())
                .createdBy(supplier.getCreatedBy() != null ? supplier.getCreatedBy().getUsername() : null)
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .contacts(supplier.getContacts().stream()
                        .map(this::convertContactToDTO)
                        .collect(Collectors.toList()))
                .documents(supplier.getDocuments().stream()
                        .map(this::convertDocumentToDTO)
                        .collect(Collectors.toList()))
                .build();
    }

    private SupplierContactDTO convertContactToDTO(SupplierContact contact) {
        return SupplierContactDTO.builder()
                .id(contact.getId())
                .supplierId(contact.getSupplier().getId())
                .name(contact.getName())
                .designation(contact.getDesignation())
                .email(contact.getEmail())
                .phone(contact.getPhone())
                .mobile(contact.getMobile())
                .isPrimary(contact.getIsPrimary())
                .notes(contact.getNotes())
                .build();
    }

    private SupplierDocumentDTO convertDocumentToDTO(SupplierDocument document) {
        return SupplierDocumentDTO.builder()
                .id(document.getId())
                .supplierId(document.getSupplier().getId())
                .documentType(document.getDocumentType())
                .documentName(document.getDocumentName())
                .filePath(document.getFilePath())
                .fileSize(document.getFileSize())
                .expiryDate(document.getExpiryDate())
                .uploadedBy(document.getUploadedBy() != null ? document.getUploadedBy().getUsername() : null)
                .build();
    }

    private PageResponseDTO<SupplierDTO> convertToPageResponse(Page<Supplier> supplierPage) {
        List<SupplierDTO> content = supplierPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageResponseDTO.<SupplierDTO>builder()
                .content(content)
                .size(supplierPage.getSize())
                .totalElements(supplierPage.getTotalElements())
                .totalPages(supplierPage.getTotalPages())
                .last(supplierPage.isLast())
                .build();
    }
}