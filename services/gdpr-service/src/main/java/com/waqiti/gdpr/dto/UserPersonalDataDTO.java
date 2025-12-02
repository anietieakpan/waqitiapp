package com.waqiti.gdpr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for user personal data in GDPR export
 * Contains all personal information stored about the user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPersonalDataDTO {

    private String userId;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private LocalDateTime dateOfBirth;
    private String nationality;
    private String taxId;

    // Address information
    private AddressDTO primaryAddress;
    private AddressDTO billingAddress;
    private List<AddressDTO> historicalAddresses;

    // KYC/Identity information
    private KYCDataDTO kycData;

    // Account information
    private LocalDateTime accountCreatedAt;
    private LocalDateTime lastLoginAt;
    private String accountStatus;
    private String accountType;

    // Profile information
    private String profilePictureUrl;
    private Map<String, Object> customFields;

    // Security information
    private List<DeviceDTO> registeredDevices;
    private List<SecurityEventDTO> securityEvents;

    // Financial information summary
    private FinancialSummaryDTO financialSummary;

    // Data retrieval metadata
    private boolean dataRetrievalFailed;
    private String failureReason;
    private boolean requiresManualReview;
    private LocalDateTime retrievedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressDTO {
        private String street;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private String addressType;
        private LocalDateTime validFrom;
        private LocalDateTime validTo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KYCDataDTO {
        private String verificat ionLevel;
        private String documentType;
        private String documentNumber;
        private LocalDateTime documentExpiryDate;
        private LocalDateTime verificationDate;
        private String verificationStatus;
        private List<String> verificationMethods;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceDTO {
        private String deviceId;
        private String deviceType;
        private String deviceName;
        private LocalDateTime firstSeenAt;
        private LocalDateTime lastUsedAt;
        private String trustLevel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityEventDTO {
        private String eventType;
        private LocalDateTime occurredAt;
        private String ipAddress;
        private String location;
        private String outcome;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialSummaryDTO {
        private Integer totalWallets;
        private Integer totalPaymentMethods;
        private String preferredCurrency;
        private boolean hasActiveLoans;
        private boolean hasInvestments;
    }
}
