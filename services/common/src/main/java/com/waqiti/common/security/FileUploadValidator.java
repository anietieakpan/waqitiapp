package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Comprehensive file upload validation service
 * Provides security validation for uploaded files to prevent malware and attacks
 */
@Slf4j
@Service
public class FileUploadValidator {

    // Maximum file sizes by type (in bytes)
    private static final Map<String, Long> MAX_FILE_SIZES = Map.of(
        "image/jpeg", 10L * 1024 * 1024,  // 10MB
        "image/png", 10L * 1024 * 1024,   // 10MB
        "image/gif", 5L * 1024 * 1024,    // 5MB
        "application/pdf", 25L * 1024 * 1024, // 25MB
        "text/plain", 1L * 1024 * 1024,   // 1MB
        "application/msword", 10L * 1024 * 1024, // 10MB
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 10L * 1024 * 1024 // 10MB
    );

    // Allowed MIME types for different document categories
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif"
    );

    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain"
    );

    private static final Set<String> ALL_ALLOWED_TYPES = new HashSet<>();
    static {
        ALL_ALLOWED_TYPES.addAll(ALLOWED_IMAGE_TYPES);
        ALL_ALLOWED_TYPES.addAll(ALLOWED_DOCUMENT_TYPES);
    }

    // Dangerous file extensions to block
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
        "exe", "bat", "cmd", "com", "pif", "scr", "vbs", "js", "jar", "wsf", "wsh",
        "ps1", "ps1xml", "ps2", "ps2xml", "psc1", "psc2", "msh", "msh1", "msh2",
        "mshxml", "msh1xml", "msh2xml", "scf", "lnk", "inf", "reg", "php", "jsp",
        "asp", "aspx", "cgi", "pl", "py", "rb", "sh", "bash", "zsh", "fish"
    );

    // File signature patterns (magic numbers) for validation
    private static final Map<String, byte[]> FILE_SIGNATURES = Map.of(
        "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
        "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
        "image/gif", new byte[]{0x47, 0x49, 0x46, 0x38},
        "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46} // %PDF
    );

    // Malware signature patterns (simplified)
    private static final Set<String> MALWARE_PATTERNS = Set.of(
        "eval(", "exec(", "system(", "shell_exec(", "passthru(",
        "<script", "javascript:", "vbscript:", "onload=", "onerror=",
        "<?php", "<%", "<%=", "#!/bin/", "#!/usr/bin/"
    );

    @Value("${file.upload.max.size:26214400}") // 25MB default
    private long globalMaxFileSize;

    @Value("${file.upload.antivirus.enabled:false}")
    private boolean antivirusEnabled;

    /**
     * Validates an uploaded file for security issues
     * @param file The uploaded file
     * @param allowedTypes Set of allowed MIME types
     * @return ValidationResult with validation status and messages
     */
    public ValidationResult validateFile(MultipartFile file, Set<String> allowedTypes) {
        if (file == null || file.isEmpty()) {
            return ValidationResult.invalid("File is empty or null");
        }

        ValidationResult result = new ValidationResult();

        try {
            // Check file size
            validateFileSize(file, result);

            // Check file extension
            validateFileExtension(file, result);

            // Check MIME type
            validateMimeType(file, allowedTypes, result);

            // Check file signature (magic numbers)
            validateFileSignature(file, result);

            // Check for malicious content
            validateFileContent(file, result);

            // Scan filename for suspicious patterns
            validateFilename(file.getOriginalFilename(), result);

            // Additional checks for specific file types
            performTypeSpecificValidation(file, result);

            if (antivirusEnabled) {
                // Placeholder for antivirus integration
                scanForMalware(file, result);
            }

        } catch (Exception e) {
            log.error("Error validating file: {}", file.getOriginalFilename(), e);
            result.addError("File validation failed due to internal error");
        }

        return result;
    }

    /**
     * Validates file for KYC document upload
     */
    public ValidationResult validateKYCDocument(MultipartFile file) {
        Set<String> allowedTypes = new HashSet<>();
        allowedTypes.addAll(ALLOWED_IMAGE_TYPES);
        allowedTypes.add("application/pdf");
        
        return validateFile(file, allowedTypes);
    }

    /**
     * Validates file for general document upload
     */
    public ValidationResult validateDocument(MultipartFile file) {
        return validateFile(file, ALLOWED_DOCUMENT_TYPES);
    }

    /**
     * Validates file for image upload
     */
    public ValidationResult validateImage(MultipartFile file) {
        return validateFile(file, ALLOWED_IMAGE_TYPES);
    }

    private void validateFileSize(MultipartFile file, ValidationResult result) {
        long fileSize = file.getSize();
        
        // Check global max size
        if (fileSize > globalMaxFileSize) {
            result.addError("File size exceeds maximum allowed size: " + 
                (globalMaxFileSize / 1024 / 1024) + "MB");
            return;
        }

        // Check type-specific max size
        String contentType = file.getContentType();
        if (contentType != null && MAX_FILE_SIZES.containsKey(contentType)) {
            long maxSize = MAX_FILE_SIZES.get(contentType);
            if (fileSize > maxSize) {
                result.addError("File size exceeds maximum for " + contentType + ": " + 
                    (maxSize / 1024 / 1024) + "MB");
            }
        }
    }

    private void validateFileExtension(MultipartFile file, ValidationResult result) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            result.addError("Filename is empty");
            return;
        }

        String extension = getFileExtension(filename).toLowerCase();
        
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            result.addError("File extension '" + extension + "' is not allowed");
        }

        // Check for multiple extensions (e.g., .jpg.exe)
        if (filename.toLowerCase().matches(".*\\.[a-z0-9]+\\.[a-z0-9]+$")) {
            String[] parts = filename.toLowerCase().split("\\.");
            for (String part : parts) {
                if (BLOCKED_EXTENSIONS.contains(part)) {
                    result.addError("File contains blocked extension: " + part);
                }
            }
        }
    }

    private void validateMimeType(MultipartFile file, Set<String> allowedTypes, ValidationResult result) {
        String contentType = file.getContentType();
        
        if (contentType == null || contentType.trim().isEmpty()) {
            result.addError("File content type is missing");
            return;
        }

        if (!allowedTypes.contains(contentType)) {
            result.addError("File type '" + contentType + "' is not allowed");
        }
    }

    private void validateFileSignature(MultipartFile file, ValidationResult result) {
        try (InputStream inputStream = file.getInputStream()) {
            String contentType = file.getContentType();
            if (contentType != null && FILE_SIGNATURES.containsKey(contentType)) {
                byte[] expectedSignature = FILE_SIGNATURES.get(contentType);
                byte[] actualSignature = new byte[expectedSignature.length];
                
                int bytesRead = inputStream.read(actualSignature);
                if (bytesRead < expectedSignature.length) {
                    result.addError("File is too small or corrupted");
                    return;
                }

                if (!Arrays.equals(expectedSignature, actualSignature)) {
                    result.addError("File signature doesn't match declared type");
                }
            }
        } catch (IOException e) {
            result.addError("Unable to read file signature");
        }
    }

    private void validateFileContent(MultipartFile file, ValidationResult result) {
        try (InputStream inputStream = file.getInputStream()) {
            // Read first few KB to scan for malicious content
            byte[] buffer = new byte[8192]; // 8KB
            int bytesRead = inputStream.read(buffer);
            
            if (bytesRead > 0) {
                String content = new String(buffer, 0, bytesRead).toLowerCase();
                
                for (String pattern : MALWARE_PATTERNS) {
                    if (content.contains(pattern.toLowerCase())) {
                        result.addError("File contains potentially malicious content");
                        log.warn("Malicious pattern detected in file: {} - pattern: {}", 
                            file.getOriginalFilename(), pattern);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            result.addError("Unable to scan file content");
        }
    }

    private void validateFilename(String filename, ValidationResult result) {
        if (filename == null || filename.trim().isEmpty()) {
            result.addError("Filename is empty");
            return;
        }

        // Check for path traversal attempts
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            result.addError("Filename contains invalid characters");
        }

        // Check for excessively long filenames
        if (filename.length() > 255) {
            result.addError("Filename is too long");
        }

        // Check for suspicious characters
        Pattern suspiciousPattern = Pattern.compile("[<>:\"|?*\\x00-\\x1f]");
        if (suspiciousPattern.matcher(filename).find()) {
            result.addError("Filename contains invalid characters");
        }
    }

    private void performTypeSpecificValidation(MultipartFile file, ValidationResult result) {
        String contentType = file.getContentType();
        
        if (contentType != null) {
            switch (contentType) {
                case "application/pdf":
                    validatePdfFile(file, result);
                    break;
                case "image/jpeg":
                case "image/png":
                case "image/gif":
                    validateImageFile(file, result);
                    break;
            }
        }
    }

    private void validatePdfFile(MultipartFile file, ValidationResult result) {
        // Additional PDF-specific validations
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[10];
            int bytesRead = inputStream.read(header);
            
            if (bytesRead >= 4) {
                String headerStr = new String(header, 0, Math.min(bytesRead, 10));
                if (!headerStr.startsWith("%PDF-")) {
                    result.addError("Invalid PDF file format");
                }
            }
        } catch (IOException e) {
            result.addError("Unable to validate PDF file");
        }
    }

    private void validateImageFile(MultipartFile file, ValidationResult result) {
        // Additional image-specific validations
        long fileSize = file.getSize();
        
        // Check for suspiciously small image files
        if (fileSize < 100) {
            result.addError("Image file is suspiciously small");
        }
        
        // Check for excessively large image files
        if (fileSize > 50 * 1024 * 1024) { // 50MB
            result.addError("Image file is too large");
        }
    }

    private void scanForMalware(MultipartFile file, ValidationResult result) {
        // Placeholder for antivirus integration
        // This would integrate with ClamAV or other antivirus solutions
        log.debug("Antivirus scan would be performed here for file: {}", file.getOriginalFilename());
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * Generates secure filename to prevent path traversal and other attacks
     */
    public String generateSecureFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }

        // Extract extension
        String extension = getFileExtension(originalFilename);
        
        // Generate secure base name
        String baseName = originalFilename;
        if (baseName.contains(".")) {
            baseName = baseName.substring(0, baseName.lastIndexOf("."));
        }
        
        // Sanitize base name
        baseName = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // Ensure it's not too long
        if (baseName.length() > 100) {
            baseName = baseName.substring(0, 100);
        }
        
        // Add timestamp for uniqueness
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        return baseName + "_" + timestamp + (extension.isEmpty() ? "" : "." + extension);
    }

    /**
     * Calculates file hash for integrity verification
     */
    public String calculateFileHash(MultipartFile file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        try (InputStream inputStream = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        
        return hexString.toString();
    }

    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        public List<String> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }

        public static ValidationResult invalid(String error) {
            ValidationResult result = new ValidationResult();
            result.addError(error);
            return result;
        }

        public static ValidationResult valid() {
            return new ValidationResult();
        }
    }
}