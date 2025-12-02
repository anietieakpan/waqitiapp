package com.waqiti.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Production-ready file upload validator with security checks
 */
@Component
public class FileUploadValidator implements ConstraintValidator<ValidationConstraints.ValidFileUpload, Object> {
    
    private String[] allowedExtensions;
    private long maxSize;
    private String[] allowedMimeTypes;
    
    // Magic bytes for common file types
    private static final Map<String, byte[]> FILE_SIGNATURES = new HashMap<>();
    
    static {
        // PDF
        FILE_SIGNATURES.put("application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46}); // %PDF
        
        // JPEG
        FILE_SIGNATURES.put("image/jpeg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF});
        
        // PNG
        FILE_SIGNATURES.put("image/png", new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        
        // GIF
        FILE_SIGNATURES.put("image/gif", new byte[]{0x47, 0x49, 0x46, 0x38}); // GIF8
        
        // ZIP
        FILE_SIGNATURES.put("application/zip", new byte[]{0x50, 0x4B, 0x03, 0x04});
        
        // Microsoft Office (DOCX, XLSX, PPTX)
        FILE_SIGNATURES.put("application/vnd.openxmlformats", new byte[]{0x50, 0x4B, 0x03, 0x04});
        
        // Microsoft Office (older formats)
        FILE_SIGNATURES.put("application/msword", new byte[]{(byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0});
    }
    
    // Dangerous file extensions that should always be blocked
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
        ".exe", ".com", ".bat", ".cmd", ".scr", ".vbs", ".vbe", ".js", ".jse",
        ".wsf", ".wsh", ".ps1", ".ps1xml", ".ps2", ".ps2xml", ".psc1", ".psc2",
        ".msh", ".msh1", ".msh2", ".mshxml", ".msh1xml", ".msh2xml",
        ".scf", ".lnk", ".inf", ".reg", ".dll", ".app", ".jar", ".jsp",
        ".php", ".asp", ".aspx", ".cgi", ".pl", ".py", ".rb", ".sh"
    );
    
    @Override
    public void initialize(ValidationConstraints.ValidFileUpload annotation) {
        this.allowedExtensions = annotation.allowedExtensions();
        this.maxSize = annotation.maxSize();
        this.allowedMimeTypes = annotation.allowedMimeTypes();
    }
    
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotNull handle null validation
        }
        
        if (value instanceof MultipartFile) {
            return validateMultipartFile((MultipartFile) value, context);
        } else if (value instanceof byte[]) {
            return validateByteArray((byte[]) value, context);
        } else if (value instanceof java.io.File) {
            return validateFile((java.io.File) value, context);
        }
        
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("Unsupported file type for validation").addConstraintViolation();
        return false;
    }
    
    private boolean validateMultipartFile(MultipartFile file, ConstraintValidatorContext context) {
        // Check if file is empty
        if (file.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("File cannot be empty").addConstraintViolation();
            return false;
        }
        
        // Check file size
        if (file.getSize() > maxSize) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("File size exceeds maximum allowed size of %d bytes", maxSize)
            ).addConstraintViolation();
            return false;
        }
        
