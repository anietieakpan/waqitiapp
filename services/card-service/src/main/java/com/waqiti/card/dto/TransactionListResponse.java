package com.waqiti.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * TransactionListResponse DTO - List of transactions with pagination info
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionListResponse {
    private List<CardTransactionResponse> transactions;
    private Integer totalElements;
    private Integer totalPages;
    private Integer currentPage;
    private Integer pageSize;
}
