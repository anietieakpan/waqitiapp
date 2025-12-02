/**
 * HD Wallet Keys DTO
 * Contains hierarchical deterministic wallet key information
 */
package com.waqiti.crypto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HDWalletKeys {
    private String privateKey;
    private String publicKey;
    private String address;
    private String derivationPath;
    private String chainCode;
    private String mnemonic; // Only used during wallet creation, not stored
}