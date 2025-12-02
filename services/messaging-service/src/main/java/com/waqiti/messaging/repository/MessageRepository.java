package com.waqiti.messaging.repository;

import com.waqiti.messaging.domain.Message;
import com.waqiti.messaging.domain.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {
    
    Page<Message> findByConversationIdOrderBySentAtDesc(String conversationId, Pageable pageable);
    
    Page<Message> findByConversationIdAndSentAtBeforeOrderBySentAtDesc(
        String conversationId, LocalDateTime before, Pageable pageable);
    
    @Query("SELECT m FROM Message m WHERE m.isEphemeral = true AND m.expiresAt < :now AND m.status != :expired")
    List<Message> findExpiredEphemeralMessages(LocalDateTime now, MessageStatus expired);
    
    default List<Message> findExpiredEphemeralMessages(LocalDateTime now) {
        return findExpiredEphemeralMessages(now, MessageStatus.EXPIRED);
    }
    
    List<Message> findBySenderIdAndConversationId(String senderId, String conversationId);
    
    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversationId = :conversationId AND m.sentAt > :since")
    long countMessagesSince(String conversationId, LocalDateTime since);
    
    @Query("SELECT m FROM Message m WHERE m.conversationId = :conversationId AND m.id > :afterMessageId ORDER BY m.sentAt")
    List<Message> findMessagesAfter(String conversationId, String afterMessageId);
    
    void deleteByConversationIdAndDeletedAtNotNull(String conversationId);
}