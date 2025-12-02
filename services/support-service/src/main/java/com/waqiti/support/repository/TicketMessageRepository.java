package com.waqiti.support.repository;

import com.waqiti.support.domain.TicketMessage;
import com.waqiti.support.domain.MessageSenderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketMessageRepository extends JpaRepository<TicketMessage, String> {
    
    // Find messages by ticket
    List<TicketMessage> findByTicketIdOrderByCreatedAtAsc(String ticketId);
    
    Page<TicketMessage> findByTicketIdOrderByCreatedAtDesc(String ticketId, Pageable pageable);
    
    // Find messages by sender
    List<TicketMessage> findBySenderIdAndSenderType(String senderId, MessageSenderType senderType);
    
    Page<TicketMessage> findBySenderIdOrderByCreatedAtDesc(String senderId, Pageable pageable);
    
    // Find unread messages
    List<TicketMessage> findByTicketIdAndIsReadFalseOrderByCreatedAtAsc(String ticketId);
    
    @Query("SELECT tm FROM TicketMessage tm WHERE tm.ticket.assignedToAgentId = :agentId AND tm.isRead = false")
    List<TicketMessage> findUnreadMessagesByAgent(@Param("agentId") String agentId);
    
    // Find internal messages
    List<TicketMessage> findByTicketIdAndIsInternalTrueOrderByCreatedAtAsc(String ticketId);
    
    // Find public messages
    List<TicketMessage> findByTicketIdAndIsPublicTrueOrderByCreatedAtAsc(String ticketId);
    
    // Find latest message for ticket
    Optional<TicketMessage> findTopByTicketIdOrderByCreatedAtDesc(String ticketId);
    
    // Find messages within date range
    @Query("SELECT tm FROM TicketMessage tm WHERE tm.ticket.id = :ticketId AND tm.createdAt BETWEEN :startDate AND :endDate ORDER BY tm.createdAt ASC")
    List<TicketMessage> findByTicketIdAndDateRange(
        @Param("ticketId") String ticketId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    // Count messages by ticket
    long countByTicketId(String ticketId);
    
    // Count unread messages by ticket
    long countByTicketIdAndIsReadFalse(String ticketId);
    
    // Count messages by sender type
    long countByTicketIdAndSenderType(String ticketId, MessageSenderType senderType);
    
    // Mark messages as read
    @Modifying
    @Query("UPDATE TicketMessage tm SET tm.isRead = true, tm.readAt = :readAt, tm.readBy = :readBy WHERE tm.ticket.id = :ticketId AND tm.isRead = false")
    int markMessagesAsRead(@Param("ticketId") String ticketId, @Param("readAt") LocalDateTime readAt, @Param("readBy") String readBy);
    
    @Modifying
    @Query("UPDATE TicketMessage tm SET tm.isRead = true, tm.readAt = :readAt, tm.readBy = :readBy WHERE tm.id = :messageId")
    int markMessageAsRead(@Param("messageId") String messageId, @Param("readAt") LocalDateTime readAt, @Param("readBy") String readBy);
    
    // Find messages with attachments
    @Query("SELECT tm FROM TicketMessage tm WHERE tm.ticket.id = :ticketId AND EXISTS (SELECT ta FROM TicketAttachment ta WHERE ta.message.id = tm.id)")
    List<TicketMessage> findMessagesWithAttachments(@Param("ticketId") String ticketId);
    
    // Find messages by content (full text search) - secured with proper parameterization
    @Query(value = "SELECT tm.* FROM ticket_messages tm WHERE tm.ticket_id = :ticketId AND to_tsvector('english', tm.content) @@ plainto_tsquery('english', :searchQuery)", nativeQuery = true)
    List<TicketMessage> searchMessageContent(@Param("ticketId") String ticketId, @Param("searchQuery") String searchQuery);
    
    // Alternative secure search using JPQL with LIKE for broader database compatibility
    @Query("SELECT tm FROM TicketMessage tm WHERE tm.ticket.id = :ticketId AND (LOWER(tm.content) LIKE LOWER(CONCAT('%', :searchQuery, '%')) OR LOWER(tm.subject) LIKE LOWER(CONCAT('%', :searchQuery, '%')))")
    List<TicketMessage> searchMessageContentSecure(@Param("ticketId") String ticketId, @Param("searchQuery") String searchQuery);
    
    // Statistics queries
    @Query("SELECT COUNT(tm) FROM TicketMessage tm WHERE tm.createdAt >= :since")
    long countMessagesSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(tm) FROM TicketMessage tm WHERE tm.senderType = :senderType AND tm.createdAt >= :since")
    long countMessagesBySenderTypeSince(@Param("senderType") MessageSenderType senderType, @Param("since") LocalDateTime since);
    
    @Query("SELECT AVG(EXTRACT(EPOCH FROM (tm2.createdAt - tm1.createdAt))/60) FROM TicketMessage tm1 JOIN TicketMessage tm2 ON tm1.ticket.id = tm2.ticket.id WHERE tm1.senderType = :senderType1 AND tm2.senderType = :senderType2 AND tm2.createdAt > tm1.createdAt AND tm1.createdAt >= :since")
    Double getAverageResponseTimeMinutes(
        @Param("senderType1") MessageSenderType senderType1, 
        @Param("senderType2") MessageSenderType senderType2, 
        @Param("since") LocalDateTime since
    );
    
    // Find first response time for tickets
    @Query("SELECT tm FROM TicketMessage tm WHERE tm.ticket.id = :ticketId AND tm.senderType = :senderType ORDER BY tm.createdAt ASC")
    List<TicketMessage> findFirstResponseByType(@Param("ticketId") String ticketId, @Param("senderType") MessageSenderType senderType);
    
    // Delete old messages (for data retention)
    @Modifying
    @Query("DELETE FROM TicketMessage tm WHERE tm.createdAt < :cutoffDate AND tm.isInternal = false")
    int deleteMessagesOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // Find messages needing AI analysis
    @Query("SELECT tm FROM TicketMessage tm WHERE tm.senderType = 'CUSTOMER' AND tm.aiAnalyzed = false ORDER BY tm.createdAt ASC")
    List<TicketMessage> findMessagesForAIAnalysis(Pageable pageable);
    
    // Update AI analysis flag
    @Modifying
    @Query("UPDATE TicketMessage tm SET tm.aiAnalyzed = true WHERE tm.id = :messageId")
    int markAsAIAnalyzed(@Param("messageId") String messageId);
}