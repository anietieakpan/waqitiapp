package com.waqiti.common.metrics.abstraction;

import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Production-Grade Tag Set
 * Immutable, validated set of metric tags with normalization
 */
@Slf4j
public class TagSet {
    
    private final Map<String, String> tags;
    private final String cacheKey;
    private final Tags micrometerTags;
    
    // Tag normalization cache for performance
    private static final Map<String, String> NORMALIZATION_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 10000;
    
    private TagSet(Map<String, String> tags) {
        this.tags = Collections.unmodifiableMap(new LinkedHashMap<>(tags));
        this.cacheKey = computeCacheKey();
        this.micrometerTags = createMicrometerTags();
    }
    
    /**
     * Builder for creating TagSets
     */
    public static class Builder {
        private final Map<String, String> tags = new LinkedHashMap<>();
        private final TagConstraints constraints;
        
        public Builder() {
            this.constraints = TagConstraints.DEFAULT;
        }
        
        public Builder(TagConstraints constraints) {
            this.constraints = constraints;
        }
        
        public Builder tag(String key, String value) {
            // Validate and normalize
            String normalizedKey = normalizeTagKey(key, constraints);
            String normalizedValue = normalizeTagValue(value, constraints);
            
            // Check constraints
            if (!constraints.isAllowedTag(normalizedKey)) {
                if (constraints.isStrict()) {
                    throw new IllegalArgumentException("Tag not allowed: " + key);
                }
                log.debug("Dropping disallowed tag: {}", key);
                return this;
            }
            
            tags.put(normalizedKey, normalizedValue);
            return this;
        }
        
        public Builder tag(String key, Enum<?> value) {
            return tag(key, value != null ? value.name().toLowerCase() : "unknown");
        }
        
        public Builder tag(String key, Number value) {
            return tag(key, value != null ? value.toString() : "0");
        }
        
        public Builder tag(String key, boolean value) {
            return tag(key, String.valueOf(value));
        }
        
        public Builder tags(Map<String, String> tags) {
            tags.forEach(this::tag);
            return this;
        }
        
        public TagSet build() {
            // Apply max tags constraint
            if (tags.size() > constraints.getMaxTags()) {
                log.warn("Tag count {} exceeds max {}, truncating", tags.size(), constraints.getMaxTags());
                Map<String, String> truncated = tags.entrySet().stream()
                    .limit(constraints.getMaxTags())
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                    ));
                return new TagSet(truncated);
            }
            
            return new TagSet(tags);
        }
    }
    
    /**
     * Create a new builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create a new builder with constraints
     */
    public static Builder builder(TagConstraints constraints) {
        return new Builder(constraints);
    }
    
    /**
     * Create an empty tag set
     */
    public static TagSet empty() {
        return new TagSet(Collections.emptyMap());
    }
    
    /**
     * Create a tag set from a single tag
     */
    public static TagSet of(String key, String value) {
        return builder().tag(key, value).build();
    }
    
    /**
     * Create a tag set from two tags
     */
    public static TagSet of(String k1, String v1, String k2, String v2) {
        return builder().tag(k1, v1).tag(k2, v2).build();
    }
    
    /**
     * Create a tag set from three tags
     */
    public static TagSet of(String k1, String v1, String k2, String v2, String k3, String v3) {
        return builder()
            .tag(k1, v1)
            .tag(k2, v2)
            .tag(k3, v3)
            .build();
    }
    
    /**
     * Normalize tag key
     */
    private static String normalizeTagKey(String key, TagConstraints constraints) {
        if (key == null) return "unknown";
        
        String cacheKey = "key:" + key;
        String normalized = NORMALIZATION_CACHE.get(cacheKey);
        if (normalized != null) {
            return normalized;
        }
        
        // Normalize: lowercase, replace invalid chars
        normalized = key.toLowerCase()
            .replaceAll("[^a-z0-9_.]", "_")
            .replaceAll("_{2,}", "_")
            .replaceAll("^_|_$", "");
            
        if (normalized.isEmpty()) {
            normalized = "unknown";
        }
        
        // Limit length
        if (normalized.length() > constraints.getMaxTagKeyLength()) {
            normalized = normalized.substring(0, constraints.getMaxTagKeyLength());
        }
        
        // Cache if not full
        if (NORMALIZATION_CACHE.size() < MAX_CACHE_SIZE) {
            NORMALIZATION_CACHE.put(cacheKey, normalized);
        }
        
        return normalized;
    }
    
    /**
     * Normalize tag value
     */
    private static String normalizeTagValue(String value, TagConstraints constraints) {
        if (value == null) return "unknown";
        
        String cacheKey = "val:" + value;
        String normalized = NORMALIZATION_CACHE.get(cacheKey);
        if (normalized != null) {
            return normalized;
        }
        
        // Normalize: lowercase, replace invalid chars
        normalized = value.toLowerCase()
            .replaceAll("[^a-z0-9_.-]", "_")
            .replaceAll("_{2,}", "_")
            .replaceAll("^_|_$", "");
            
        if (normalized.isEmpty()) {
            normalized = "unknown";
        }
        
        // Limit length
        if (normalized.length() > constraints.getMaxTagValueLength()) {
            normalized = normalized.substring(0, constraints.getMaxTagValueLength());
        }
        
        // Apply value constraints
        normalized = constraints.normalizeValue(normalized);
        
        // Cache if not full
        if (NORMALIZATION_CACHE.size() < MAX_CACHE_SIZE) {
            NORMALIZATION_CACHE.put(cacheKey, normalized);
        }
        
        return normalized;
    }
    
    /**
     * Compute cache key for this tag set
     */
    private String computeCacheKey() {
        return tags.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(","));
    }
    
    /**
     * Create Micrometer Tags
     */
    private Tags createMicrometerTags() {
        Tags result = Tags.empty();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            result = result.and(entry.getKey(), entry.getValue());
        }
        return result;
    }
    
    /**
     * Get tag value
     */
    public String getTag(String key) {
        return tags.get(normalizeTagKey(key, TagConstraints.DEFAULT));
    }
    
    /**
     * Check if tag exists
     */
    public boolean hasTag(String key) {
        return tags.containsKey(normalizeTagKey(key, TagConstraints.DEFAULT));
    }
    
    /**
     * Get all tags
     */
    public Map<String, String> getTags() {
        return tags;
    }
    
    /**
     * Get cache key
     */
    public String toCacheKey() {
        return cacheKey;
    }
    
    /**
     * Convert to Micrometer Tags
     */
    public Tags toMicrometerTags() {
        return micrometerTags;
    }
    
    /**
     * Get tag count
     */
    public int size() {
        return tags.size();
    }
    
    /**
     * Check if empty
     */
    public boolean isEmpty() {
        return tags.isEmpty();
    }
    
    /**
     * Merge with another tag set
     */
    public TagSet merge(TagSet other) {
        Builder builder = builder();
        builder.tags(this.tags);
        builder.tags(other.tags);
        return builder.build();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagSet tagSet = (TagSet) o;
        return Objects.equals(tags, tagSet.tags);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(tags);
    }
    
    @Override
    public String toString() {
        return "TagSet{" + cacheKey + "}";
    }
}