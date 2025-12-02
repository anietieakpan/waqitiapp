package com.waqiti.common.cdn.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Metadata for CDN objects
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectMetadata {
    
    /**
     * Object key/path
     */
    private String key;
    
    /**
     * Content type
     */
    private String contentType;
    
    /**
     * Content length in bytes
     */
    private long contentLength;
    
    /**
     * ETag/MD5 hash
     */
    private String etag;
    
    /**
     * Last modified timestamp
     */
    private Instant lastModified;
    
    /**
     * Cache control header
     */
    private String cacheControl;
    
    /**
     * Content encoding
     */
    private String contentEncoding;
    
    /**
     * Content disposition
     */
    private String contentDisposition;
    
    /**
     * Storage class
     */
    private String storageClass;
    
    /**
     * Server side encryption
     */
    private String serverSideEncryption;
    
    /**
     * Version ID
     */
    private String versionId;
    
    /**
     * Custom metadata
     */
    private Map<String, String> customMetadata;
    
    /**
     * Access control list
     */
    private String acl;
    
    /**
     * Expiration time
     */
    private Instant expirationTime;
    
    /**
     * Website redirect location
     */
    private String websiteRedirectLocation;
    
    /**
     * Replication status
     */
    private String replicationStatus;
    
    /**
     * Tags
     */
    private Map<String, String> tags;
}