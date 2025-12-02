/**
 * UTXO Set DTO
 * Contains unspent transaction outputs for Bitcoin-like currencies
 */
package com.waqiti.crypto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UTXOSet {
    private String address;
    private List<UTXO> utxos;
    private BigDecimal totalAmount;
    private Integer count;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class UTXO {
    private String txHash;
    private Integer outputIndex;
    private BigDecimal amount;
    private Integer confirmations;
    private String scriptPubKey;
}