package com.waqiti.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * CardListResponse DTO - List of cards with pagination info
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardListResponse {
    private List<CardResponse> cards;
    private Integer totalElements;
    private Integer totalPages;
    private Integer currentPage;
    private Integer pageSize;
}
