package com.waqiti.messaging.repository;

import com.waqiti.messaging.domain.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, String> {
    
    List<ConversationParticipant> findByUserId(String userId);
    
    List<ConversationParticipant> findByUserIdAndLeftAtIsNull(String userId);
    
    Optional<ConversationParticipant> findByConversationIdAndUserId(String conversationId, String userId);
    
    long countByConversationIdAndLeftAtIsNull(String conversationId);
}