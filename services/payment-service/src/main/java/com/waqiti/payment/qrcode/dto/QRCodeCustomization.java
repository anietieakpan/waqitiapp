package com.waqiti.payment.qrcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * QR Code customization options for enhanced visual appearance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QRCodeCustomization {
    
    // Logo customization
    private String logoPath;
    private int logoSize;
    private boolean includeLogo;
    
    // Color customization
    private String foregroundColor;
    private String backgroundColor;
    private String gradientStartColor;
    private String gradientEndColor;
    
    // Border customization
    private int borderRadius;
    private String borderColor;
    private int borderWidth;
    
    // Text customization
    private String fontFamily;
    private int fontSize;
    private boolean includeAmount;
    private boolean includeMerchantName;
    private String labelText;
    
    // Template
    private String templateId;
    
    // Custom fields
    private Map<String, String> customFields;
    
    // Accessibility
    private boolean highContrast;
    private String altText;
}