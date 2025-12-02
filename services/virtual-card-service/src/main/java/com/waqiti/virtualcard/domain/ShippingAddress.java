package com.waqiti.virtualcard.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Shipping Address embedded entity
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddress {
    
    @Column(name = "recipient_name", nullable = false)
    private String recipientName;
    
    @Column(name = "company_name")
    private String companyName;
    
    @Column(name = "address_line_1", nullable = false)
    private String addressLine1;
    
    @Column(name = "address_line_2")
    private String addressLine2;
    
    @Column(name = "city", nullable = false)
    private String city;
    
    @Column(name = "state_province")
    private String stateProvince;
    
    @Column(name = "postal_code", nullable = false)
    private String postalCode;
    
    @Column(name = "country", nullable = false)
    private String country;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column(name = "delivery_instructions", length = 500)
    private String deliveryInstructions;
    
    @Column(name = "address_type")
    private String addressType; // HOME, BUSINESS, PO_BOX
    
    @Column(name = "validated")
    @Builder.Default
    private boolean validated = false;
    
    @Column(name = "validation_score")
    private Double validationScore;
    
    @Column(name = "latitude")
    private Double latitude;
    
    @Column(name = "longitude")
    private Double longitude;
    
    /**
     * Returns formatted address for display
     */
    public String getFormattedAddress() {
        StringBuilder address = new StringBuilder();
        
        if (recipientName != null) {
            address.append(recipientName).append("\n");
        }
        
        if (companyName != null && !companyName.trim().isEmpty()) {
            address.append(companyName).append("\n");
        }
        
        address.append(addressLine1);
        
        if (addressLine2 != null && !addressLine2.trim().isEmpty()) {
            address.append("\n").append(addressLine2);
        }
        
        address.append("\n").append(city);
        
        if (stateProvince != null && !stateProvince.trim().isEmpty()) {
            address.append(", ").append(stateProvince);
        }
        
        address.append(" ").append(postalCode);
        address.append("\n").append(country);
        
        return address.toString();
    }
    
    /**
     * Validates required fields are present
     */
    public boolean isValid() {
        return recipientName != null && !recipientName.trim().isEmpty() &&
               addressLine1 != null && !addressLine1.trim().isEmpty() &&
               city != null && !city.trim().isEmpty() &&
               postalCode != null && !postalCode.trim().isEmpty() &&
               country != null && !country.trim().isEmpty();
    }
    
    /**
     * Returns single line address for shipping labels
     */
    public String getShippingLabel() {
        StringBuilder label = new StringBuilder();
        
        label.append(addressLine1);
        
        if (addressLine2 != null && !addressLine2.trim().isEmpty()) {
            label.append(", ").append(addressLine2);
        }
        
        label.append(", ").append(city);
        
        if (stateProvince != null && !stateProvince.trim().isEmpty()) {
            label.append(", ").append(stateProvince);
        }
        
        label.append(" ").append(postalCode).append(", ").append(country);
        
        return label.toString();
    }
}