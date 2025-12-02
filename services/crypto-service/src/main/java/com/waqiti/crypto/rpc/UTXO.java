package com.waqiti.crypto.rpc;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class UTXO {
    private String txHash;
    private int outputIndex;
    private BigDecimal amount;
    private int confirmations;
    private String scriptPubKey;
    private String address;
    private boolean spendable;
    private boolean safe;
    private String label;
    private boolean solvable;
}