package com.waqiti.accounting.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * Journal Line entity representing individual debit/credit entries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("journal_lines")
public class JournalLine {

    @Id
    private String id;

    @NotNull
    private JournalEntry journalEntry;

    @NotNull
    @Min(1)
    private Integer lineNumber;

    @NotNull
    @Size(max = 10)
    private String accountCode;

    @Size(max = 500)
    private String description;

    private BigDecimal debitAmount;

    private BigDecimal creditAmount;

    @Size(max = 3)
    private String currency;

    @Size(max = 50)
    private String userId;

    @Size(max = 50)
    private String merchantId;

    @Size(max = 100)
    private String reference;

    @Size(max = 1000)
    private String metadata;
}