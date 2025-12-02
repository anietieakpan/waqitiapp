package com.waqiti.grouppayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Split Bill Result (Internal DTO)
 * Internal data structure used during split bill calculations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitBillResult {

    @Builder.Default
    private List<ParticipantSplit> participantSplits = new ArrayList<>();

    @Builder.Default
    private List<SplitAdjustment> adjustments = new ArrayList<>();

    private BigDecimal totalAllocated;
}
