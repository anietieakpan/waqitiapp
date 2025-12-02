package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.ShippingAddress;
import com.waqiti.virtualcard.domain.enums.ReplacementReason;
import com.waqiti.virtualcard.domain.enums.ShippingMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for replacing a physical card
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplaceCardRequest {
    
    @NotNull(message = "Replacement reason is required")
    private ReplacementReason reason;
    
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;
    
    @Valid
    private ShippingAddress shippingAddress; // Optional - uses last address if not provided
    
    private ShippingMethod shippingMethod; // Optional - defaults to standard
    
    @Builder.Default
    private boolean rushDelivery = false;
    
    private String policeReportNumber; // For lost/stolen cards
    
    private String incidentLocation; // Where the card was lost/stolen
    
    private String contactPhone; // Emergency contact number
    
    @Size(max = 500, message = "Additional notes cannot exceed 500 characters")
    private String additionalNotes;
    
    /**
     * Validates the replacement request
     */
    public boolean isValid() {
        if (reason == null) {
            return false;
        }
        
        // For lost/stolen cards, require additional details
        if ((reason == ReplacementReason.LOST || reason == ReplacementReason.STOLEN)) {
            if (description == null || description.trim().isEmpty()) {
                return false;
            }
        }
        
        // Validate shipping address if provided
        if (shippingAddress != null && !shippingAddress.isValid()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if this is a security-related replacement
     */
    public boolean isSecurityRelated() {
        return reason == ReplacementReason.LOST || 
               reason == ReplacementReason.STOLEN;
    }
    
    /**
     * Checks if replacement should be expedited
     */
    public boolean shouldExpedite() {
        return isSecurityRelated() || 
               reason == ReplacementReason.DEFECTIVE ||
               rushDelivery;
    }
    
    /**
     * Gets effective shipping method with rush delivery consideration
     */
    public ShippingMethod getEffectiveShippingMethod() {
        if (rushDelivery) {
            return ShippingMethod.EXPRESS;
        }
        return shippingMethod != null ? shippingMethod : ShippingMethod.STANDARD;
    }
    
    /**
     * Checks if police report is required
     */
    public boolean requiresPoliceReport() {
        return reason == ReplacementReason.STOLEN;
    }
    
    /**
     * Gets the incident summary for reporting
     */
    public String getIncidentSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Card replacement requested - Reason: ").append(reason.toString());
        
        if (description != null && !description.trim().isEmpty()) {
            summary.append(", Description: ").append(description);
        }
        
        if (incidentLocation != null && !incidentLocation.trim().isEmpty()) {
            summary.append(", Location: ").append(incidentLocation);
        }
        
        if (policeReportNumber != null && !policeReportNumber.trim().isEmpty()) {
            summary.append(", Police Report: ").append(policeReportNumber);
        }
        
        return summary.toString();
    }
}