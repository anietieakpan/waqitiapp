package com.waqiti.notification.repository;

import com.waqiti.notification.model.EmailNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for email notifications
 */
@Repository
public interface EmailNotificationRepository extends MongoRepository<EmailNotification, String> {
    
    List<EmailNotification> findByUserId(String userId);
    
    List<EmailNotification> findByRecipientEmail(String recipientEmail);
    
    List<EmailNotification> findByStatus(String status);
    
    Page<EmailNotification> findByUserId(String userId, Pageable pageable);
    
    Page<EmailNotification> findByStatus(String status, Pageable pageable);
    
    @Query("{'status': 'pending', 'createdAt': {$lt: ?0}}")
    List<EmailNotification> findPendingOlderThan(LocalDateTime threshold);
    
    @Query("{'status': 'failed', 'retryCount': {$lt: ?0}}")
    List<EmailNotification> findFailedForRetry(int maxRetries);
    
    @Query("{'userId': ?0, 'createdAt': {$gte: ?1, $lte: ?2}}")
    List<EmailNotification> findByUserIdAndDateRange(String userId, LocalDateTime start, LocalDateTime end);
    
    @Query("{'status': 'sent', 'sentAt': {$gte: ?0, $lte: ?1}}")
    List<EmailNotification> findSentInDateRange(LocalDateTime start, LocalDateTime end);
    
    Optional<EmailNotification> findByMessageId(String messageId);
    
    Optional<EmailNotification> findByProviderMessageId(String providerMessageId);
    
    long countByStatus(String status);
    
    long countByUserIdAndStatus(String userId, String status);
    
    @Query("{'userId': ?0, 'status': 'sent', 'sentAt': {$gte: ?1}}")
    long countRecentSentByUser(String userId, LocalDateTime since);
    
    boolean existsByUserIdAndMessageId(String userId, String messageId);
}