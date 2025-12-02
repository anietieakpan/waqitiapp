package com.waqiti.accounting.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Chart of Accounts entity defining the accounting structure
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("chart_of_accounts")
public class ChartOfAccounts {

    @Id
    private String id;

    @NotNull
    @Size(max = 10)
    private String code;

    @NotNull
    @Size(max = 200)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotNull
    private AccountType type;

    @NotNull
    private NormalBalance normalBalance;

    @Size(max = 10)
    private String parentCode;

    @Size(max = 3)
    private String currency;

    @NotNull
    private Boolean isActive;

    @NotNull
    private Boolean isSystemAccount;

    @Size(max = 50)
    private String category;

    @Size(max = 50)
    private String subcategory;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

