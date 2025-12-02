/**
 * Network Fees DTO
 * Contains current network fee information for a cryptocurrency
 */
package com.waqiti.crypto.dto;

import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkFees {
    private CryptoCurrency currency;
    private BigDecimal slow;
    private BigDecimal standard;
    private BigDecimal fast;
    private Map<String, Integer> estimatedConfirmationTime; // in minutes
}