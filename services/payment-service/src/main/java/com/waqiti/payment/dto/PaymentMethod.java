package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentMethod {
    private UUID id;
    private String methodId;
    private com.waqiti.payment.domain.PaymentMethod.PaymentMethodType methodType;
    private String provider;
    private com.waqiti.payment.domain.PaymentMethod.PaymentMethodStatus status;
    private boolean isDefault;
    private String displayName;
    private String maskedDetails;
    private com.waqiti.payment.domain.PaymentMethod.VerificationStatus verificationStatus;
    private LocalDate expiresAt;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static PaymentMethod fromEntity(com.waqiti.payment.domain.PaymentMethod entity) {
        return PaymentMethod.builder()
                .id(entity.getId())
                .methodId(entity.getMethodId())
                .methodType(entity.getMethodType())
                .provider(entity.getProvider())
                .status(entity.getStatus())
                .isDefault(entity.isDefault())
                .displayName(entity.getDisplayName())
                .maskedDetails(entity.getMaskedDetails())
                .verificationStatus(entity.getVerificationStatus())
                .expiresAt(entity.getExpiresAt())
                .metadata(entity.getMetadata())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}