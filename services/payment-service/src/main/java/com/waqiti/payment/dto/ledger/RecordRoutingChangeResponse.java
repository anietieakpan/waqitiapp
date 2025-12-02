package com.waqiti.payment.dto.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for recording payment routing change response from the ledger service
 * 
 * This response confirms the successful recording of routing change information
 * and provides tracking details for the ledger entry.
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecordRoutingChangeResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Indicates if the ledger record was successfully created
     */
    @NotNull(message = "Success flag is required")
    private boolean success;

    /**
     * Unique identifier for the created ledger entry
     */
    @Size(max = 100, message = "Ledger entry ID must not exceed 100 characters")
    private String ledgerEntryId;

    /**
     * Cost savings amount that was recorded in the ledger
     */
    private BigDecimal costSavingsRecorded;

    /**
     * Timestamp when the ledger entry was created
     */
    @NotNull(message = "Timestamp is required")
    private Instant timestamp;
}