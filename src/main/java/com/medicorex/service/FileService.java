package com.medicorex.service;

import com.medicorex.entity.FileUpload;
import com.medicorex.entity.User;
import com.medicorex.exception.BusinessException;
import com.medicorex.repository.FileUploadRepository;
import com.medicorex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FileService {

    private final FileUploadRepository fileUploadRepository;
    private final UserRepository userRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.allowed-extensions}")
    private String allowedExtensions;

    @Value("${file.max-size}")
    private Long maxFileSize;

    public String uploadProductImage(MultipartFile file) {
        validateFile(file);

        try {
            // Generate unique filename
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String fileExtension = getFileExtension(originalFilename);
            String newFilename = UUID.randomUUID().toString() + "." + fileExtension;

            // Save file to disk
            Path targetLocation = Paths.get(uploadDir + "/products/" + newFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Save file record to database
            FileUpload fileUpload = new FileUpload();
            fileUpload.setFileName(newFilename);
            fileUpload.setFileType(file.getContentType());
            fileUpload.setFileSize(file.getSize());
            fileUpload.setFilePath("/uploads/products/" + newFilename);
            fileUpload.setUploadedBy(getCurrentUser());

            fileUploadRepository.save(fileUpload);

            // Return the URL path for accessing the image
            return "/uploads/products/" + newFilename;

        } catch (IOException e) {
            throw new BusinessException("Failed to upload file: " + e.getMessage());
        }
    }

    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }

        try {
            // Extract filename from path
            String filename = filePath.substring(filePath.lastIndexOf("/") + 1);
            Path fileToDelete = Paths.get(uploadDir + "/products/" + filename);
            Files.deleteIfExists(fileToDelete);

            // Delete database record
            fileUploadRepository.findByFileName(filename).ifPresent(fileUploadRepository::delete);

        } catch (IOException e) {
            // Log error but don't throw exception
            System.err.println("Failed to delete file: " + e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        // Check if file is empty
        if (file.isEmpty()) {
            throw new BusinessException("File is empty");
        }

        // Check file size
        if (file.getSize() > maxFileSize) {
            throw new BusinessException("File size exceeds maximum allowed size of " + (maxFileSize / 1024 / 1024) + "MB");
        }

        // Check file extension
        String filename = file.getOriginalFilename();
        String extension = getFileExtension(filename);
        List<String> allowed = Arrays.asList(allowedExtensions.split(","));

        if (!allowed.contains(extension.toLowerCase())) {
            throw new BusinessException("File type not allowed. Allowed types: " + allowedExtensions);
        }

        // Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("File must be an image");
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException("Invalid filename");
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElse(null); // File upload can work without user tracking
    }
}