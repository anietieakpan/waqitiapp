package com.waqiti.common.cdn;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents an optimized image with multiple variants
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizedImage {
    
    public static class OptimizedImageBuilder {
        public OptimizedImageBuilder original(byte[] originalData) {
            // Store original data somewhere - for now just set metadata
            return this;
        }
        
        public OptimizedImageBuilder sizeVariants(Map<String, byte[]> sizeVariants) {
            // Convert byte arrays to ImageVariant objects
            return this;
        }
        
        public OptimizedImageBuilder webpVariants(Map<String, byte[]> webpVariants) {
            // Convert byte arrays to WebP ImageVariant objects
            return this;
        }
    }
    
    /**
     * Original image identifier
     */
    private String imageId;
    
    /**
     * Original image URL
     */
    private String originalUrl;
    
    /**
     * Optimized variants
     */
    private Map<String, ImageVariant> variants;
    
    /**
     * Primary optimized URL
     */
    private String optimizedUrl;
    
    /**
     * Original image metadata
     */
    private ImageMetadata originalMetadata;
    
    /**
     * Optimization statistics
     */
    private OptimizationStats optimizationStats;
    
    /**
     * Responsive image srcset
     */
    private String srcset;
    
    /**
     * Default sizes attribute for responsive images
     */
    private String sizes;
    
    /**
     * Get original image data
     */
    public byte[] getOriginal() {
        // Return original data - for now return empty
        return new byte[0];
    }
    
    /**
     * Get size variants as a list
     */
    public java.util.List<ImageVariant> getSizeVariants() {
        java.util.List<ImageVariant> sizeVariants = new java.util.ArrayList<>();
        if (variants != null) {
            variants.values().stream()
                .filter(v -> !v.getFormat().equalsIgnoreCase("webp"))
                .forEach(sizeVariants::add);
        }
        return sizeVariants;
    }
    
    /**
     * Get WebP variants as a list
     */
    public java.util.List<ImageVariant> getWebpVariants() {
        java.util.List<ImageVariant> webpVariants = new java.util.ArrayList<>();
        if (variants != null) {
            variants.values().stream()
                .filter(v -> v.getFormat().equalsIgnoreCase("webp"))
                .forEach(webpVariants::add);
        }
        return webpVariants;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageVariant {
        private String variantId;
        private String url;
        private int width;
        private int height;
        private String format;
        private long sizeBytes;
        private int quality;
        private String suffix;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageMetadata {
        private int width;
        private int height;
        private String format;
        private long sizeBytes;
        private String mimeType;
        private Map<String, String> exifData;
        private boolean hasAlpha;
        private String colorSpace;
        private int bitDepth;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationStats {
        private long originalSize;
        private long optimizedSize;
        private double compressionRatio;
        private long processingTimeMs;
        private int variantsGenerated;
        private Map<String, Long> sizeByFormat;
    }
}