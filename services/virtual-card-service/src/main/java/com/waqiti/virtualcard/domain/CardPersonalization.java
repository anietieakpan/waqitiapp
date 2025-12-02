package com.waqiti.virtualcard.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Card Personalization embedded entity for custom card details
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardPersonalization {
    
    @Column(name = "cardholder_name", nullable = false)
    private String cardholderName;
    
    @Column(name = "custom_text")
    private String customText;
    
    @Column(name = "font_style")
    private String fontStyle;
    
    @Column(name = "name_position")
    private String namePosition;
    
    @Column(name = "embossed")
    @Builder.Default
    private boolean embossed = true;
    
    @Column(name = "signature_panel")
    @Builder.Default
    private boolean signaturePanel = true;
    
    @Column(name = "special_characters")
    @Builder.Default
    private boolean specialCharacters = false;
    
    @Column(name = "rush_delivery")
    @Builder.Default
    private boolean rushDelivery = false;
    
    @Column(name = "gift_message", length = 500)
    private String giftMessage;
    
    @Column(name = "personalization_notes", length = 1000)
    private String personalizationNotes;
    
    /**
     * Validates the personalization data
     */
    public boolean isValid() {
        return cardholderName != null && 
               !cardholderName.trim().isEmpty() &&
               cardholderName.length() <= 26 && // Standard card name limit
               (customText == null || customText.length() <= 20) &&
               (giftMessage == null || giftMessage.length() <= 500);
    }
    
    /**
     * Sanitizes the cardholder name for card production
     */
    public String getSanitizedCardholderName() {
        if (cardholderName == null) {
            return "";
        }
        
        return cardholderName.trim()
            .toUpperCase()
            .replaceAll("[^A-Z0-9\\s\\-'./]", "") // Remove special chars except allowed ones
            .replaceAll("\\s+", " ") // Normalize spaces
            .substring(0, Math.min(cardholderName.length(), 26)); // Truncate if needed
    }
}