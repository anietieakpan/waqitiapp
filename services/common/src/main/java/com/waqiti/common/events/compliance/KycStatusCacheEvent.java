package com.waqiti.common.events.compliance;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycStatusCacheEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Event ID is required")
    @JsonProperty("event_id")
    private String eventId;

    @NotBlank(message = "Correlation ID is required")
    @JsonProperty("correlation_id")
    private String correlationId;

    @NotNull(message = "Timestamp is required")
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    @JsonProperty("event_version")
    private String eventVersion = "1.0";

    @NotBlank(message = "Event source is required")
    @JsonProperty("source")
    private String source;

    @NotBlank(message = "User ID is required")
    @JsonProperty("user_id")
    private String userId;

    @NotBlank(message = "Verification ID is required")
    @JsonProperty("verification_id")
    private String verificationId;

    @NotBlank(message = "Cache operation is required")
    @JsonProperty("cache_operation")
    private String cacheOperation; // SET, GET, DELETE, INVALIDATE, REFRESH

    @NotBlank(message = "Cache key is required")
    @JsonProperty("cache_key")
    private String cacheKey;

    @JsonProperty("cache_region")
    private String cacheRegion;

    @JsonProperty("kyc_status")
    private String kycStatus;

    @JsonProperty("kyc_level")
    private String kycLevel;

    @JsonProperty("previous_status")
    private String previousStatus;

    @JsonProperty("verification_score")
    private Integer verificationScore;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("compliance_flags")
    private String complianceFlags;

    @JsonProperty("last_verified_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime lastVerifiedAt;

    @JsonProperty("expiry_time")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime expiryTime;

    @JsonProperty("cache_hit")
    private Boolean cacheHit;

    @JsonProperty("cache_ttl_seconds")
    private Long cacheTtlSeconds;

    @JsonProperty("cache_size")
    private Long cacheSize;

    @JsonProperty("eviction_reason")
    private String evictionReason;

    @JsonProperty("updated_fields")
    private String updatedFields;
}