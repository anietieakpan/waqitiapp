package com.waqiti.security.domain;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transaction_signatures", indexes = {
    @Index(name = "idx_transaction_signature_txn", columnList = "transaction_id", unique = true),
    @Index(name = "idx_transaction_signature_user", columnList = "user_id"),
    @Index(name = "idx_transaction_signature_status", columnList = "status"),
    @Index(name = "idx_transaction_signature_hash", columnList = "signature_hash")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TransactionSignature {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @EqualsAndHashCode.Include
    private String id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "signing_key_id")
    private String signingKeyId;

    @Column(name = "signature_value", nullable = false, columnDefinition = "TEXT")
    private String signatureValue;

    @Column(name = "signature_hash", nullable = false)
    private String signatureHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "signing_method", nullable = false)
    private SigningMethod signingMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SignatureStatus status;

    // Transaction details
    @Column(name = "transaction_data", nullable = false, columnDefinition = "TEXT")
    private String transactionData;

    @Column(name = "transaction_amount")
    private Double transactionAmount;

    @Column(name = "transaction_currency")
    private String transactionCurrency;

    @Column(name = "transaction_type")
    private String transactionType;

    @Column(name = "recipient_id")
    private String recipientId;

    @Column(name = "recipient_name")
    private String recipientName;

    // Hardware key specific
    @Column(name = "hardware_device_id")
    private String hardwareDeviceId;

    @Column(name = "hardware_attestation_data", columnDefinition = "TEXT")
    private String hardwareAttestationData;

    @Column(name = "secure_element_used")
    private boolean secureElementUsed;

    // Biometric specific
    @Enumerated(EnumType.STRING)
    @Column(name = "biometric_type")
    private BiometricType biometricType;

    @Column(name = "biometric_score")
    private Double biometricScore;

    @Column(name = "biometric_template_id")
    private String biometricTemplateId;

    // Multi-signature specific
    @Column(name = "multi_sig_config_id")
    private String multiSigConfigId;

    @Column(name = "required_signatures")
    private Integer requiredSignatures;

    @OneToMany(mappedBy = "transactionSignature", cascade = CascadeType.ALL)
    private List<MultiSignature> collectedSignatures = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "signature_signer_ids", joinColumns = @JoinColumn(name = "signature_id"))
    @Column(name = "signer_id")
    private List<String> signerIds = new ArrayList<>();

    // Security context
    @Column(name = "device_info", columnDefinition = "TEXT")
    private String deviceInfo;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "geo_location")
    private String geoLocation;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "session_id")
    private String sessionId;

    // Risk assessment
    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "risk_factors", columnDefinition = "TEXT")
    private String riskFactors;

    @Column(name = "fraud_check_result")
    private String fraudCheckResult;

    // Verification
    @Column(name = "verification_count")
    private int verificationCount;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Column(name = "verified_by_systems", columnDefinition = "TEXT")
    private String verifiedBySystems;

    // Timestamps
    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revocation_reason")
    private String revocationReason;

    // Audit trail
    @Column(name = "audit_log", columnDefinition = "TEXT")
    private String auditLog;

    @Column(name = "compliance_checked")
    private boolean complianceChecked;

    @Column(name = "compliance_result")
    private String complianceResult;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = SignatureStatus.ACTIVE;
        }
        verificationCount = 0;
        complianceChecked = false;
    }

    /**
     * Check if signature is expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if signature is valid
     */
    public boolean isValid() {
        return status == SignatureStatus.ACTIVE && 
               !isExpired() && 
               revokedAt == null;
    }

    /**
     * Increment verification count
     */
    public void incrementVerificationCount() {
        verificationCount++;
        lastVerifiedAt = LocalDateTime.now();
    }

    /**
     * Add a signature for multi-sig
     */
    public void addSignature(String signerId, String signature) {
        MultiSignature multiSig = MultiSignature.builder()
            .transactionSignature(this)
            .signerId(signerId)
            .signature(signature)
            .signedAt(LocalDateTime.now())
            .build();
        
        collectedSignatures.add(multiSig);
        
        // Check if we have enough signatures
        if (collectedSignatures.size() >= requiredSignatures) {
            status = SignatureStatus.ACTIVE;
        }
    }

    /**
     * Check if multi-signature is complete
     */
    public boolean isMultiSigComplete() {
        return signingMethod == SigningMethod.MULTI_SIGNATURE &&
               collectedSignatures.size() >= requiredSignatures;
    }

    /**
     * Revoke the signature
     */
    public void revoke(String reason) {
        status = SignatureStatus.REVOKED;
        revokedAt = LocalDateTime.now();
        revocationReason = reason;
    }

    /**
     * Add audit log entry
     */
    public void addAuditEntry(String entry) {
        if (auditLog == null) {
            auditLog = "";
        }
        auditLog += LocalDateTime.now() + ": " + entry + "\n";
    }

    /**
     * Set compliance check result
     */
    public void setComplianceCheckResult(boolean passed, String details) {
        complianceChecked = true;
        complianceResult = passed ? "PASSED" : "FAILED";
        if (details != null) {
            complianceResult += ": " + details;
        }
    }

    /**
     * Get signature summary for display
     */
    public String getSignatureSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Transaction: ").append(transactionId).append("\n");
        summary.append("Method: ").append(signingMethod).append("\n");
        summary.append("Status: ").append(status).append("\n");
        
        if (signingMethod == SigningMethod.HARDWARE_KEY) {
            summary.append("Device: ").append(hardwareDeviceId).append("\n");
        } else if (signingMethod == SigningMethod.BIOMETRIC) {
            summary.append("Biometric: ").append(biometricType).append("\n");
        } else if (signingMethod == SigningMethod.MULTI_SIGNATURE) {
            summary.append("Signatures: ").append(collectedSignatures.size())
                   .append("/").append(requiredSignatures).append("\n");
        }
        
        return summary.toString();
    }
}