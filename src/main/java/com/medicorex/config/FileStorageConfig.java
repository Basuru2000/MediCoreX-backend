// Update src/main/java/com/medicorex/config/FileStorageConfig.java

package com.medicorex.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class FileStorageConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @PostConstruct
    public void init() {
        try {
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            // Create subdirectories
            Files.createDirectories(uploadPath.resolve("products"));
            Files.createDirectories(uploadPath.resolve("users"));
            Files.createDirectories(uploadPath.resolve("temp"));

            System.out.println("✅ Upload directory initialized at: " + uploadPath);
        } catch (Exception e) {
            System.err.println("❌ Could not create upload directory: " + e.getMessage());
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Make uploaded files accessible via URL
        String uploadPath = "file:" + Paths.get(uploadDir).toAbsolutePath().normalize() + "/";

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath)
                .setCachePeriod(0);  // Disable caching for development

        System.out.println("✅ Static resources mapped: /uploads/** -> " + uploadPath);
    }
}