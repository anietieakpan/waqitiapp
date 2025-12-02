package com.waqiti.support.service;

import com.waqiti.support.domain.Ticket;
import com.waqiti.support.domain.TicketCategory;
import com.waqiti.support.domain.TicketSubCategory;
import com.waqiti.support.domain.TicketPriority;
import com.waqiti.support.dto.CreateTicketRequest;
import com.waqiti.support.dto.CategorizationResult;
import com.waqiti.support.dto.CategorizationConfidence;

import java.util.List;
import java.util.Set;

/**
 * Service for automatically categorizing support tickets using machine learning
 * and rule-based classification
 */
public interface TicketCategorizationService {

    /**
     * Automatically categorize a ticket using ML and rule-based approaches
     */
    CategorizationResult categorizeTicket(CreateTicketRequest request);

    /**
     * Re-categorize an existing ticket (for training or correction)
     */
    CategorizationResult recategorizeTicket(Ticket ticket);

    /**
     * Determine priority based on content analysis and context
     */
    TicketPriority determinePriority(CreateTicketRequest request);

    /**
     * Extract relevant tags from ticket content
     */
    Set<String> extractTags(String subject, String description);

    /**
     * Predict escalation probability
     */
    double predictEscalationProbability(CreateTicketRequest request);

    /**
     * Get confidence scores for all possible categories
     */
    List<CategorizationConfidence> getCategoryConfidences(String subject, String description);

    /**
     * Train the categorization model with labeled data
     */
    void trainModel(List<Ticket> labeledTickets);

    /**
     * Check if the ticket content indicates urgent/high-priority issues
     */
    boolean isUrgentIssue(String content);

    /**
     * Identify if ticket is related to a security issue
     */
    boolean isSecurityIssue(String content);

    /**
     * Check if ticket appears to be spam or invalid
     */
    boolean isSpamTicket(String subject, String description);

    /**
     * Find similar historical tickets for context
     */
    List<Ticket> findSimilarTickets(String content, int limit);

    /**
     * Get category-specific routing suggestions
     */
    List<String> getRoutingSuggestions(TicketCategory category, TicketSubCategory subCategory);
}