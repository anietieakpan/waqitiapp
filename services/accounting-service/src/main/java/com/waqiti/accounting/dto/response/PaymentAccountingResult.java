package com.waqiti.accounting.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.waqiti.accounting.domain.AccountingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment accounting result")
public class PaymentAccountingResult {

    @Schema(description = "Transaction identifier")
    @JsonProperty("transaction_id")
    private UUID transactionId;

    @Schema(description = "Journal entry identifier")
    @JsonProperty("journal_entry_id")
    private UUID journalEntryId;

    @Schema(description = "Gross transaction amount")
    @JsonProperty("gross_amount")
    private BigDecimal grossAmount;

    @Schema(description = "Platform fee")
    @JsonProperty("platform_fee")
    private BigDecimal platformFee;

    @Schema(description = "Processor fee")
    @JsonProperty("processor_fee")
    private BigDecimal processorFee;

    @Schema(description = "Total fees")
    @JsonProperty("total_fees")
    private BigDecimal totalFees;

    @Schema(description = "Taxes")
    @JsonProperty("taxes")
    private BigDecimal taxes;

    @Schema(description = "Net amount")
    @JsonProperty("net_amount")
    private BigDecimal netAmount;

    @Schema(description = "Settlement identifier")
    @JsonProperty("settlement_id")
    private String settlementId;

    @Schema(description = "Accounting status")
    @JsonProperty("status")
    private AccountingStatus status;

    @Schema(description = "Processing timestamp")
    @JsonProperty("processed_at")
    private LocalDateTime processedAt;
}
