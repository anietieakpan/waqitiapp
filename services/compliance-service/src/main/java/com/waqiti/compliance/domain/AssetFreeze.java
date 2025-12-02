package com.waqiti.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Asset Freeze domain entity for compliance management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "asset_freezes")
public class AssetFreeze {

    @Id
    private String id;

    private String freezeId;
    private String userId;
    private String accountId;
    private FreezeReason reason;
    private FreezeStatus status;
    private String severity;
    private String description;
    private String legalOrder;
    private String requestingAuthority;
    private String correlationId;

    private LocalDateTime frozenAt;
    private LocalDateTime releasedAt;
    private LocalDateTime expiresAt;

    private boolean isActive;
    private String releasedBy;
    private String releaseReason;

    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
