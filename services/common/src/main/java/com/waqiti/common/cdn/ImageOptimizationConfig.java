package com.waqiti.common.cdn;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration for image optimization
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageOptimizationConfig {
    
    /**
     * Target quality for JPEG compression (0-100)
     */
    @Builder.Default
    private int jpegQuality = 85;
    
    /**
     * Target quality for WebP compression (0-100)
     */
    @Builder.Default
    private int webpQuality = 85;
    
    /**
     * Whether to convert to WebP format
     */
    @Builder.Default
    private boolean convertToWebP = true;
    
    /**
     * Whether to strip metadata
     */
    @Builder.Default
    private boolean stripMetadata = true;
    
    /**
     * Whether to enable progressive encoding
     */
    @Builder.Default
    private boolean progressive = true;
    
    /**
     * Maximum width in pixels
     */
    private Integer maxWidth;
    
    /**
     * Maximum height in pixels
     */
    private Integer maxHeight;
    
    /**
     * Whether to maintain aspect ratio
     */
    @Builder.Default
    private boolean maintainAspectRatio = true;
    
    /**
     * Resize mode
     */
    @Builder.Default
    private ResizeMode resizeMode = ResizeMode.FIT;
    
    /**
     * Responsive image sizes to generate
     */
    private List<ResponsiveSize> responsiveSizes;
    
    /**
     * Image formats to generate
     */
    @Builder.Default
    private List<ImageFormat> outputFormats = List.of(ImageFormat.WEBP, ImageFormat.JPEG);
    
    /**
     * Whether to enable lazy loading hints
     */
    @Builder.Default
    private boolean enableLazyLoading = true;
    
    /**
     * Sharpening amount (0-100)
     */
    @Builder.Default
    private int sharpenAmount = 20;
    
    /**
     * Whether to optimize for mobile
     */
    @Builder.Default
    private boolean optimizeForMobile = true;
    
    public enum ResizeMode {
        FIT,        // Fit within bounds maintaining aspect ratio
        FILL,       // Fill the bounds, cropping if necessary
        STRETCH,    // Stretch to fill bounds exactly
        PAD,        // Pad to fill bounds with background
        SMART_CROP  // AI-based smart cropping
    }
    
    public enum ImageFormat {
        JPEG,
        PNG,
        WEBP,
        AVIF,
        GIF,
        SVG
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponsiveSize {
        private String name;
        private int width;
        private int height;
        private String suffix;
        private int quality;
    }
}