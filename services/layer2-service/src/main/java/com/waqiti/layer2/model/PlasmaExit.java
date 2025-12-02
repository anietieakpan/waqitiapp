package com.waqiti.layer2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * Plasma exit (withdrawal) request
 */
@Data
@Builder
public class PlasmaExit {
    private String id;
    private String userAddress;
    private BigInteger amount;
    private ExitProof exitProof;
    private LocalDateTime timestamp;
    private LocalDateTime challengePeriodEnd;
    private PlasmaExitStatus status;
    private String l1TransactionHash;
    private String finalizationTxHash;
}
