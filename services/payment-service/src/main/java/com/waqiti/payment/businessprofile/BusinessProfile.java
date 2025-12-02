package com.waqiti.payment.businessprofile;

import com.waqiti.common.encryption.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "business_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessProfile {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;
    
    @Column(name = "legal_name", nullable = false, length = 255)
    private String legalName;
    
    @Column(name = "business_type", nullable = false, length = 50)
    private String businessType;
    
    @Column(name = "industry", nullable = false, length = 100)
    private String industry;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "logo", length = 500)
    private String logo;
    
    @Column(name = "website", length = 255)
    private String website;
    
    @Column(name = "email", nullable = false, length = 255)
    private String email;
    
    @Column(name = "phone", nullable = false, length = 50)
    private String phone;
    
    @Column(name = "country", length = 2)
    private String country;
    
    @Embedded
    private Address address;
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "tax_id", unique = true, length = 500)
    private String taxId; // PCI DSS: Encrypted EIN/Tax ID
    
    @Column(name = "registration_number", unique = true, length = 50)
    private String registrationNumber;
    
    @Column(name = "incorporation_date")
    private LocalDate incorporationDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProfileStatus status;
    
    @ElementCollection
    @CollectionTable(name = "business_hours", joinColumns = @JoinColumn(name = "profile_id"))
    @MapKeyColumn(name = "day_of_week")
    private Map<String, BusinessHours> businessHours;
    
    @Embedded
    private PaymentSettings paymentSettings;
    
    @Embedded
    private InvoiceSettings invoiceSettings;
    
    @Column(name = "verified_at")
    private Instant verifiedAt;
    
    @Column(name = "verification_details", columnDefinition = "JSONB")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> verificationDetails;
    
    @Column(name = "verification_failure_reason")
    private String verificationFailureReason;
    
    @Column(name = "suspension_reason")
    private String suspensionReason;
    
    @Column(name = "suspended_at")
    private Instant suspendedAt;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = ProfileStatus.PENDING_VERIFICATION;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}