/**
 * Blockchain Transaction DTO
 * Contains transaction details from blockchain
 */
package com.waqiti.crypto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockchainTransaction {
    private String txHash;
    private Long blockHeight;
    private Integer confirmations;
    private Long timestamp;
    private BigDecimal fee;
    private String status;
    private String from;
    private String to;
    private BigDecimal value;
    private BigInteger gasUsed;
    private List<TransactionInput> inputs;
    private List<TransactionOutput> outputs;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionInput {
        private String txHash;
        private Integer outputIndex;
        private String address;
        private BigDecimal amount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionOutput {
        private Integer index;
        private String address;
        private BigDecimal amount;
        private String scriptPubKey;
    }
}