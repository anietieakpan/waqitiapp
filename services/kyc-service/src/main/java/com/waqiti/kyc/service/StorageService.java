package com.waqiti.kyc.service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Service interface for managing file storage (S3, local, etc.)
 */
public interface StorageService {
    
    /**
     * Upload a file to storage
     * @param path The storage path
     * @param data The file data
     * @param contentType The content type
     * @return The storage URL
     */
    String uploadFile(String path, byte[] data, String contentType);
    
    /**
     * Upload a file from input stream
     * @param path The storage path
     * @param inputStream The input stream
     * @param contentLength The content length
     * @param contentType The content type
     * @return The storage URL
     */
    String uploadFile(String path, InputStream inputStream, long contentLength, String contentType);
    
    /**
     * Download a file from storage
     * @param path The storage path
     * @return The file data
     */
    byte[] downloadFile(String path);
    
    /**
     * Download a file as input stream
     * @param path The storage path
     * @return The input stream
     */
    InputStream downloadFileStream(String path);
    
    /**
     * Delete a file from storage
     * @param path The storage path
     */
    void deleteFile(String path);
    
    /**
     * Check if a file exists
     * @param path The storage path
     * @return true if the file exists
     */
    boolean fileExists(String path);
    
    /**
     * Generate a presigned URL for temporary access
     * @param path The storage path
     * @param expiryMinutes The expiry time in minutes
     * @return The presigned URL
     */
    String generatePresignedUrl(String path, int expiryMinutes);
    
    /**
     * Copy a file within storage
     * @param sourcePath The source path
     * @param destinationPath The destination path
     */
    void copyFile(String sourcePath, String destinationPath);
    
    /**
     * Move a file within storage
     * @param sourcePath The source path
     * @param destinationPath The destination path
     */
    void moveFile(String sourcePath, String destinationPath);
    
    /**
     * Get file metadata
     * @param path The storage path
     * @return File metadata
     */
    Map<String, String> getFileMetadata(String path);
    
    /**
     * Update file metadata
     * @param path The storage path
     * @param metadata The metadata to update
     */
    void updateFileMetadata(String path, Map<String, String> metadata);
    
    /**
     * List files in a directory
     * @param prefix The directory prefix
     * @return List of file paths
     */
    List<String> listFiles(String prefix);
    
    /**
     * Get file size
     * @param path The storage path
     * @return The file size in bytes
     */
    long getFileSize(String path);
    
    /**
     * Create a directory
     * @param path The directory path
     */
    void createDirectory(String path);
    
    /**
     * Delete a directory and all its contents
     * @param path The directory path
     */
    void deleteDirectory(String path);
}