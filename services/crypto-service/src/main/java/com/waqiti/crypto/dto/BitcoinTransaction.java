package com.waqiti.crypto.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class BitcoinTransaction {
    private String txId;
    private int version;
    private int size;
    private int virtualSize;
    private long lockTime;
    private BigDecimal fee;
    private int confirmations;
    private String blockHash;
    private long blockHeight;
    private long timestamp;
    private List<TransactionInput> inputs;
    private List<TransactionOutput> outputs;

    @Data
    @Builder
    public static class TransactionInput {
        private String txId;
        private int vout;
        private String scriptSig;
        private String sequence;
        private List<String> witness;
    }

    @Data
    @Builder
    public static class TransactionOutput {
        private BigDecimal value;
        private int n;
        private String scriptPubKey;
        private String address;
        private String type;
    }
}