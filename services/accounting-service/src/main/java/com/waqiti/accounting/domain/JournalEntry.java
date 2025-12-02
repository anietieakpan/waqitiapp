package com.waqiti.accounting.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.MappedCollection;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Journal Entry entity for double-entry bookkeeping
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("journal_entries")
public class JournalEntry {

    @Id
    private String id;

    @NotNull
    @Size(max = 50)
    private String entryNumber;

    @NotNull
    @Size(max = 50)
    private String transactionId;

    @NotNull
    private LocalDate entryDate;

    @NotNull
    private FinancialPeriod period;

    @Size(max = 500)
    private String description;

    @Size(max = 3)
    private String currency;

    @NotNull
    private JournalStatus status;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal totalDebits;

    @NotNull
    @DecimalMin("0.00") 
    private BigDecimal totalCredits;

    private LocalDateTime postedAt;

    @Size(max = 50)
    private String createdBy;

    @Size(max = 50)
    private String postedBy;

    @Size(max = 1000)
    private String notes;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @MappedCollection(idColumn = "journal_entry_id")
    private List<JournalLine> lines;
}

