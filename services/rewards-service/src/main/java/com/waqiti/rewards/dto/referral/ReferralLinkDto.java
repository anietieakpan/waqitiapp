package com.waqiti.rewards.dto.referral;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Referral link details")
public class ReferralLinkDto {

    @Schema(description = "Link unique identifier", example = "LNK-ABC12345")
    private String linkId;

    @Schema(description = "User ID who owns this link")
    private UUID userId;

    @Schema(description = "Program ID this link belongs to")
    private String programId;

    @Schema(description = "Program name")
    private String programName;

    @Schema(description = "Unique referral code", example = "JOHN-REF-XYZ")
    private String referralCode;

    @Schema(description = "Short URL for sharing", example = "https://example.com/r/JOHN-REF-XYZ")
    private String shortUrl;

    @Schema(description = "Full URL with UTM parameters")
    private String fullUrl;

    @Schema(description = "Marketing channel", example = "SOCIAL_MEDIA")
    private String channel;

    @Schema(description = "Campaign identifier")
    private String campaign;

    @Schema(description = "Number of clicks on this link")
    private Integer clickCount;

    @Schema(description = "Number of unique clicks")
    private Integer uniqueClicks;

    @Schema(description = "Number of conversions from this link")
    private Integer conversionCount;

    @Schema(description = "Conversion rate as percentage")
    private Double conversionRate;

    @Schema(description = "Whether link is currently active")
    private Boolean isActive;

    @Schema(description = "Link expiration date")
    private LocalDateTime expiresAt;

    @Schema(description = "Last time link was clicked")
    private LocalDateTime lastClickedAt;

    @Schema(description = "Additional link metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
