package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerInteraction;
import com.waqiti.customer.entity.CustomerInteraction.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CustomerInteraction entity
 *
 * Provides data access methods for customer interaction management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerInteractionRepository extends JpaRepository<CustomerInteraction, UUID> {

    /**
     * Find interaction by interaction ID
     *
     * @param interactionId the unique interaction identifier
     * @return Optional containing the interaction if found
     */
    Optional<CustomerInteraction> findByInteractionId(String interactionId);

    /**
     * Find all interactions for a customer
     *
     * @param customerId the customer ID
     * @return list of interactions
     */
    List<CustomerInteraction> findByCustomerId(String customerId);

    /**
     * Find interactions by customer ID with pagination
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of interactions
     */
    Page<CustomerInteraction> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Find interactions by type for a customer
     *
     * @param customerId the customer ID
     * @param interactionType the interaction type
     * @param pageable pagination information
     * @return page of interactions
     */
    Page<CustomerInteraction> findByCustomerIdAndInteractionType(
        String customerId,
        InteractionType interactionType,
        Pageable pageable
    );

    /**
     * Find interactions by channel for a customer
     *
     * @param customerId the customer ID
     * @param channel the interaction channel
     * @param pageable pagination information
     * @return page of interactions
     */
    Page<CustomerInteraction> findByCustomerIdAndInteractionChannel(
        String customerId,
        InteractionChannel channel,
        Pageable pageable
    );

    /**
     * Find interactions by sentiment for a customer
     *
     * @param customerId the customer ID
     * @param sentiment the sentiment
     * @param pageable pagination information
     * @return page of interactions
     */
    Page<CustomerInteraction> findByCustomerIdAndSentiment(
        String customerId,
        Sentiment sentiment,
        Pageable pageable
    );

    /**
     * Find interactions by outcome for a customer
     *
     * @param customerId the customer ID
     * @param outcome the outcome
     * @param pageable pagination information
     * @return page of interactions
     */
    Page<CustomerInteraction> findByCustomerIdAndOutcome(
        String customerId,
        Outcome outcome,
        Pageable pageable
    );

    /**
     * Find interactions within date range for a customer
     *
     * @param customerId the customer ID
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of interactions
     */
    @Query("SELECT i FROM CustomerInteraction i WHERE i.customerId = :customerId " +
           "AND i.interactionDate BETWEEN :startDate AND :endDate ORDER BY i.interactionDate DESC")
    Page<CustomerInteraction> findByDateRange(
        @Param("customerId") String customerId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Find all interactions within date range
     *
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of interactions
     */
    @Query("SELECT i FROM CustomerInteraction i WHERE i.interactionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY i.interactionDate DESC")
    Page<CustomerInteraction> findByInteractionDateBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Find interactions requiring follow-up for a customer
     *
     * @param customerId the customer ID
     * @return list of interactions requiring follow-up
     */
    @Query("SELECT i FROM CustomerInteraction i WHERE i.customerId = :customerId " +
           "AND i.followUpRequired = true ORDER BY i.followUpDate ASC")
    List<CustomerInteraction> findFollowUpRequired(@Param("customerId") String customerId);

    /**
     * Find all interactions requiring follow-up
     *
     * @param pageable pagination information
     * @return page of interactions requiring follow-up
     */
    @Query("SELECT i FROM CustomerInteraction i WHERE i.followUpRequired = true ORDER BY i.followUpDate ASC")
    Page<CustomerInteraction> findAllFollowUpRequired(Pageable pageable);

    /**
     * Find overdue follow-ups
     *
     * @param currentDate the current date
     * @param pageable pagination information
     * @return page of overdue follow-ups
     */
    @Query("SELECT i FROM CustomerInteraction i WHERE i.followUpRequired = true " +
           "AND i.followUpDate IS NOT NULL AND i.followUpDate < :currentDate ORDER BY i.followUpDate ASC")
    Page<CustomerInteraction> findOverdueFollowUps(@Param("currentDate") LocalDate currentDate, Pageable pageable);

    /**
     * Find negative interactions for a customer
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of negative interactions
     */
    @Query("SELECT i FROM CustomerInteraction i WHERE i.customerId = :customerId " +
           "AND i.sentiment IN ('NEGATIVE', 'VERY_NEGATIVE') ORDER BY i.interactionDate DESC")
    Page<CustomerInteraction> findNegativeInteractions(@Param("customerId") String customerId, Pageable pageable);

    /**
     * Find all negative interactions
     *
     * @param pageable pagination information
     * @return page of negative interactions
     */
    @Query("SELECT i FROM CustomerInteraction i WHERE i.sentiment IN ('NEGATIVE', 'VERY_NEGATIVE') " +
           "ORDER BY i.interactionDate DESC")
    Page<CustomerInteraction> findAllNegativeInteractions(Pageable pageable);

    /**
     * Find positive interactions for a customer
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of positive interactions
     */
    @Query("SELECT i FROM CustomerInteraction i WHERE i.customerId = :customerId " +
           "AND i.sentiment IN ('POSITIVE', 'VERY_POSITIVE') ORDER BY i.interactionDate DESC")
    Page<CustomerInteraction> findPositiveInteractions(@Param("customerId") String customerId, Pageable pageable);

    /**
     * Find escalated interactions
     *
     * @param pageable pagination information
     * @return page of escalated interactions
     */
    @Query("SELECT i FROM CustomerInteraction i WHERE i.outcome = 'ESCALATED' ORDER BY i.interactionDate DESC")
    Page<CustomerInteraction> findEscalatedInteractions(Pageable pageable);

    /**
     * Find unresolved interactions for a customer
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of unresolved interactions
     */
    @Query("SELECT i FROM CustomerInteraction i WHERE i.customerId = :customerId " +
           "AND i.outcome IN ('PENDING', 'ESCALATED') ORDER BY i.interactionDate DESC")
    Page<CustomerInteraction> findUnresolvedInteractions(@Param("customerId") String customerId, Pageable pageable);

    /**
     * Find interactions handled by a specific user
     *
     * @param handledBy the user who handled the interactions
     * @param pageable pagination information
     * @return page of interactions
     */
    Page<CustomerInteraction> findByHandledBy(String handledBy, Pageable pageable);

    /**
     * Find interactions by handler for a customer
     *
     * @param customerId the customer ID
     * @param handledBy the user who handled the interactions
     * @param pageable pagination information
     * @return page of interactions
     */
    Page<CustomerInteraction> findByCustomerIdAndHandledBy(
        String customerId,
        String handledBy,
        Pageable pageable
    );

    /**
     * Find all interactions by type
     *
     * @param interactionType the interaction type
     * @param pageable pagination information
     * @return page of interactions
     */
    Page<CustomerInteraction> findByInteractionType(InteractionType interactionType, Pageable pageable);

    /**
     * Find all interactions by channel
     *
     * @param channel the interaction channel
     * @param pageable pagination information
     * @return page of interactions
     */
    Page<CustomerInteraction> findByInteractionChannel(InteractionChannel channel, Pageable pageable);

    /**
     * Count interactions by customer ID
     *
     * @param customerId the customer ID
     * @return count of interactions
     */
    long countByCustomerId(String customerId);

    /**
     * Count interactions by type for a customer
     *
     * @param customerId the customer ID
     * @param interactionType the interaction type
     * @return count of interactions
     */
    long countByCustomerIdAndInteractionType(String customerId, InteractionType interactionType);

    /**
     * Count interactions by channel for a customer
     *
     * @param customerId the customer ID
     * @param channel the interaction channel
     * @return count of interactions
     */
    long countByCustomerIdAndInteractionChannel(String customerId, InteractionChannel channel);

    /**
     * Count negative interactions by customer ID
     *
     * @param customerId the customer ID
     * @return count of negative interactions
     */
    @Query("SELECT COUNT(i) FROM CustomerInteraction i WHERE i.customerId = :customerId " +
           "AND i.sentiment IN ('NEGATIVE', 'VERY_NEGATIVE')")
    long countNegativeByCustomerId(@Param("customerId") String customerId);

    /**
     * Count unresolved interactions by customer ID
     *
     * @param customerId the customer ID
     * @return count of unresolved interactions
     */
    @Query("SELECT COUNT(i) FROM CustomerInteraction i WHERE i.customerId = :customerId " +
           "AND i.outcome IN ('PENDING', 'ESCALATED')")
    long countUnresolvedByCustomerId(@Param("customerId") String customerId);

    /**
     * Get average interaction duration for a customer
     *
     * @param customerId the customer ID
     * @return average duration in seconds
     */
    @Query("SELECT AVG(i.durationSeconds) FROM CustomerInteraction i WHERE i.customerId = :customerId " +
           "AND i.durationSeconds IS NOT NULL")
    Double getAverageDuration(@Param("customerId") String customerId);

    /**
     * Get total interaction duration for a customer
     *
     * @param customerId the customer ID
     * @return total duration in seconds
     */
    @Query("SELECT COALESCE(SUM(i.durationSeconds), 0) FROM CustomerInteraction i WHERE i.customerId = :customerId")
    Long getTotalDuration(@Param("customerId") String customerId);
}
