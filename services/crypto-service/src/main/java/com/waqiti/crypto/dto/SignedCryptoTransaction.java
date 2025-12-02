/**
 * Signed Crypto Transaction DTO
 * Contains signed transaction data ready for blockchain broadcast
 */
package com.waqiti.crypto.dto;

import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignedCryptoTransaction {
    private UUID transactionId;
    private CryptoCurrency currency;
    private String signedTransaction; // Hex-encoded signed transaction
    private String fromAddress;
    private String toAddress;
    private BigDecimal amount;
    private BigDecimal fee;
    private String transactionType;
}