package com.waqiti.reconciliation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import jakarta.servlet.MultipartConfigElement;
import java.io.File;

/**
 * Configuration for secure file upload handling.
 * Implements security best practices for file uploads.
 */
@Configuration
public class FileUploadSecurityConfig {
    
    @Value("${file.upload.max.size:52428800}") // 50MB
    private long maxFileSize;
    
    @Value("${file.upload.max.request.size:104857600}") // 100MB
    private long maxRequestSize;
    
    @Value("${file.upload.temp.dir:/tmp/uploads}")
    private String tempDir;
    
    @Value("${file.upload.threshold:1048576}") // 1MB
    private int fileSizeThreshold;
    
    /**
     * Configure multipart file upload with security constraints
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        
        // Set maximum file size
        factory.setMaxFileSize(DataSize.ofBytes(maxFileSize));
        
        // Set maximum request size
        factory.setMaxRequestSize(DataSize.ofBytes(maxRequestSize));
        
        // Set the size threshold after which files will be written to disk
        factory.setFileSizeThreshold(DataSize.ofBytes(fileSizeThreshold));
        
        // Set temporary upload directory with proper permissions
        File uploadDir = new File(tempDir);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
        
        // Set restrictive directory permissions (Unix/Linux)
        try {
            java.nio.file.Path dirPath = uploadDir.toPath();
            java.util.Set<java.nio.file.attribute.PosixFilePermission> perms = 
                new java.util.HashSet<>();
            perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ);
            perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
            perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
            java.nio.file.Files.setPosixFilePermissions(dirPath, perms);
        } catch (Exception e) {
            // Not a POSIX system or permissions couldn't be set
        }
        
        factory.setLocation(tempDir);
        
        return factory.createMultipartConfig();
    }
    
    /**
     * Configure multipart resolver with security settings
     */
    @Bean
    public MultipartResolver multipartResolver() {
        StandardServletMultipartResolver resolver = new StandardServletMultipartResolver();
        resolver.setResolveLazily(true); // Resolve multipart requests lazily
        return resolver;
    }
}