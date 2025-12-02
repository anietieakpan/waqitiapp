package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Zero-Knowledge Proof for transaction privacy and validity
 */
@Data
@Builder
public class ZKProof {
    private String id;
    private String transactionId;
    private String[] publicInputs;
    private byte[] proof;
    private String verificationKey;
    private LocalDateTime timestamp;
    private boolean isValid;
}
