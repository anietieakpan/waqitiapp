package com.waqiti.support.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public interface StorageService {
    
    /**
     * Store a file and return the storage path
     */
    String storeFile(MultipartFile file, String directory);
    
    /**
     * Store a file with a specific filename
     */
    String storeFile(MultipartFile file, String directory, String filename);
    
    /**
     * Store a file from input stream
     */
    String storeFile(InputStream inputStream, String originalFilename, String contentType, String directory);
    
    /**
     * Store multiple files
     */
    List<String> storeFiles(List<MultipartFile> files, String directory);
    
    /**
     * Retrieve a file as input stream
     */
    InputStream getFile(String filePath);
    
    /**
     * Get file as byte array
     */
    byte[] getFileBytes(String filePath);
    
    /**
     * Check if file exists
     */
    boolean fileExists(String filePath);
    
    /**
     * Delete a file
     */
    boolean deleteFile(String filePath);
    
    /**
     * Delete multiple files
     */
    void deleteFiles(List<String> filePaths);
    
    /**
     * Get file size
     */
    long getFileSize(String filePath);
    
    /**
     * Get file metadata
     */
    FileMetadata getFileMetadata(String filePath);
    
    /**
     * Generate a public URL for file access
     */
    String generatePublicUrl(String filePath);
    
    /**
     * Generate a temporary signed URL for file access
     */
    String generateSignedUrl(String filePath, int expirationMinutes);
    
    /**
     * Copy file to another location
     */
    String copyFile(String sourceFilePath, String destinationDirectory);
    
    /**
     * Move file to another location
     */
    String moveFile(String sourceFilePath, String destinationDirectory);
    
    /**
     * Validate file type and size
     */
    void validateFile(MultipartFile file);
    
    /**
     * Scan file for viruses (if antivirus is enabled)
     */
    VirusScanResult scanFile(String filePath);
    
    /**
     * Compress/optimize image files
     */
    String optimizeImage(String imagePath, ImageOptimizationOptions options);
    
    /**
     * Generate thumbnail for image
     */
    String generateThumbnail(String imagePath, int width, int height);
    
    /**
     * Clean up temporary files older than specified days
     */
    void cleanupTempFiles(int daysOld);
    
    /**
     * Get storage statistics
     */
    StorageStats getStorageStats();
    
    // Nested classes for metadata and options
    record FileMetadata(
        String fileName,
        String contentType, 
        long size,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime modifiedAt,
        String checksum
    ) {}
    
    record VirusScanResult(
        boolean isClean,
        String scanEngine,
        String threatName,
        java.time.LocalDateTime scannedAt
    ) {}
    
    record ImageOptimizationOptions(
        int quality,
        int maxWidth,
        int maxHeight,
        String format
    ) {}
    
    record StorageStats(
        long totalFiles,
        long totalSizeBytes,
        long availableSpaceBytes,
        java.time.LocalDateTime lastCalculated
    ) {}
}