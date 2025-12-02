package com.waqiti.payment.qrcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * QR Code template for reusable designs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QRCodeTemplate {
    
    private UUID id;
    private String name;
    private String description;
    private String category;
    
    // Design settings
    private QRCodeCustomization customization;
    
    // Layout settings
    private LayoutType layoutType;
    private String layoutConfig;
    
    // Branding
    private String brandName;
    private String brandLogoUrl;
    private Map<String, String> brandColors;
    
    // Usage restrictions
    private boolean isPublic;
    private UUID ownerId;
    private String ownerType; // MERCHANT, SYSTEM, USER
    
    // Metadata
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean active;
    
    // Statistics
    private long usageCount;
    private double averageRating;
    
    public enum LayoutType {
        STANDARD,
        COMPACT,
        DETAILED,
        BRANDED,
        MINIMALIST,
        CUSTOM
    }
}