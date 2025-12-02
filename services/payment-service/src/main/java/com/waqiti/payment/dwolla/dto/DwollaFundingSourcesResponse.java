package com.waqiti.payment.dwolla.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Dwolla Funding Sources Response DTO
 * 
 * Response object for funding sources list.
 */
@Data
public class DwollaFundingSourcesResponse {
    
    @JsonProperty("_embedded")
    private DwollaFundingSourcesEmbedded embedded;
    
    @Data
    public static class DwollaFundingSourcesEmbedded {
        
        @JsonProperty("funding-sources")
        private List<DwollaFundingSource> fundingSources;
    }
}