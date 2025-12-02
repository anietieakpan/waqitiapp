package com.waqiti.reconciliation.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Production-grade secure file upload validation service.
 * Implements comprehensive security controls for file uploads including:
 * - Magic byte validation
 * - File type verification
 * - Content scanning
 * - Virus scanning integration
 * - Size and rate limiting
 * - Path traversal prevention
 * - Archive bomb detection
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecureFileUploadValidator {
    
    private final FileUploadAuditService auditService;
    private final MalwareSignatureDatabase malwareSignatureDatabase;
    private final MalwareHashDatabase malwareHashDatabase;
    private final QuarantineService quarantineService;
    private final SecurityAlertService securityAlertService;
    private final SecurityContextService securityContext;
    private final CloudVirusScanService cloudVirusScanService;
    private final MLThreatDetectionService mlThreatDetectionService;
    private final Tika tika = new Tika();
    
    @Value("${file.upload.max.size:52428800}") // 50MB default
    private long maxFileSize;
    
    @Value("${file.upload.max.name.length:255}")
    private int maxFileNameLength;
    
    @Value("${file.upload.allowed.extensions:csv,xlsx,xls,json,xml,txt,pdf}")
    private String allowedExtensions;
    
    @Value("${file.upload.scan.enabled:true}")
    private boolean virusScanEnabled;
    
    @Value("${file.upload.quarantine.path:/tmp/quarantine}")
    private String quarantinePath;
    
    @Value("${file.upload.temp.path:/tmp/uploads}")
    private String tempUploadPath;
    
    // Magic bytes for common file types
    private static final Map<String, byte[]> MAGIC_BYTES = new HashMap<>();
    static {
        MAGIC_BYTES.put("pdf", new byte[]{0x25, 0x50, 0x44, 0x46}); // %PDF
        MAGIC_BYTES.put("xlsx", new byte[]{0x50, 0x4B, 0x03, 0x04}); // PK.. (ZIP)
        MAGIC_BYTES.put("xls", new byte[]{(byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0}); // OLE
        MAGIC_BYTES.put("csv", new byte[]{}); // Text file, no specific magic bytes
        MAGIC_BYTES.put("json", new byte[]{}); // Text file, starts with { or [
        MAGIC_BYTES.put("xml", new byte[]{0x3C, 0x3F, 0x78, 0x6D, 0x6C}); // <?xml
        MAGIC_BYTES.put("txt", new byte[]{}); // Text file, no specific magic bytes
    }
    
    // Dangerous patterns in file content
    private static final List<Pattern> DANGEROUS_PATTERNS = Arrays.asList(
        Pattern.compile("(?i)<script[^>]*>.*?</script>"),
        Pattern.compile("(?i)javascript:"),
        Pattern.compile("(?i)on\\w+\\s*="),
        Pattern.compile("(?i)<iframe[^>]*>"),
        Pattern.compile("(?i)<embed[^>]*>"),
        Pattern.compile("(?i)<object[^>]*>"),
        Pattern.compile("(?i)eval\\s*\\("),
        Pattern.compile("(?i)expression\\s*\\("),
        Pattern.compile("(?i)vbscript:"),
        Pattern.compile("(?i)onload\\s*="),
        Pattern.compile("(?i)onerror\\s*="),
        Pattern.compile("(?i)alert\\s*\\(")
    );
    
    // Track upload rates per user
    private final Map<String, UploadRateLimit> uploadRateLimits = new ConcurrentHashMap<>();
    
    /**
     * Comprehensive file upload validation
     */
    public FileValidationResult validateFileUpload(MultipartFile file, String userId, FileUploadContext context) {
        String uploadId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        
        log.debug("Validating file upload: uploadId={}, userId={}, filename={}", 
                uploadId, userId, file.getOriginalFilename());
        
        try {
            // Step 1: Check rate limiting
            if (!checkRateLimit(userId)) {
                auditService.logRateLimitExceeded(userId, file.getOriginalFilename());
                return FileValidationResult.failure("Upload rate limit exceeded");
            }
            
            // Step 2: Validate file metadata
            ValidationResult metadataValidation = validateFileMetadata(file);
            if (!metadataValidation.isValid()) {
                auditService.logValidationFailure(userId, file.getOriginalFilename(), 
                        metadataValidation.getError());
                return FileValidationResult.failure(metadataValidation.getError());
            }
            
            // Step 3: Validate file extension
            ValidationResult extensionValidation = validateFileExtension(file.getOriginalFilename());
            if (!extensionValidation.isValid()) {
                auditService.logValidationFailure(userId, file.getOriginalFilename(), 
                        extensionValidation.getError());
                return FileValidationResult.failure(extensionValidation.getError());
            }
            
            // Step 4: Validate MIME type
            ValidationResult mimeValidation = validateMimeType(file);
            if (!mimeValidation.isValid()) {
                auditService.logValidationFailure(userId, file.getOriginalFilename(), 
                        mimeValidation.getError());
                return FileValidationResult.failure(mimeValidation.getError());
            }
            
            // Step 5: Validate magic bytes
            ValidationResult magicBytesValidation = validateMagicBytes(file);
            if (!magicBytesValidation.isValid()) {
                auditService.logValidationFailure(userId, file.getOriginalFilename(), 
                        magicBytesValidation.getError());
                return FileValidationResult.failure(magicBytesValidation.getError());
            }
            
            // Step 6: Scan file content
            ValidationResult contentValidation = scanFileContent(file);
            if (!contentValidation.isValid()) {
                auditService.logMaliciousContent(userId, file.getOriginalFilename(), 
                        contentValidation.getError());
                quarantineFile(file, userId, "Malicious content detected");
                return FileValidationResult.failure(contentValidation.getError());
            }
            
            // Step 7: Check for archive bombs
            if (isArchiveFile(file)) {
                ValidationResult archiveValidation = validateArchive(file);
                if (!archiveValidation.isValid()) {
                    auditService.logValidationFailure(userId, file.getOriginalFilename(), 
                            archiveValidation.getError());
                    return FileValidationResult.failure(archiveValidation.getError());
                }
            }
            
            // Step 8: Virus scan (if enabled)
            if (virusScanEnabled) {
                ValidationResult virusValidation = performVirusScan(file);
                if (!virusValidation.isValid()) {
                    auditService.logVirusDetected(userId, file.getOriginalFilename(), 
                            virusValidation.getError());
                    quarantineFile(file, userId, "Virus detected");
                    return FileValidationResult.failure(virusValidation.getError());
                }
            }
            
            // Step 9: Save file securely
            String secureFilePath = saveFileSecurely(file, userId, context);
            
            // Step 10: Generate file hash for integrity
            String fileHash = generateFileHash(file);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            auditService.logSuccessfulValidation(userId, file.getOriginalFilename(), 
                    uploadId, processingTime);
            
            return FileValidationResult.success(uploadId, secureFilePath, fileHash);
            
        } catch (Exception e) {
            log.error("File validation error: uploadId={}, userId={}, filename={}", 
                    uploadId, userId, file.getOriginalFilename(), e);
            
            auditService.logValidationError(userId, file.getOriginalFilename(), e.getMessage());
            
            return FileValidationResult.failure("File validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Validate file metadata
     */
    private ValidationResult validateFileMetadata(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ValidationResult.invalid("File is empty");
        }
        
        if (file.getSize() > maxFileSize) {
            return ValidationResult.invalid(
                String.format("File size %d exceeds maximum allowed size of %d bytes", 
                            file.getSize(), maxFileSize));
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            return ValidationResult.invalid("Invalid filename");
        }
        
        if (filename.length() > maxFileNameLength) {
            return ValidationResult.invalid(
                String.format("Filename length %d exceeds maximum allowed length of %d", 
                            filename.length(), maxFileNameLength));
        }
        
        // Check for path traversal attempts
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ValidationResult.invalid("Invalid filename: path traversal detected");
        }
        
        // Check for null bytes
        if (filename.contains("\0")) {
            return ValidationResult.invalid("Invalid filename: null byte detected");
        }
        
        // Check for control characters
        if (containsControlCharacters(filename)) {
            return ValidationResult.invalid("Invalid filename: control characters detected");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validate file extension
     */
    private ValidationResult validateFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return ValidationResult.invalid("File has no extension");
        }
        
        String extension = filename.substring(lastDot + 1).toLowerCase();
        
        // Check for double extensions
        String nameWithoutExt = filename.substring(0, lastDot);
        if (nameWithoutExt.contains(".")) {
            log.warn("File has multiple extensions: {}", filename);
            // Check if any extension is dangerous
            String[] parts = filename.split("\\.");
            for (String part : parts) {
                if (isDangerousExtension(part)) {
                    return ValidationResult.invalid("Dangerous file extension detected");
                }
            }
        }
        
        List<String> allowed = Arrays.asList(allowedExtensions.split(","));
        if (!allowed.contains(extension)) {
            return ValidationResult.invalid(
                String.format("File extension '%s' is not allowed. Allowed extensions: %s", 
                            extension, allowedExtensions));
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validate MIME type using Apache Tika
     */
    private ValidationResult validateMimeType(MultipartFile file) {
        try {
            String detectedType = tika.detect(file.getInputStream(), file.getOriginalFilename());
            
            log.debug("Detected MIME type: {} for file: {}", detectedType, file.getOriginalFilename());
            
            // Map detected MIME type to expected types
            Map<String, List<String>> allowedMimeTypes = new HashMap<>();
            allowedMimeTypes.put("csv", Arrays.asList("text/csv", "text/plain"));
            allowedMimeTypes.put("xlsx", Arrays.asList("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            allowedMimeTypes.put("xls", Arrays.asList("application/vnd.ms-excel"));
            allowedMimeTypes.put("json", Arrays.asList("application/json", "text/json", "text/plain"));
            allowedMimeTypes.put("xml", Arrays.asList("application/xml", "text/xml"));
            allowedMimeTypes.put("txt", Arrays.asList("text/plain"));
            allowedMimeTypes.put("pdf", Arrays.asList("application/pdf"));
            
            String extension = getFileExtension(file.getOriginalFilename());
            List<String> expectedTypes = allowedMimeTypes.get(extension);
            
            if (expectedTypes != null && !expectedTypes.contains(detectedType)) {
                return ValidationResult.invalid(
                    String.format("MIME type mismatch. Expected %s for .%s file, but detected %s", 
                                expectedTypes, extension, detectedType));
            }
            
            return ValidationResult.valid();
            
        } catch (IOException e) {
            log.error("Error detecting MIME type", e);
            return ValidationResult.invalid("Unable to detect MIME type");
        }
    }
    
    /**
     * Validate magic bytes (file signature)
     */
    private ValidationResult validateMagicBytes(MultipartFile file) {
        try {
            String extension = getFileExtension(file.getOriginalFilename());
            byte[] expectedMagic = MAGIC_BYTES.get(extension);
            
            if (expectedMagic == null || expectedMagic.length == 0) {
                // No specific magic bytes for this file type (e.g., CSV, TXT)
                return ValidationResult.valid();
            }
            
            byte[] fileHeader = new byte[expectedMagic.length];
            try (InputStream is = file.getInputStream()) {
                int bytesRead = is.read(fileHeader);
                if (bytesRead < expectedMagic.length) {
                    return ValidationResult.invalid("File is too small");
                }
            }
            
            if (!Arrays.equals(fileHeader, expectedMagic)) {
                return ValidationResult.invalid(
                    String.format("Invalid file signature for .%s file", extension));
            }
            
            return ValidationResult.valid();
            
        } catch (IOException e) {
            log.error("Error validating magic bytes", e);
            return ValidationResult.invalid("Unable to validate file signature");
        }
    }
    
    /**
     * Scan file content for malicious patterns
     */
    private ValidationResult scanFileContent(MultipartFile file) {
        String extension = getFileExtension(file.getOriginalFilename());
        
        // Only scan text-based files
        if (!Arrays.asList("csv", "json", "xml", "txt").contains(extension)) {
            return ValidationResult.valid();
        }
        
        try {
            String content = new String(file.getBytes(), "UTF-8");
            
            // Check for dangerous patterns
            for (Pattern pattern : DANGEROUS_PATTERNS) {
                if (pattern.matcher(content).find()) {
                    return ValidationResult.invalid(
                        "Potentially malicious content detected: " + pattern.pattern());
                }
            }
            
            // Check for binary content in text files
            if (containsBinaryContent(content)) {
                return ValidationResult.invalid("Binary content detected in text file");
            }
            
            return ValidationResult.valid();
            
        } catch (IOException e) {
            log.error("Error scanning file content", e);
            return ValidationResult.invalid("Unable to scan file content");
        }
    }
    
    /**
     * Validate archive files (prevent zip bombs)
     */
    private ValidationResult validateArchive(MultipartFile file) {
        if (!file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
            return ValidationResult.valid();
        }
        
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            int fileCount = 0;
            long totalUncompressedSize = 0;
            final int MAX_FILES = 100;
            final long MAX_UNCOMPRESSED_SIZE = 100 * 1024 * 1024; // 100MB
            final double MAX_COMPRESSION_RATIO = 100.0;
            
            while ((entry = zis.getNextEntry()) != null) {
                fileCount++;
                
                if (fileCount > MAX_FILES) {
                    return ValidationResult.invalid("Archive contains too many files");
                }
                
                // Check for path traversal
                if (entry.getName().contains("..")) {
                    return ValidationResult.invalid("Archive contains path traversal");
                }
                
                long uncompressedSize = entry.getSize();
                long compressedSize = entry.getCompressedSize();
                
                if (uncompressedSize != -1) {
                    totalUncompressedSize += uncompressedSize;
                    
                    if (totalUncompressedSize > MAX_UNCOMPRESSED_SIZE) {
                        return ValidationResult.invalid("Archive uncompressed size too large");
                    }
                    
                    if (compressedSize != -1 && compressedSize > 0) {
                        double ratio = (double) uncompressedSize / compressedSize;
                        if (ratio > MAX_COMPRESSION_RATIO) {
                            return ValidationResult.invalid("Suspicious compression ratio detected");
                        }
                    }
                }
                
                zis.closeEntry();
            }
            
            return ValidationResult.valid();
            
        } catch (IOException e) {
            log.error("Error validating archive", e);
            return ValidationResult.invalid("Unable to validate archive");
        }
    }
    
    /**
     * Perform virus scan (integration point for antivirus)
     */
    private ValidationResult performVirusScan(MultipartFile file) {
        // This is a placeholder for actual virus scanning integration
        // In production, integrate with ClamAV or commercial antivirus API
        
        try {
            // Save file temporarily for scanning
            Path tempFile = Files.createTempFile("scan_", "_" + file.getOriginalFilename());
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            // Integrate with comprehensive virus scanning
            VirusScanResult scanResult = performComprehensiveVirusScan(tempFile);
            
            if (!scanResult.isClean()) {
                log.error("Virus detected in uploaded file: {} - Threats: {}", 
                        file.getOriginalFilename(), scanResult.getThreats());
                
                // Immediately quarantine the file
                quarantineService.quarantineFile(tempFile, scanResult);
                
                // Alert security team
                securityAlertService.alertVirusDetection(
                        file.getOriginalFilename(), 
                        scanResult.getThreats(),
                        getCurrentUserContext());
                
                // Clean up temp file securely
                secureFileCleanup(tempFile);
                
                return ValidationResult.invalid("File contains malicious content: " + 
                        String.join(", ", scanResult.getThreats()));
            }
            
            // Clean up temp file
            Files.deleteIfExists(tempFile);
            
            return ValidationResult.valid();
            
        } catch (IOException e) {
            log.error("Error during virus scan", e);
            return ValidationResult.invalid("Virus scan failed");
        }
    }
    
    /**
     * Save file securely with proper isolation
     */
    private String saveFileSecurely(MultipartFile file, String userId, FileUploadContext context) 
            throws IOException {
        
        // Generate secure filename
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String secureFilename = String.format("%s_%s_%s.%s",
                userId,
                System.currentTimeMillis(),
                UUID.randomUUID().toString().substring(0, 8),
                extension);
        
        // Create user-specific directory
        Path userDir = Path.of(tempUploadPath, userId);
        Files.createDirectories(userDir);
        
        // Save file
        Path filePath = userDir.resolve(secureFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        // Set restrictive permissions (Unix/Linux)
        try {
            Set<java.nio.file.attribute.PosixFilePermission> perms = new HashSet<>();
            perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ);
            perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(filePath, perms);
        } catch (UnsupportedOperationException e) {
            // Not a POSIX system, permissions not set
            log.debug("POSIX permissions not supported on this system");
        }
        
        return filePath.toString();
    }
    
    /**
     * Generate SHA-256 hash of file
     */
    private String generateFileHash(MultipartFile file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        try (InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            hexString.append(String.format("%02x", b));
        }
        
        return hexString.toString();
    }
    
    /**
     * Quarantine suspicious file
     */
    private void quarantineFile(MultipartFile file, String userId, String reason) {
        try {
            Path quarantineDir = Path.of(quarantinePath, userId);
            Files.createDirectories(quarantineDir);
            
            String quarantineFilename = String.format("QUARANTINE_%s_%s_%s",
                    System.currentTimeMillis(),
                    UUID.randomUUID().toString().substring(0, 8),
                    file.getOriginalFilename());
            
            Path quarantineFile = quarantineDir.resolve(quarantineFilename);
            Files.copy(file.getInputStream(), quarantineFile, StandardCopyOption.REPLACE_EXISTING);
            
            // Write quarantine metadata
            Path metadataFile = quarantineDir.resolve(quarantineFilename + ".metadata");
            String metadata = String.format("User: %s\nReason: %s\nTimestamp: %s\nOriginal: %s",
                    userId, reason, new Date(), file.getOriginalFilename());
            Files.writeString(metadataFile, metadata);
            
            log.warn("File quarantined: user={}, file={}, reason={}", 
                    userId, file.getOriginalFilename(), reason);
            
        } catch (IOException e) {
            log.error("Failed to quarantine file", e);
        }
    }
    
    /**
     * Check upload rate limiting
     */
    private boolean checkRateLimit(String userId) {
        UploadRateLimit rateLimit = uploadRateLimits.computeIfAbsent(userId, 
                k -> new UploadRateLimit());
        
        return rateLimit.allowUpload();
    }
    
    // Helper methods
    
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
    
    private boolean containsControlCharacters(String text) {
        for (char c : text.toCharArray()) {
            if (Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r') {
                return true;
            }
        }
        return false;
    }
    
    private boolean containsBinaryContent(String content) {
        int binaryCount = 0;
        for (char c : content.toCharArray()) {
            if (c < 32 && c != '\t' && c != '\n' && c != '\r') {
                binaryCount++;
            }
        }
        // If more than 1% of characters are binary, consider it binary content
        return (double) binaryCount / content.length() > 0.01;
    }
    
    private boolean isDangerousExtension(String extension) {
        List<String> dangerous = Arrays.asList(
            "exe", "dll", "scr", "bat", "cmd", "com", "pif", "vbs", "js", 
            "jar", "sh", "app", "deb", "rpm", "msi", "dmg"
        );
        return dangerous.contains(extension.toLowerCase());
    }
    
    private boolean isArchiveFile(MultipartFile file) {
        String filename = file.getOriginalFilename().toLowerCase();
        return filename.endsWith(".zip") || filename.endsWith(".rar") || 
               filename.endsWith(".7z") || filename.endsWith(".tar") || 
               filename.endsWith(".gz");
    }
    
    // Inner classes
    
    private static class ValidationResult {
        private final boolean valid;
        private final String error;
        
        private ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getError() {
            return error;
        }
    }
    
    public static class FileValidationResult {
        private final boolean valid;
        private final String error;
        private final String uploadId;
        private final String filePath;
        private final String fileHash;
        
        private FileValidationResult(boolean valid, String error, String uploadId, 
                                    String filePath, String fileHash) {
            this.valid = valid;
            this.error = error;
            this.uploadId = uploadId;
            this.filePath = filePath;
            this.fileHash = fileHash;
        }
        
        public static FileValidationResult success(String uploadId, String filePath, String fileHash) {
            return new FileValidationResult(true, null, uploadId, filePath, fileHash);
        }
        
        public static FileValidationResult failure(String error) {
            return new FileValidationResult(false, error, null, null, null);
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public String getError() { return error; }
        public String getUploadId() { return uploadId; }
        public String getFilePath() { return filePath; }
        public String getFileHash() { return fileHash; }
    }
    
    public static class FileUploadContext {
        private String purpose;
        private String category;
        private Map<String, String> metadata;
        
        // Getters and setters
        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    }
    
    private static class UploadRateLimit {
        private static final int MAX_UPLOADS_PER_MINUTE = 10;
        private static final long WINDOW_SIZE_MS = 60000; // 1 minute
        
        private final Queue<Long> uploadTimestamps = new LinkedList<>();
        
        public synchronized boolean allowUpload() {
            long now = System.currentTimeMillis();
            
            // Remove old timestamps outside the window
            while (!uploadTimestamps.isEmpty() && 
                   uploadTimestamps.peek() < now - WINDOW_SIZE_MS) {
                uploadTimestamps.poll();
            }
            
            if (uploadTimestamps.size() >= MAX_UPLOADS_PER_MINUTE) {
                return false;
            }
            
            uploadTimestamps.offer(now);
            return true;
        }
    }
    
    // ==================== VIRUS SCANNING IMPLEMENTATION ====================
    
    /**
     * Perform comprehensive virus scanning using multiple engines
     */
    private VirusScanResult performComprehensiveVirusScan(Path filePath) throws IOException {
        log.info("Performing comprehensive virus scan on file: {}", filePath.getFileName());
        
        List<String> detectedThreats = new ArrayList<>();
        boolean isClean = true;
        
        try {
            // 1. Signature-based scanning
            VirusScanResult signatureResult = performSignatureScan(filePath);
            if (!signatureResult.isClean()) {
                isClean = false;
                detectedThreats.addAll(signatureResult.getThreats());
            }
            
            // 2. Heuristic analysis
            VirusScanResult heuristicResult = performHeuristicAnalysis(filePath);
            if (!heuristicResult.isClean()) {
                isClean = false;
                detectedThreats.addAll(heuristicResult.getThreats());
            }
            
            // 3. Cloud-based scanning (if available)
            if (cloudVirusScanService.isAvailable()) {
                VirusScanResult cloudResult = cloudVirusScanService.scanFile(filePath);
                if (!cloudResult.isClean()) {
                    isClean = false;
                    detectedThreats.addAll(cloudResult.getThreats());
                }
            }
            
            // 4. Machine learning-based detection
            if (mlThreatDetectionService.isEnabled()) {
                VirusScanResult mlResult = mlThreatDetectionService.analyzeFile(filePath);
                if (!mlResult.isClean()) {
                    isClean = false;
                    detectedThreats.addAll(mlResult.getThreats());
                }
            }
            
            return VirusScanResult.builder()
                    .isClean(isClean)
                    .threats(detectedThreats)
                    .scanDuration(System.currentTimeMillis())
                    .scanEngines(List.of("Signature", "Heuristic", "Cloud", "ML"))
                    .build();
                    
        } catch (Exception e) {
            log.error("Error during virus scanning: {}", e.getMessage());
            // Fail secure - treat scan errors as potential threats
            return VirusScanResult.builder()
                    .isClean(false)
                    .threats(List.of("SCAN_ERROR: " + e.getMessage()))
                    .scanDuration(System.currentTimeMillis())
                    .build();
        }
    }
    
    /**
     * Perform signature-based virus scanning
     */
    private VirusScanResult performSignatureScan(Path filePath) throws IOException {
        List<String> threats = new ArrayList<>();
        
        // Check against known malware signatures
        byte[] fileBytes = Files.readAllBytes(filePath);
        String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
        
        // Check for common malware patterns
        List<MalwareSignature> knownSignatures = malwareSignatureDatabase.getAllSignatures();
        
        for (MalwareSignature signature : knownSignatures) {
            if (signature.matches(fileBytes) || signature.matches(fileContent)) {
                threats.add("SIGNATURE_MATCH: " + signature.getName());
                log.warn("Malware signature detected: {} in file: {}", 
                        signature.getName(), filePath.getFileName());
            }
        }
        
        // Check file hash against known malware hashes
        String fileHash = calculateSHA256Hash(fileBytes);
        if (malwareHashDatabase.isKnownMalwareHash(fileHash)) {
            threats.add("KNOWN_MALWARE_HASH: " + fileHash);
            log.warn("Known malware hash detected: {} in file: {}", 
                    fileHash, filePath.getFileName());
        }
        
        return VirusScanResult.builder()
                .isClean(threats.isEmpty())
                .threats(threats)
                .build();
    }
    
    /**
     * Perform heuristic analysis for potential threats
     */
    private VirusScanResult performHeuristicAnalysis(Path filePath) throws IOException {
        List<String> threats = new ArrayList<>();
        
        try {
            byte[] fileBytes = Files.readAllBytes(filePath);
            String filename = filePath.getFileName().toString().toLowerCase();
            
            // Check for suspicious file characteristics
            
            // 1. Executable disguised as document
            if (filename.matches(".*\\.(doc|pdf|jpg|png)$") && 
                containsExecutableHeaders(fileBytes)) {
                threats.add("DISGUISED_EXECUTABLE");
            }
            
            // 2. Multiple file extensions
            if (filename.split("\\.").length > 3) {
                threats.add("MULTIPLE_EXTENSIONS");
            }
            
            // 3. Suspicious embedded content
            if (containsSuspiciousEmbeddedContent(fileBytes)) {
                threats.add("SUSPICIOUS_EMBEDDED_CONTENT");
            }
            
            // 4. Entropy analysis (packed/encrypted content)
            double entropy = calculateFileEntropy(fileBytes);
            if (entropy > 7.5) { // High entropy suggests compression/encryption
                threats.add("HIGH_ENTROPY_CONTENT");
            }
            
            // 5. Suspicious strings/patterns
            String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
            if (containsSuspiciousStrings(fileContent)) {
                threats.add("SUSPICIOUS_STRINGS");
            }
            
            // 6. Script injection patterns
            if (containsScriptInjection(fileContent)) {
                threats.add("SCRIPT_INJECTION");
            }
            
        } catch (Exception e) {
            log.warn("Error during heuristic analysis: {}", e.getMessage());
            threats.add("HEURISTIC_ANALYSIS_ERROR");
        }
        
        return VirusScanResult.builder()
                .isClean(threats.isEmpty())
                .threats(threats)
                .build();
    }
    
    /**
     * Securely clean up temporary files
     */
    private void secureFileCleanup(Path filePath) {
        try {
            if (Files.exists(filePath)) {
                // Overwrite file content before deletion for security
                byte[] randomData = new byte[(int) Files.size(filePath)];
                new SecureRandom().nextBytes(randomData);
                Files.write(filePath, randomData, StandardOpenOption.TRUNCATE_EXISTING);
                
                // Delete the file
                Files.delete(filePath);
                log.debug("Securely cleaned up temporary file: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Error during secure file cleanup: {}", e.getMessage());
        }
    }
    
    /**
     * Get current user context for security alerts
     */
    private UserSecurityContext getCurrentUserContext() {
        // Get current authenticated user details
        String userId = securityContext.getCurrentUserId();
        String sessionId = securityContext.getCurrentSessionId();
        String ipAddress = securityContext.getClientIpAddress();
        String userAgent = securityContext.getUserAgent();
        
        return UserSecurityContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .timestamp(Instant.now())
                .build();
    }
    
    // Helper methods for virus scanning
    
    private String calculateSHA256Hash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    private boolean containsExecutableHeaders(byte[] fileBytes) {
        if (fileBytes.length < 4) return false;
        
        // Check for common executable headers
        String header = new String(fileBytes, 0, Math.min(4, fileBytes.length));
        return header.equals("MZ") || header.equals("PK") || 
               header.startsWith("\u007fELF") || header.equals("CAFE");
    }
    
    private boolean containsSuspiciousEmbeddedContent(byte[] fileBytes) {
        String content = new String(fileBytes, StandardCharsets.UTF_8);
        
        // Check for embedded scripts, macros, or suspicious patterns
        String[] suspiciousPatterns = {
            "javascript:", "vbscript:", "data:text/html",
            "<script", "eval(", "document.write",
            "ActiveXObject", "Shell.Application",
            "WScript.Shell", "powershell", "cmd.exe"
        };
        
        String lowercaseContent = content.toLowerCase();
        for (String pattern : suspiciousPatterns) {
            if (lowercaseContent.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
    
    private double calculateFileEntropy(byte[] data) {
        Map<Byte, Integer> frequency = new HashMap<>();
        
        // Count byte frequencies
        for (byte b : data) {
            frequency.merge(b, 1, Integer::sum);
        }
        
        // Calculate entropy
        double entropy = 0.0;
        for (int count : frequency.values()) {
            double probability = (double) count / data.length;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        
        return entropy;
    }
    
    private boolean containsSuspiciousStrings(String content) {
        String[] suspiciousStrings = {
            "malware", "trojan", "backdoor", "keylogger",
            "ransomware", "cryptolocker", "botnet",
            "exploit", "shellcode", "payload"
        };
        
        String lowercaseContent = content.toLowerCase();
        for (String suspicious : suspiciousStrings) {
            if (lowercaseContent.contains(suspicious)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean containsScriptInjection(String content) {
        String[] injectionPatterns = {
            "<script>", "</script>", "javascript:",
            "onload=", "onerror=", "onclick=",
            "${", "<%", "%>", "<?php"
        };
        
        String lowercaseContent = content.toLowerCase();
        for (String pattern : injectionPatterns) {
            if (lowercaseContent.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
}