package com.waqiti.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSuggestions {
    private Long ticketId;
    private String aiSuggestion;
    private List<String> resolutionTemplates;
    private List<String> potentialSolutions;
    private List<SimilarTicketSummary> similarTickets;
    private long estimatedResolutionMinutes;
    private List<String> suggestedTags;
    private String suggestedPriority;
    private List<String> recommendedActions;
    private boolean error;
    private String errorMessage;
}