package com.waqiti.business.repository;

import com.waqiti.business.domain.EmailOutbox;
import com.waqiti.business.domain.EmailOutbox.EmailStatus;
import com.waqiti.business.domain.EmailOutbox.EmailType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Email Outbox
 *
 * Provides queries for email processing and monitoring
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    /**
     * Find emails ready to send (PENDING or RETRY_SCHEDULED with retry time passed)
     */
    @Query("SELECT e FROM EmailOutbox e WHERE " +
            "(e.status = 'PENDING' OR " +
            "(e.status = 'RETRY_SCHEDULED' AND e.nextRetryAt <= :now)) " +
            "ORDER BY e.priority ASC, e.createdAt ASC")
    List<EmailOutbox> findEmailsReadyToSend(@Param("now") LocalDateTime now);

    /**
     * Find emails by status
     */
    List<EmailOutbox> findByStatusOrderByCreatedAtDesc(EmailStatus status);

    /**
     * Find emails by type and status
     */
    List<EmailOutbox> findByEmailTypeAndStatus(EmailType emailType, EmailStatus status);

    /**
     * Find emails by recipient
     */
    List<EmailOutbox> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    /**
     * Find email by SendGrid message ID
     */
    Optional<EmailOutbox> findBySendgridMessageId(String sendgridMessageId);

    /**
     * Count emails by status
     */
    long countByStatus(EmailStatus status);

    /**
     * Find failed emails (max retries exceeded)
     */
    @Query("SELECT e FROM EmailOutbox e WHERE e.status = 'FAILED' " +
            "AND e.createdAt >= :since ORDER BY e.createdAt DESC")
    List<EmailOutbox> findFailedEmailsSince(@Param("since") LocalDateTime since);

    /**
     * Find emails pending for too long (stuck emails)
     */
    @Query("SELECT e FROM EmailOutbox e WHERE e.status IN ('PENDING', 'SENDING') " +
            "AND e.createdAt < :threshold")
    List<EmailOutbox> findStuckEmails(@Param("threshold") LocalDateTime threshold);

    /**
     * Count emails sent today
     */
    @Query("SELECT COUNT(e) FROM EmailOutbox e WHERE e.status = 'SENT' " +
            "AND e.sentAt >= :startOfDay")
    long countEmailsSentToday(@Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Get email statistics for dashboard
     */
    @Query("SELECT e.status, COUNT(e) FROM EmailOutbox e " +
            "WHERE e.createdAt >= :since GROUP BY e.status")
    List<Object[]> getEmailStatistics(@Param("since") LocalDateTime since);
}
