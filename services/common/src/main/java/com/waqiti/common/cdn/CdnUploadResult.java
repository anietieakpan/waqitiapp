package com.waqiti.common.cdn;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Result of a CDN upload operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdnUploadResult {
    
    public static class CdnUploadResultBuilder {
        public CdnUploadResultBuilder s3Key(String s3Key) {
            // Map s3Key to s3Url
            this.s3Url = "s3://bucket/" + s3Key;
            return this;
        }
        
        public CdnUploadResultBuilder error(String error) {
            this.errorMessage = error;
            this.success = false;
            return this;
        }
        
        public CdnUploadResultBuilder fileName(String fileName) {
            // Store in metadata
            if (this.metadata == null) {
                this.metadata = new java.util.HashMap<>();
            }
            this.metadata.put("fileName", fileName);
            return this;
        }
        
        public CdnUploadResultBuilder fileSize(int fileSize) {
            this.sizeBytes = fileSize;
            return this;
        }
        
        public CdnUploadResultBuilder variant(String variant) {
            // Store variant information in metadata
            if (this.metadata == null) {
                this.metadata = new java.util.HashMap<>();
            }
            this.metadata.put("variant", variant);
            return this;
        }
    }
    
    /**
     * Unique identifier for the uploaded content
     */
    private String contentId;
    
    /**
     * CDN URL where the content can be accessed
     */
    private String cdnUrl;
    
    /**
     * Direct S3 URL (for internal use)
     */
    private String s3Url;
    
    /**
     * CloudFront distribution ID
     */
    private String distributionId;
    
    /**
     * Size of the uploaded content in bytes
     */
    private long sizeBytes;
    
    /**
     * MIME type of the content
     */
    private String contentType;
    
    /**
     * ETag of the uploaded object
     */
    private String etag;
    
    /**
     * Time when the upload was completed
     */
    private Instant uploadTime;
    
    /**
     * Whether the upload was successful
     */
    private boolean success;
    
    /**
     * Error message if upload failed
     */
    private String errorMessage;
    
    /**
     * Cache control headers applied
     */
    private String cacheControl;
    
    /**
     * Time to live in seconds
     */
    private long ttlSeconds;
    
    /**
     * Whether the content was compressed
     */
    private boolean compressed;
    
    /**
     * Compression type if compressed
     */
    private String compressionType;
    
    /**
     * CloudFront invalidation ID if invalidation was triggered
     */
    private String invalidationId;
    
    /**
     * Additional metadata
     */
    private Map<String, String> metadata;
    
    /**
     * Optimization results if content was optimized
     */
    private OptimizationResult optimizationResult;
    
    /**
     * List of variant uploads (e.g., different sizes/formats)
     */
    private java.util.List<CdnUploadResult> variants;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationResult {
        private long originalSize;
        private long optimizedSize;
        private double compressionRatio;
        private String optimizationType;
        private Map<String, Object> details;
    }
}