package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response containing supported card networks and types
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportedCardResponse {
    private List<CardNetwork> supportedNetworks;
    private List<String> supportedCardTypes;
    private List<String> supportedCountries;
    private List<FeeTier> feeTiers;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardNetwork {
        private String network;
        private String displayName;
        private String logoUrl;
        private boolean instantDepositSupported;
        private Double feePercentage;
        private Double minFee;
        private Double maxFee;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeeTier {
        private Double minAmount;
        private Double maxAmount;
        private Double feePercentage;
        private Double flatFee;
    }
}