package com.waqiti.account.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Final Statement Entity
 *
 * Final account statement generated at closure
 */
@Entity
@Table(name = "final_statements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "closure_id")
    private UUID closureId;

    @Column(name = "statement_period_start")
    private LocalDateTime statementPeriodStart;

    @Column(name = "statement_period_end")
    private LocalDateTime statementPeriodEnd;

    @Column(name = "opening_balance", precision = 19, scale = 4)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 19, scale = 4)
    private BigDecimal closingBalance;

    @Column(name = "total_credits", precision = 19, scale = 4)
    private BigDecimal totalCredits;

    @Column(name = "total_debits", precision = 19, scale = 4)
    private BigDecimal totalDebits;

    @Column(name = "statement_url", length = 500)
    private String statementUrl;

    @CreationTimestamp
    @Column(name = "generated_at")
    private LocalDateTime generatedAt;
}
