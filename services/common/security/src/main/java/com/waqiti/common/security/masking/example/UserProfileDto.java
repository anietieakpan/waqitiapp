package com.waqiti.common.security.masking.example;

import com.waqiti.common.security.masking.MaskedData;
import com.waqiti.common.security.masking.MaskingSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Example DTO showing how to use data masking annotations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    
    private String userId;
    
    @MaskedData(MaskingSerializer.MaskingType.NAME)
    private String fullName;
    
    @MaskedData(MaskingSerializer.MaskingType.EMAIL)
    private String email;
    
    @MaskedData(MaskingSerializer.MaskingType.PHONE)
    private String phoneNumber;
    
    @MaskedData(MaskingSerializer.MaskingType.SSN)
    private String ssn;
    
    @MaskedData(MaskingSerializer.MaskingType.ADDRESS)
    private String address;
    
    @MaskedData(MaskingSerializer.MaskingType.ACCOUNT_NUMBER)
    private String bankAccountNumber;
    
    @MaskedData(MaskingSerializer.MaskingType.CARD_NUMBER)
    private String creditCardNumber;
    
    // This field won't be masked
    private String country;
    
    // Nested object with masking
    private EmergencyContact emergencyContact;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmergencyContact {
        @MaskedData(MaskingSerializer.MaskingType.NAME)
        private String name;
        
        @MaskedData(MaskingSerializer.MaskingType.PHONE)
        private String phone;
        
        private String relationship;
    }
}