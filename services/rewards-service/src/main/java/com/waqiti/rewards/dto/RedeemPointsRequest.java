package com.waqiti.rewards.dto;

import com.waqiti.rewards.enums.RedemptionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedeemPointsRequest {
    
    @NotNull(message = "Points amount is required")
    @Min(value = 1, message = "Points must be greater than 0")
    private Long points;
    
    @NotNull(message = "Reward type is required")
    private RedemptionType rewardType;
    
    private String giftCardMerchant;
    private String charityId;
    private String merchantId;
    private String notes;
    private Map<String, Object> metadata;
}