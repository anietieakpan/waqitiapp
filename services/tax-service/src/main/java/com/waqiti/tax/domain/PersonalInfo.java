package com.waqiti.tax.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.waqiti.common.encryption.annotation.Encrypted;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalInfo {
    
    @Column(name = "first_name", nullable = false)
    private String firstName;
    
    @Column(name = "last_name", nullable = false)
    private String lastName;
    
    @JsonIgnore  // SECURITY: SSN must NEVER be exposed in API responses, even encrypted
    @Encrypted(fieldType = "SSN", highlySensitive = true)
    @Column(name = "ssn", nullable = false, length = 500) // Larger length for encrypted data
    private String ssn;

    /**
     * Get masked SSN for display purposes only (XXX-XX-1234)
     */
    public String getMaskedSSN() {
        if (ssn == null || ssn.length() < 4) {
            return "***-**-****";
        }
        // Note: SSN is encrypted, so this works on decrypted value
        String decrypted = ssn;  // Assume @Encrypted handles decryption
        if (decrypted.length() >= 4) {
            return "***-**-" + decrypted.substring(decrypted.length() - 4);
        }
        return "***-**-****";
    }
    
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    
    @Column(name = "address", nullable = false)
    private String address;
    
    @Column(name = "city", nullable = false)
    private String city;
    
    @Column(name = "state", nullable = false)
    private String state;
    
    @Column(name = "zip_code", nullable = false)
    private String zipCode;
    
    @Column(name = "phone")
    private String phone;
    
    @Column(name = "email", nullable = false)
    private String email;
    
    public String getFullName() {
        return firstName + " " + lastName;
    }
    
    public String getFullAddress() {
        return address + ", " + city + ", " + state + " " + zipCode;
    }
}