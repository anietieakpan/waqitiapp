package com.waqiti.common.cdn;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * CDN cache configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdnCacheConfig {
    
    /**
     * Cache control header value
     */
    @Builder.Default
    private String cacheControl = "public, max-age=86400";
    
    /**
     * Time to live in seconds
     */
    @Builder.Default
    private long ttlSeconds = 86400; // 24 hours
    
    /**
     * Whether to enable browser caching
     */
    @Builder.Default
    private boolean enableBrowserCache = true;
    
    /**
     * Whether to enable CDN edge caching
     */
    @Builder.Default
    private boolean enableEdgeCache = true;
    
    /**
     * Cache behavior type
     */
    @Builder.Default
    private CacheBehavior behavior = CacheBehavior.CACHE_OPTIMIZED;
    
    /**
     * Custom cache headers
     */
    private Map<String, String> customHeaders;
    
    /**
     * Cache key parameters
     */
    private CacheKeyParameters cacheKeyParameters;
    
    /**
     * Vary headers for cache key
     */
    private String[] varyHeaders;
    
    /**
     * Whether to compress objects automatically
     */
    @Builder.Default
    private boolean compress = true;
    
    /**
     * Minimum file size for compression (bytes)
     */
    @Builder.Default
    private long compressionMinSize = 1000;
    
    /**
     * Get max age from cache control or TTL
     */
    public long getMaxAge() {
        return ttlSeconds;
    }
    
    /**
     * Get shared max age (s-maxage)
     */
    public long getSMaxAge() {
        return ttlSeconds; // Default to same as max-age
    }
    
    public enum CacheBehavior {
        CACHE_OPTIMIZED,
        CACHE_DISABLED,
        CACHE_AND_ORIGIN_REQUEST_POLICY,
        USE_ORIGIN_CACHE_HEADERS,
        CUSTOM
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheKeyParameters {
        private boolean includeQueryStrings;
        private boolean includeCookies;
        private boolean includeHeaders;
        private String[] queryStringWhitelist;
        private String[] cookieWhitelist;
        private String[] headerWhitelist;
    }
}