package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.enums.ReplacementReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request DTO for reporting a card as lost or stolen
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportLostStolenRequest {
    
    @NotNull(message = "Reason is required")
    private ReplacementReason reason; // LOST or STOLEN only
    
    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 1000, message = "Description must be between 10 and 1000 characters")
    private String description;
    
    @Size(max = 200, message = "Location cannot exceed 200 characters")
    private String location;
    
    private Instant incidentTime;
    
    @Pattern(regexp = "^[A-Z0-9-]*$", message = "Police report number must contain only uppercase letters, numbers, and hyphens")
    @Size(max = 50, message = "Police report number cannot exceed 50 characters")
    private String policeReportNumber;
    
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String emergencyContactPhone;
    
    @Size(max = 100, message = "Emergency contact name cannot exceed 100 characters")
    private String emergencyContactName;
    
    @Builder.Default
    private boolean requestReplacement = true;
    
    @Builder.Default
    private boolean blockAllRelatedCards = false;
    
    @Size(max = 500, message = "Additional information cannot exceed 500 characters")
    private String additionalInformation;
    
    /**
     * Validates the report request
     */
    public boolean isValid() {
        if (reason == null || (reason != ReplacementReason.LOST && reason != ReplacementReason.STOLEN)) {
            return false;
        }
        
        if (description == null || description.trim().length() < 10) {
            return false;
        }
        
        // For stolen cards, strongly recommend police report
        if (reason == ReplacementReason.STOLEN && 
            (policeReportNumber == null || policeReportNumber.trim().isEmpty())) {
            // This is valid but should trigger a warning
        }
        
        return true;
    }
    
    /**
     * Checks if this is a stolen card report
     */
    public boolean isStolen() {
        return reason == ReplacementReason.STOLEN;
    }
    
    /**
     * Checks if this is a lost card report
     */
    public boolean isLost() {
        return reason == ReplacementReason.LOST;
    }
    
    /**
     * Checks if emergency contact information is provided
     */
    public boolean hasEmergencyContact() {
        return emergencyContactPhone != null && !emergencyContactPhone.trim().isEmpty();
    }
    
    /**
     * Checks if police report is provided
     */
    public boolean hasPoliceReport() {
        return policeReportNumber != null && !policeReportNumber.trim().isEmpty();
    }
    
    /**
     * Gets the security risk level
     */
    public String getSecurityRiskLevel() {
        if (isStolen()) {
            return hasPoliceReport() ? "HIGH" : "CRITICAL";
        } else {
            return blockAllRelatedCards ? "MEDIUM" : "LOW";
        }
    }
    
    /**
     * Gets formatted incident summary
     */
    public String getIncidentSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Card reported ").append(reason.toString().toLowerCase());
        
        if (incidentTime != null) {
            summary.append(" on ").append(incidentTime.toString());
        }
        
        if (location != null && !location.trim().isEmpty()) {
            summary.append(" at ").append(location);
        }
        
        summary.append(". ").append(description);
        
        if (hasPoliceReport()) {
            summary.append(" Police Report: ").append(policeReportNumber);
        }
        
        return summary.toString();
    }
}