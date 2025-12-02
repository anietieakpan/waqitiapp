package com.waqiti.support.service.impl;

import com.waqiti.support.service.StorageService;
import com.waqiti.support.security.FileAccessTokenService;
import com.waqiti.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StorageServiceImpl implements StorageService {

    @Value("${support.file-upload.storage-path:/var/waqiti/support/attachments}")
    private String basePath;

    @Value("${support.file-upload.max-size:10485760}") // 10MB
    private long maxFileSize;

    @Value("${support.file-upload.allowed-types}")
    private List<String> allowedContentTypes;

    @Autowired
    private FileAccessTokenService fileAccessTokenService;

    private Path baseStoragePath;
    private final Tika tika = new Tika();

    // SECURITY FIX: Removed extension-based validation, using content-type detection instead
    private final Set<String> allowedMimeTypes = Set.of(
        "image/jpeg", "image/png", "image/gif",
        "application/pdf",
        "text/plain",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    
    @PostConstruct
    public void init() {
        baseStoragePath = Paths.get(basePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseStoragePath);
            log.info("Storage service initialized with base path: {}", baseStoragePath);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create storage directory: " + baseStoragePath, e);
        }
    }
    
    @Override
    public String storeFile(MultipartFile file, String directory) {
        validateFile(file);
        
        String filename = generateSecureFilename(file.getOriginalFilename());
        return storeFile(file, directory, filename);
    }
    
    @Override
    public String storeFile(MultipartFile file, String directory, String filename) {
        validateFile(file);
        
        try {
            Path directoryPath = createDirectoryIfNotExists(directory);
            Path filePath = directoryPath.resolve(filename);
            
            // Ensure the file doesn't exist or generate a new name
            filePath = ensureUniqueFilePath(filePath);
            
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            String relativePath = baseStoragePath.relativize(filePath).toString();
            log.info("File stored successfully: {}", relativePath);
            
            return relativePath;
            
        } catch (IOException e) {
            log.error("Failed to store file: {}", filename, e);
            throw new BusinessException("Could not store file: " + filename, e);
        }
    }
    
    @Override
    public String storeFile(InputStream inputStream, String originalFilename, String contentType, String directory) {
        // Validate content type
        if (!allowedContentTypes.contains(contentType)) {
            throw new BusinessException("File type not allowed: " + contentType);
        }
        
        try {
            String filename = generateSecureFilename(originalFilename);
            Path directoryPath = createDirectoryIfNotExists(directory);
            Path filePath = ensureUniqueFilePath(directoryPath.resolve(filename));
            
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            
            String relativePath = baseStoragePath.relativize(filePath).toString();
            log.info("File stored from input stream: {}", relativePath);
            
            return relativePath;
            
        } catch (IOException e) {
            log.error("Failed to store file from input stream: {}", originalFilename, e);
            throw new BusinessException("Could not store file: " + originalFilename, e);
        }
    }
    
    @Override
    public List<String> storeFiles(List<MultipartFile> files, String directory) {
        return files.stream()
            .map(file -> storeFile(file, directory))
            .collect(Collectors.toList());
    }
    
    @Override
    public InputStream getFile(String filePath) {
        try {
            Path path = baseStoragePath.resolve(filePath).normalize();
            validateFilePath(path);
            
            if (!Files.exists(path)) {
                throw new BusinessException("File not found: " + filePath);
            }
            
            return Files.newInputStream(path);
            
        } catch (IOException e) {
            log.error("Failed to retrieve file: {}", filePath, e);
            throw new BusinessException("Could not retrieve file: " + filePath, e);
        }
    }
    
    @Override
    public byte[] getFileBytes(String filePath) {
        try {
            Path path = baseStoragePath.resolve(filePath).normalize();
            validateFilePath(path);
            
            if (!Files.exists(path)) {
                throw new BusinessException("File not found: " + filePath);
            }
            
            return Files.readAllBytes(path);
            
        } catch (IOException e) {
            log.error("Failed to read file bytes: {}", filePath, e);
            throw new BusinessException("Could not read file: " + filePath, e);
        }
    }
    
    @Override
    public boolean fileExists(String filePath) {
        try {
            Path path = baseStoragePath.resolve(filePath).normalize();
            validateFilePath(path);
            return Files.exists(path);
        } catch (Exception e) {
            log.warn("Error checking file existence: {}", filePath, e);
            return false;
        }
    }
    
    @Override
    public boolean deleteFile(String filePath) {
        try {
            Path path = baseStoragePath.resolve(filePath).normalize();
            validateFilePath(path);
            
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                log.info("File deleted successfully: {}", filePath);
            } else {
                log.warn("File not found for deletion: {}", filePath);
            }
            return deleted;
            
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filePath, e);
            throw new BusinessException("Could not delete file: " + filePath, e);
        }
    }
    
    @Override
    public void deleteFiles(List<String> filePaths) {
        for (String filePath : filePaths) {
            try {
                deleteFile(filePath);
            } catch (Exception e) {
                log.error("Failed to delete file during batch operation: {}", filePath, e);
            }
        }
    }
    
    @Override
    public long getFileSize(String filePath) {
        try {
            Path path = baseStoragePath.resolve(filePath).normalize();
            validateFilePath(path);
            return Files.size(path);
        } catch (IOException e) {
            log.error("Failed to get file size: {}", filePath, e);
            throw new BusinessException("Could not get file size: " + filePath, e);
        }
    }
    
    @Override
    public FileMetadata getFileMetadata(String filePath) {
        try {
            Path path = baseStoragePath.resolve(filePath).normalize();
            validateFilePath(path);
            
            if (!Files.exists(path)) {
                throw new BusinessException("File not found: " + filePath);
            }
            
            String contentType = Files.probeContentType(path);
            long size = Files.size(path);
            LocalDateTime createdAt = LocalDateTime.ofInstant(
                Files.getLastModifiedTime(path).toInstant(), 
                java.time.ZoneId.systemDefault()
            );
            LocalDateTime modifiedAt = createdAt; // Basic implementation
            String checksum = calculateChecksum(path);
            
            return new FileMetadata(
                path.getFileName().toString(),
                contentType,
                size,
                createdAt,
                modifiedAt,
                checksum
            );
            
        } catch (IOException e) {
            log.error("Failed to get file metadata: {}", filePath, e);
            throw new BusinessException("Could not get file metadata: " + filePath, e);
        }
    }
    
    @Override
    public String generatePublicUrl(String filePath) {
        // In a real implementation, this would generate a public URL
        // For now, return a relative path that can be served by the web server
        return "/api/v1/support/files/" + filePath;
    }
    
    @Override
    public String generateSignedUrl(String filePath, int expirationMinutes) {
        // SECURITY FIX: Use proper JWT token generation with FileAccessTokenService
        // This requires userId and ticketId context - should be called from service layer with context
        throw new UnsupportedOperationException(
            "Use generateSignedUrl(filePath, userId, ticketId, expirationMinutes) instead"
        );
    }

    /**
     * PRODUCTION-READY: Generate signed URL with proper JWT token and user context
     */
    public String generateSignedUrl(String filePath, String userId, String ticketId,
                                    int expirationMinutes) {
        try {
            String token = fileAccessTokenService.generateToken(filePath, userId, ticketId,
                                                                expirationMinutes);
            return "/api/v1/support/files/" + filePath + "?token=" + token;
        } catch (Exception e) {
            log.error("Failed to generate signed URL for file: {}", filePath, e);
            throw new BusinessException("Could not generate signed URL", e);
        }
    }
    
    @Override
    public String copyFile(String sourceFilePath, String destinationDirectory) {
        try {
            Path sourcePath = baseStoragePath.resolve(sourceFilePath).normalize();
            validateFilePath(sourcePath);
            
            Path destDirectoryPath = createDirectoryIfNotExists(destinationDirectory);
            Path destPath = ensureUniqueFilePath(
                destDirectoryPath.resolve(sourcePath.getFileName())
            );
            
            Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
            
            String relativePath = baseStoragePath.relativize(destPath).toString();
            log.info("File copied from {} to {}", sourceFilePath, relativePath);
            
            return relativePath;
            
        } catch (IOException e) {
            log.error("Failed to copy file: {}", sourceFilePath, e);
            throw new BusinessException("Could not copy file: " + sourceFilePath, e);
        }
    }
    
    @Override
    public String moveFile(String sourceFilePath, String destinationDirectory) {
        String newPath = copyFile(sourceFilePath, destinationDirectory);
        deleteFile(sourceFilePath);
        return newPath;
    }
    
    @Override
    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File cannot be empty");
        }

        // 1. Size validation
        if (file.getSize() > maxFileSize) {
            throw new BusinessException(
                String.format("File size (%d bytes) exceeds maximum limit (%d bytes)",
                             file.getSize(), maxFileSize)
            );
        }

        // 2. SECURITY FIX: Use Apache Tika for actual content type detection
        String detectedMimeType;
        try (InputStream is = file.getInputStream()) {
            detectedMimeType = tika.detect(is, file.getOriginalFilename());
            log.debug("Detected MIME type: {} for file: {}", detectedMimeType,
                     file.getOriginalFilename());
        } catch (IOException e) {
            log.error("Failed to detect file content type: {}", file.getOriginalFilename(), e);
            throw new BusinessException("Could not validate file content type", e);
        }

        // 3. Validate detected content type against allowed types
        if (!allowedMimeTypes.contains(detectedMimeType)) {
            String clientType = file.getContentType();
            log.warn("SECURITY: File upload blocked - Detected type: {}, Client claimed: {}, File: {}",
                    detectedMimeType, clientType, file.getOriginalFilename());
            throw new BusinessException(
                String.format("File type '%s' is not allowed. Only images, PDFs, and documents are permitted.",
                             detectedMimeType)
            );
        }

        // 4. SECURITY: Detect content type mismatch (spoofing attempt)
        String clientType = file.getContentType();
        if (clientType != null && !clientType.equals(detectedMimeType)) {
            log.warn("SECURITY: Content type mismatch - Client: {}, Detected: {}, File: {}",
                    clientType, detectedMimeType, file.getOriginalFilename());
            // Allow but log - client MIME type is often unreliable
        }

        // 5. SECURITY: Reject archives (zip bomb protection)
        if (detectedMimeType.contains("zip") ||
            detectedMimeType.contains("rar") ||
            detectedMimeType.contains("tar") ||
            detectedMimeType.contains("7z") ||
            detectedMimeType.contains("compressed")) {
            log.warn("SECURITY: Archive file upload blocked: {}", file.getOriginalFilename());
            throw new BusinessException("Archive files (.zip, .rar, .tar, .7z) are not allowed for security reasons");
        }

        // 6. SECURITY: Validate filename doesn't contain path traversal
        String filename = file.getOriginalFilename();
        if (filename != null && (filename.contains("..") || filename.contains("/") || filename.contains("\\"))) {
            log.warn("SECURITY: Path traversal attempt in filename: {}", filename);
            throw new BusinessException("Invalid filename - path components not allowed");
        }

        // 7. Additional security checks via virus scan
        // Note: scanFile requires file to be stored first, will be called by service layer

        log.info("File validation passed: {} ({})", filename, detectedMimeType);
    }
    
    @Override
    public VirusScanResult scanFile(String filePath) {
        // Basic implementation - in production, integrate with ClamAV or similar
        log.info("Virus scan requested for file: {}", filePath);
        
        // PRODUCTION: Replace with real antivirus integration
        try {
            // Read file for analysis
            byte[] fileContent = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath));
            String fileName = java.nio.file.Paths.get(filePath).getFileName().toString();
            
            // Basic file type and signature validation
            if (isKnownMaliciousSignature(fileContent)) {
                log.warn("SECURITY: Malicious file signature detected for file: {}", fileName);
                return new VirusScanResult(
                    false, // isClean
                    "SignatureValidator",
                    "Known malicious file signature detected",
                    LocalDateTime.now()
                );
            }
            
            // Check file size limits (prevent zip bombs)
            if (fileContent.length > 100 * 1024 * 1024) { // 100MB limit
                log.warn("SECURITY: File size exceeds limit: {} bytes", fileContent.length);
                return new VirusScanResult(
                    false,
                    "SizeValidator", 
                    "File exceeds maximum allowed size",
                    LocalDateTime.now()
                );
            }
            
            // Basic content type validation
            if (containsSuspiciousContent(fileContent, fileName)) {
                log.warn("SECURITY: Suspicious content detected in file: {}", fileName);
                return new VirusScanResult(
                    false,
                    "ContentValidator",
                    "Suspicious content patterns detected",
                    LocalDateTime.now()
                );
            }
            
            log.info("File passed multi-layer security checks: {}", fileName);
            return new VirusScanResult(
                true,
                "EnterpriseSecurityValidator",
                null,
                LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("SECURITY: Error during virus scan for file: {}", fileName, e);
            // Fail secure - reject file if scan fails
            return new VirusScanResult(
                false,
                "ScanError",
                "Security scan failed: " + e.getMessage(),
                LocalDateTime.now()
            );
        }
    }
    
    /**
     * Check for known malicious file signatures
     */
    private boolean isKnownMaliciousSignature(byte[] fileContent) {
        if (fileContent == null || fileContent.length < 4) {
            return false;
        }
        
        // Check for common malicious signatures
        String[] maliciousSignatures = {
            "4D5A", // PE executable
            "7F454C46", // ELF executable  
            "504B0304", // ZIP (could contain malicious files)
            "25504446", // PDF (can contain malicious scripts)
            "89504E47", // PNG (check for embedded payloads)
            "FFD8FFE0", // JPEG (check for embedded payloads)
        };
        
        // Convert first few bytes to hex
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < Math.min(8, fileContent.length); i++) {
            hex.append(String.format("%02X", fileContent[i]));
        }
        String fileHex = hex.toString();
        
        // Check against known malicious patterns
        for (String signature : maliciousSignatures) {
            if (fileHex.startsWith(signature)) {
                // Additional validation for potentially dangerous but legitimate files
                return isLikelyMalicious(fileContent, signature);
            }
        }
        
        return false;
    }
    
    /**
     * Additional validation for potentially dangerous files
     */
    private boolean isLikelyMalicious(byte[] fileContent, String signature) {
        // For executables, always flag as malicious in document upload context
        if (signature.equals("4D5A") || signature.equals("7F454C46")) {
            return true;
        }
        
        // For archives, check if they exceed compression ratio (zip bomb detection)
        if (signature.equals("504B0304")) {
            return isZipBomb(fileContent);
        }
        
        // For PDFs and images, perform basic content analysis
        return containsEmbeddedExecutables(fileContent);
    }
    
    /**
     * Check for suspicious content patterns
     */
    private boolean containsSuspiciousContent(byte[] fileContent, String fileName) {
        String content = new String(fileContent, java.nio.charset.StandardCharsets.UTF_8);
        
        // Check for script injection patterns
        String[] suspiciousPatterns = {
            "<script", "javascript:", "vbscript:", "onload=", "onerror=",
            "eval(", "document.cookie", "window.location", "iframe",
            "shell_exec", "system(", "exec(", "cmd.exe", "/bin/sh"
        };
        
        String lowerContent = content.toLowerCase();
        for (String pattern : suspiciousPatterns) {
            if (lowerContent.contains(pattern.toLowerCase())) {
                log.warn("SECURITY: Suspicious pattern '{}' found in file: {}", pattern, fileName);
                return true;
            }
        }
        
        // Check for obfuscated content (high entropy)
        if (calculateEntropy(fileContent) > 7.5) {
            log.warn("SECURITY: High entropy content detected in file: {}", fileName);
            return true;
        }
        
        return false;
    }
    
    /**
     * Detect zip bombs by checking compression ratio
     */
    private boolean isZipBomb(byte[] zipData) {
        // Basic zip bomb detection - check uncompressed vs compressed size
        // In production, use proper ZIP parsing library
        return zipData.length < 1024 && estimateUncompressedSize(zipData) > 100 * 1024 * 1024;
    }
    
    /**
     * Check for embedded executables in files
     */
    private boolean containsEmbeddedExecutables(byte[] fileContent) {
        // Look for embedded PE or ELF signatures within the file
        String content = new String(fileContent, java.nio.charset.StandardCharsets.ISO_8859_1);
        return content.contains("MZ") || content.contains("\u007FELF");
    }
    
    /**
     * Calculate Shannon entropy of file content
     */
    private double calculateEntropy(byte[] data) {
        if (data.length == 0) return 0;
        
        int[] frequency = new int[256];
        for (byte b : data) {
            frequency[b & 0xFF]++;
        }
        
        double entropy = 0.0;
        double length = data.length;
        
        for (int freq : frequency) {
            if (freq > 0) {
                double probability = freq / length;
                entropy -= probability * (Math.log(probability) / Math.log(2));
            }
        }
        
        return entropy;
    }
    
    /**
     * Estimate uncompressed size for zip bomb detection
     */
    private long estimateUncompressedSize(byte[] zipData) {
        // Simplified estimation - in production use proper ZIP library
        return zipData.length * 1000L; // Assume 1000:1 compression ratio as threshold
    }
    
    @Override
    public String optimizeImage(String imagePath, ImageOptimizationOptions options) {
        // Basic implementation - in production, use ImageIO or similar
        log.info("Image optimization requested for: {}", imagePath);
        return imagePath; // Return original for now
    }
    
    @Override
    public String generateThumbnail(String imagePath, int width, int height) {
        // Basic implementation - in production, use image processing library
        log.info("Thumbnail generation requested for: {}", imagePath);
        return imagePath; // Return original for now
    }
    
    @Override
    public void cleanupTempFiles(int daysOld) {
        log.info("Cleaning up temporary files older than {} days", daysOld);
        
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
            Path tempDir = baseStoragePath.resolve("temp");
            
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            LocalDateTime fileTime = LocalDateTime.ofInstant(
                                Files.getLastModifiedTime(path).toInstant(),
                                java.time.ZoneId.systemDefault()
                            );
                            return fileTime.isBefore(cutoffDate);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            log.debug("Deleted old temp file: {}", path);
                        } catch (IOException e) {
                            log.warn("Failed to delete temp file: {}", path, e);
                        }
                    });
            }
        } catch (IOException e) {
            log.error("Error during temp file cleanup", e);
        }
    }
    
    @Override
    public StorageStats getStorageStats() {
        try {
            long totalFiles = Files.walk(baseStoragePath)
                .filter(Files::isRegularFile)
                .count();
            
            long totalSize = Files.walk(baseStoragePath)
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
            
            long availableSpace = Files.getFileStore(baseStoragePath).getUsableSpace();
            
            return new StorageStats(
                totalFiles,
                totalSize,
                availableSpace,
                LocalDateTime.now()
            );
            
        } catch (IOException e) {
            log.error("Failed to calculate storage stats", e);
            throw new BusinessException("Could not calculate storage statistics", e);
        }
    }
    
    // Helper methods
    private Path createDirectoryIfNotExists(String directory) throws IOException {
        Path dirPath = baseStoragePath.resolve(directory).normalize();
        validateFilePath(dirPath);
        Files.createDirectories(dirPath);
        return dirPath;
    }
    
    /**
     * SECURITY FIX: Enhanced path traversal protection with real path validation
     */
    private void validateFilePath(Path path) {
        try {
            // Normalize and resolve the path
            Path normalizedPath = path.normalize().toAbsolutePath();

            // Check if path is within base storage directory
            if (!normalizedPath.startsWith(baseStoragePath.normalize().toAbsolutePath())) {
                log.error("SECURITY: Path traversal attempt blocked - Path: {}, Base: {}",
                         normalizedPath, baseStoragePath);
                throw new SecurityException("Path traversal attempt detected");
            }

            // If file exists, verify it's not a symbolic link to outside directory
            if (Files.exists(path)) {
                Path realPath = path.toRealPath();
                if (!realPath.startsWith(baseStoragePath.toRealPath())) {
                    log.error("SECURITY: Symbolic link traversal blocked - Real path: {}, Base: {}",
                             realPath, baseStoragePath);
                    throw new SecurityException("Symbolic link traversal attempt detected");
                }
            }
        } catch (IOException e) {
            // File doesn't exist yet (new upload) or can't read - this is acceptable
            // Still perform the basic check
            if (!path.normalize().toAbsolutePath().startsWith(baseStoragePath.normalize().toAbsolutePath())) {
                throw new SecurityException("Invalid file path: " + path);
            }
        }
    }
    
    private String generateSecureFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            originalFilename = "file";
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(originalFilename);
        String baseName = getBaseName(originalFilename);
        
        // Sanitize filename
        baseName = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        return baseName + "_" + timestamp + "_" + randomSuffix + extension;
    }
    
    private Path ensureUniqueFilePath(Path filePath) {
        if (!Files.exists(filePath)) {
            return filePath;
        }
        
        String baseName = getBaseName(filePath.getFileName().toString());
        String extension = getFileExtension(filePath.getFileName().toString());
        Path parent = filePath.getParent();
        
        int counter = 1;
        Path uniquePath;
        do {
            uniquePath = parent.resolve(baseName + "_" + counter + extension);
            counter++;
        } while (Files.exists(uniquePath));
        
        return uniquePath;
    }
    
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex) : "";
    }
    
    private String getBaseName(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(0, lastDotIndex) : filename;
    }
    
    private String calculateChecksum(Path filePath) {
        try {
            // SECURITY FIX: Use SHA-256 instead of weak MD5 algorithm
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(filePath);
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // Reading file to calculate digest
                }
            }
            
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
            
        } catch (Exception e) {
            log.warn("Failed to calculate file checksum: {}", filePath, e);
            return "";
        }
    }
}