        // Check file extension
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("File must have a name").addConstraintViolation();
            return false;
        }
        
        if (!isValidExtension(fileName, context)) {
            return false;
        }
        
        // Check MIME type
        String contentType = file.getContentType();
        if (!isValidMimeType(contentType, context)) {
            return false;
        }
        
        // Validate file content (magic bytes)
        try {
            byte[] fileContent = file.getBytes();
            if (!validateFileContent(fileContent, contentType, context)) {
                return false;
            }
        } catch (IOException e) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Error reading file content").addConstraintViolation();
            return false;
        }
        
        // Check for malicious content
        if (containsMaliciousContent(fileName)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("File name contains suspicious patterns").addConstraintViolation();
            return false;
        }
        
        return true;
    }
    
    private boolean validateByteArray(byte[] content, ConstraintValidatorContext context) {
        // Check size
        if (content.length > maxSize) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("File size exceeds maximum allowed size of %d bytes", maxSize)
            ).addConstraintViolation();
            return false;
        }
        
        // Try to detect MIME type from content
        String detectedType = detectMimeType(content);
        if (!isValidMimeType(detectedType, context)) {
            return false;
        }
        
        return validateFileContent(content, detectedType, context);
    }
    
    private boolean validateFile(java.io.File file, ConstraintValidatorContext context) {
        // Check if file exists
        if (!file.exists()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("File does not exist").addConstraintViolation();
            return false;
        }
        
        // Check file size
        if (file.length() > maxSize) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("File size exceeds maximum allowed size of %d bytes", maxSize)
            ).addConstraintViolation();
            return false;
        }
        
        // Check file extension
        String fileName = file.getName();
        if (!isValidExtension(fileName, context)) {
            return false;
        }
        
        // Check for malicious content
        if (containsMaliciousContent(fileName)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("File name contains suspicious patterns").addConstraintViolation();
            return false;
        }
        
        return true;
    }
    
    private boolean isValidExtension(String fileName, ConstraintValidatorContext context) {
        String lowerFileName = fileName.toLowerCase();
        
        // Check for dangerous extensions
        for (String dangerous : DANGEROUS_EXTENSIONS) {
            if (lowerFileName.endsWith(dangerous)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    String.format("File extension %s is not allowed for security reasons", dangerous)
                ).addConstraintViolation();
                return false;
            }
        }
        
        // Check for double extensions (e.g., file.pdf.exe)
        if (hasDoubleExtension(fileName)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Files with double extensions are not allowed").addConstraintViolation();
            return false;
        }
        
        // Check allowed extensions
        boolean hasValidExtension = false;
        for (String allowed : allowedExtensions) {
            if (lowerFileName.endsWith(allowed.toLowerCase())) {
                hasValidExtension = true;
                break;
            }
        }
        
        if (!hasValidExtension) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("File extension must be one of: %s", Arrays.toString(allowedExtensions))
            ).addConstraintViolation();
            return false;
        }
        
        return true;
    }
    
    private boolean isValidMimeType(String mimeType, ConstraintValidatorContext context) {
        if (mimeType == null || mimeType.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("File MIME type cannot be determined").addConstraintViolation();
            return false;
        }
        
        boolean validMime = false;
        for (String allowed : allowedMimeTypes) {
            if (mimeType.startsWith(allowed)) {
                validMime = true;
                break;
            }
        }
        
        if (!validMime) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("MIME type %s is not allowed. Allowed types: %s", 
                    mimeType, Arrays.toString(allowedMimeTypes))
            ).addConstraintViolation();
            return false;
        }
        
        return true;
    }
    
    private boolean validateFileContent(byte[] content, String declaredMimeType, ConstraintValidatorContext context) {
        if (content == null || content.length == 0) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("File content is empty").addConstraintViolation();
            return false;
        }
        
        // Validate magic bytes match declared MIME type
        for (Map.Entry<String, byte[]> entry : FILE_SIGNATURES.entrySet()) {
            if (declaredMimeType != null && declaredMimeType.startsWith(entry.getKey())) {
                byte[] signature = entry.getValue();
                if (!startsWith(content, signature)) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate(
                        "File content does not match declared MIME type"
                    ).addConstraintViolation();
                    return false;
                }
                break;
            }
        }
        
        // Check for embedded executables
        if (containsExecutableSignature(content)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "File contains embedded executable content"
            ).addConstraintViolation();
            return false;
        }
        
        return true;
    }
    
    private boolean startsWith(byte[] array, byte[] prefix) {
        if (array.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (array[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
    
    private boolean containsExecutableSignature(byte[] content) {
        // Check for MZ header (DOS/Windows executables)
        if (content.length >= 2 && content[0] == 0x4D && content[1] == 0x5A) {
            return true;
        }
        
        // Check for ELF header (Linux executables)
        if (content.length >= 4 && content[0] == 0x7F && 
            content[1] == 0x45 && content[2] == 0x4C && content[3] == 0x46) {
            return true;
        }
        
        // Check for Mach-O header (macOS executables)
        if (content.length >= 4) {
            int magic = ((content[0] & 0xFF) << 24) | ((content[1] & 0xFF) << 16) |
                       ((content[2] & 0xFF) << 8) | (content[3] & 0xFF);
            if (magic == 0xFEEDFACE || magic == 0xFEEDFACF || 
                magic == 0xCEFAEDFE || magic == 0xCFFAEDFE) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean hasDoubleExtension(String fileName) {
        String[] parts = fileName.split("\\.");
        if (parts.length < 3) {
            return false;
        }
        
        // Check if second-to-last extension is suspicious
        String secondToLast = "." + parts[parts.length - 2].toLowerCase();
        return DANGEROUS_EXTENSIONS.contains(secondToLast) || 
               Arrays.asList(allowedExtensions).contains(secondToLast);
    }
    
    private boolean containsMaliciousContent(String fileName) {
        String lower = fileName.toLowerCase();
        
        // Check for path traversal attempts
        if (lower.contains("../") || lower.contains("..\\")) {
            return true;
        }
        
        // Check for null bytes
        if (fileName.contains("\0")) {
            return true;
        }
        
        // Check for control characters
        for (char c : fileName.toCharArray()) {
            if (Character.isISOControl(c) && c != '\t') {
                return true;
            }
        }
        
        // Check for Unicode direction override characters
        if (fileName.contains("\u202E") || fileName.contains("\u202D")) {
            return true;
        }
        
        return false;
    }
    
    private String detectMimeType(byte[] content) {
        for (Map.Entry<String, byte[]> entry : FILE_SIGNATURES.entrySet()) {
            if (startsWith(content, entry.getValue())) {
                return entry.getKey();
            }
        }
        return "application/octet-stream";
    }
}