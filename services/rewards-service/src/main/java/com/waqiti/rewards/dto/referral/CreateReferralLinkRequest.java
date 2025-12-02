package com.waqiti.rewards.dto.referral;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new referral link")
public class CreateReferralLinkRequest {

    @NotBlank(message = "Program ID is required")
    @Schema(description = "Program ID to create link for", example = "REF-PROG-001")
    private String programId;

    @Pattern(regexp = "^(EMAIL|SMS|SOCIAL_MEDIA|WHATSAPP|WEBSITE|APP|QR_CODE|OTHER)$",
             message = "Invalid channel. Must be one of: EMAIL, SMS, SOCIAL_MEDIA, WHATSAPP, WEBSITE, APP, QR_CODE, OTHER")
    @Schema(description = "Marketing channel", example = "SOCIAL_MEDIA")
    private String channel;

    @Schema(description = "Campaign identifier for tracking", example = "summer-2025")
    private String campaign;

    @Schema(description = "Custom referral code (optional, auto-generated if not provided)")
    private String customCode;

    @Schema(description = "Additional link metadata")
    private Map<String, Object> metadata;
}
