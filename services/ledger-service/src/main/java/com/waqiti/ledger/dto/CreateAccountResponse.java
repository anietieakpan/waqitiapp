package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountResponse {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private boolean success;
    private String errorMessage;
}