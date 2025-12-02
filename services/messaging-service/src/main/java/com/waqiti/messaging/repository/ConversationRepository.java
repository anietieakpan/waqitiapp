package com.waqiti.messaging.repository;

import com.waqiti.messaging.domain.Conversation;
import com.waqiti.messaging.domain.ConversationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {
    
    @Query("SELECT DISTINCT c FROM Conversation c JOIN c.participants p WHERE p.userId = :userId AND p.leftAt IS NULL ORDER BY c.lastMessageAt DESC")
    Page<Conversation> findActiveConversationsByUserId(String userId, Pageable pageable);
    
    @Query("SELECT c FROM Conversation c JOIN c.participants p WHERE p.userId = :userId AND c.isArchived = :archived AND p.leftAt IS NULL ORDER BY c.lastMessageAt DESC")
    Page<Conversation> findByUserIdAndArchived(String userId, boolean archived, Pageable pageable);
    
    @Query("SELECT c FROM Conversation c WHERE c.type = :type AND EXISTS (SELECT p FROM c.participants p WHERE p.userId = :userId AND p.leftAt IS NULL)")
    List<Conversation> findByTypeAndParticipant(ConversationType type, String userId);
    
    @Query("SELECT c FROM Conversation c WHERE c.type = 'DIRECT' AND SIZE(c.participants) = 2 AND EXISTS (SELECT p1 FROM c.participants p1 WHERE p1.userId = :userId1) AND EXISTS (SELECT p2 FROM c.participants p2 WHERE p2.userId = :userId2)")
    Optional<Conversation> findDirectConversation(String userId1, String userId2);
    
    @Query("SELECT c FROM Conversation c WHERE c.lastMessageAt < :before AND c.type != 'SUPPORT'")
    List<Conversation> findInactiveConversations(LocalDateTime before);
    
    List<Conversation> findByIsArchivedTrueAndUpdatedAtBefore(LocalDateTime before);
}