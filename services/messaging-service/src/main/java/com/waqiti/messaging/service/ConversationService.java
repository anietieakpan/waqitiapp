package com.waqiti.messaging.service;

import com.waqiti.messaging.domain.*;
import com.waqiti.messaging.dto.*;
import com.waqiti.messaging.exception.ConversationException;
import com.waqiti.messaging.repository.ConversationParticipantRepository;
import com.waqiti.messaging.repository.ConversationRepository;
import com.waqiti.messaging.websocket.MessageWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationService {
    
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final KeyManagementService keyManagementService;
    private final MessageWebSocketHandler webSocketHandler;
    private final UserService userService;
    
    @Transactional
    public ConversationDTO createConversation(CreateConversationRequest request) {
        try {
            // For direct conversations, check if one already exists
            if (request.getType() == ConversationType.DIRECT && request.getParticipantIds().size() == 1) {
                String otherUserId = request.getParticipantIds().get(0);
                var existing = conversationRepository.findDirectConversation(request.getCreatorId(), otherUserId);
                if (existing.isPresent()) {
                    return convertToDTO(existing.get());
                }
            }
            
            // Create conversation
            Conversation conversation = Conversation.builder()
                .type(request.getType())
                .name(request.getName())
                .description(request.getDescription())
                .avatarUrl(request.getAvatarUrl())
                .isEncrypted(request.getIsEncrypted() != null ? request.getIsEncrypted() : true)
                .encryptionType(request.getIsEncrypted() ? EncryptionType.E2E : EncryptionType.NONE)
                .ephemeralMessagesEnabled(request.getEphemeralMessagesEnabled())
                .defaultEphemeralDuration(request.getDefaultEphemeralDuration())
                .adminApprovalRequired(request.getAdminApprovalRequired())
                .build();
            
            conversation = conversationRepository.save(conversation);
            
            // Add creator as participant
            ConversationParticipant creator = ConversationParticipant.builder()
                .conversation(conversation)
                .userId(request.getCreatorId())
                .role(ParticipantRole.OWNER)
                .isAdmin(true)
                .canAddParticipants(true)
                .build();
            
            conversation.addParticipant(creator);
            
            // Add other participants
            for (String participantId : request.getParticipantIds()) {
                if (!participantId.equals(request.getCreatorId())) {
                    ConversationParticipant participant = ConversationParticipant.builder()
                        .conversation(conversation)
                        .userId(participantId)
                        .role(ParticipantRole.MEMBER)
                        .build();
                    
                    conversation.addParticipant(participant);
                }
            }
            
            conversation = conversationRepository.save(conversation);
            
            // Initialize encryption keys for all participants
            if (conversation.getIsEncrypted()) {
                initializeConversationEncryption(conversation);
            }
            
            // Notify participants
            notifyNewConversation(conversation);
            
            return convertToDTO(conversation);
            
        } catch (Exception e) {
            log.error("Failed to create conversation", e);
            throw new ConversationException("Failed to create conversation", e);
        }
    }
    
    @Transactional(readOnly = true)
    public Page<ConversationDTO> getUserConversations(String userId, boolean archived, Pageable pageable) {
        Page<Conversation> conversations = conversationRepository.findByUserIdAndArchived(userId, archived, pageable);
        return conversations.map(this::convertToDTO);
    }
    
    @Transactional(readOnly = true)
    public ConversationDTO getConversation(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ConversationException("Conversation not found"));
        
        // Check if user is participant
        boolean isParticipant = conversation.getParticipants().stream()
            .anyMatch(p -> p.getUserId().equals(userId) && p.isActive());
        
        if (!isParticipant) {
            throw new ConversationException("User not authorized to view conversation");
        }
        
        return convertToDTO(conversation);
    }
    
    @Transactional
    public ConversationDTO updateConversation(String conversationId, UpdateConversationRequest request, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ConversationException("Conversation not found"));
        
        // Check permissions
        ConversationParticipant participant = getParticipant(conversation, userId);
        if (!participant.getIsAdmin()) {
            throw new ConversationException("User not authorized to update conversation");
        }
        
        // Update fields
        if (request.getName() != null) {
            conversation.setName(request.getName());
        }
        if (request.getDescription() != null) {
            conversation.setDescription(request.getDescription());
        }
        if (request.getAvatarUrl() != null) {
            conversation.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getEphemeralMessagesEnabled() != null) {
            conversation.setEphemeralMessagesEnabled(request.getEphemeralMessagesEnabled());
        }
        if (request.getDefaultEphemeralDuration() != null) {
            conversation.setDefaultEphemeralDuration(request.getDefaultEphemeralDuration());
        }
        
        conversation = conversationRepository.save(conversation);
        
        // Notify participants
        notifyConversationUpdate(conversation);
        
        return convertToDTO(conversation);
    }
    
    @Transactional
    public void addParticipants(String conversationId, List<String> userIds, String addedBy) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ConversationException("Conversation not found"));
        
        // Check permissions
        ConversationParticipant adder = getParticipant(conversation, addedBy);
        if (!adder.getCanAddParticipants()) {
            throw new ConversationException("User not authorized to add participants");
        }
        
        // Check capacity
        if (!conversation.canAddParticipant()) {
            throw new ConversationException("Conversation has reached maximum participants");
        }
        
        // Add participants
        for (String userId : userIds) {
            // Check if already participant
            boolean exists = conversation.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(userId));
            
            if (!exists) {
                ConversationParticipant participant = ConversationParticipant.builder()
                    .conversation(conversation)
                    .userId(userId)
                    .role(ParticipantRole.MEMBER)
                    .build();
                
                conversation.addParticipant(participant);
                
                // Initialize encryption for new participant
                if (conversation.getIsEncrypted()) {
                    keyManagementService.initializeUserKeys(userId, "default");
                }
            }
        }
        
        conversationRepository.save(conversation);
        
        // Notify all participants
        notifyParticipantsAdded(conversation, userIds, addedBy);
    }
    
    @Transactional
    public void removeParticipant(String conversationId, String participantId, String removedBy) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ConversationException("Conversation not found"));
        
        // Check permissions
        ConversationParticipant remover = getParticipant(conversation, removedBy);
        if (!remover.getIsAdmin() && !removedBy.equals(participantId)) {
            throw new ConversationException("User not authorized to remove participants");
        }
        
        // Find and remove participant
        ConversationParticipant participant = getParticipant(conversation, participantId);
        participant.setLeftAt(LocalDateTime.now());
        
        conversationRepository.save(conversation);
        
        // Notify participants
        notifyParticipantRemoved(conversation, participantId, removedBy);
    }
    
    @Transactional
    public void leaveConversation(String conversationId, String userId) {
        removeParticipant(conversationId, userId, userId);
    }
    
    @Transactional
    public void archiveConversation(String conversationId, String userId, boolean archive) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ConversationException("Conversation not found"));
        
        // Verify user is participant
        getParticipant(conversation, userId);
        
        // This would typically be per-user, but for simplicity we're doing it globally
        conversation.setIsArchived(archive);
        conversationRepository.save(conversation);
    }
    
    @Transactional
    public void muteConversation(String conversationId, String userId, Integer durationMinutes) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ConversationException("Conversation not found"));
        
        ConversationParticipant participant = getParticipant(conversation, userId);
        participant.setNotificationPreference(NotificationPreference.NONE);
        
        if (durationMinutes != null) {
            // Set mute expiry
            conversation.setMutedUntil(LocalDateTime.now().plusMinutes(durationMinutes));
        }
        
        conversationRepository.save(conversation);
    }
    
    @Transactional
    public void unmuteConversation(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ConversationException("Conversation not found"));
        
        ConversationParticipant participant = getParticipant(conversation, userId);
        participant.setNotificationPreference(NotificationPreference.ALL);
        conversation.setMutedUntil(null);
        
        conversationRepository.save(conversation);
    }
    
    private void initializeConversationEncryption(Conversation conversation) {
        for (ConversationParticipant participant : conversation.getParticipants()) {
            if (!keyManagementService.hasKeys(participant.getUserId())) {
                keyManagementService.initializeUserKeys(participant.getUserId(), "default");
            }
        }
        
        if (conversation.isGroupConversation()) {
            // Initialize group key for sender keys protocol
            // Implementation depends on your group encryption strategy
        }
    }
    
    private ConversationParticipant getParticipant(Conversation conversation, String userId) {
        return conversation.getParticipants().stream()
            .filter(p -> p.getUserId().equals(userId) && p.isActive())
            .findFirst()
            .orElseThrow(() -> new ConversationException("User not a participant"));
    }
    
    private void notifyNewConversation(Conversation conversation) {
        MessageEvent event = MessageEvent.builder()
            .type(MessageEventType.CONVERSATION_UPDATED)
            .conversationId(conversation.getId())
            .timestamp(LocalDateTime.now())
            .data(convertToDTO(conversation))
            .build();
        
        conversation.getParticipants().forEach(p -> 
            webSocketHandler.sendToUser(p.getUserId(), event)
        );
    }
    
    private void notifyConversationUpdate(Conversation conversation) {
        notifyNewConversation(conversation);
    }
    
    private void notifyParticipantsAdded(Conversation conversation, List<String> addedUserIds, String addedBy) {
        MessageEvent event = MessageEvent.builder()
            .type(MessageEventType.PARTICIPANT_JOINED)
            .conversationId(conversation.getId())
            .senderId(addedBy)
            .timestamp(LocalDateTime.now())
            .data(Map.of("addedUsers", addedUserIds))
            .build();
        
        conversation.getParticipants().stream()
            .filter(ConversationParticipant::isActive)
            .forEach(p -> webSocketHandler.sendToUser(p.getUserId(), event));
    }
    
    private void notifyParticipantRemoved(Conversation conversation, String removedUserId, String removedBy) {
        MessageEvent event = MessageEvent.builder()
            .type(MessageEventType.PARTICIPANT_LEFT)
            .conversationId(conversation.getId())
            .senderId(removedBy)
            .timestamp(LocalDateTime.now())
            .data(Map.of("removedUser", removedUserId))
            .build();
        
        conversation.getParticipants().stream()
            .filter(ConversationParticipant::isActive)
            .forEach(p -> webSocketHandler.sendToUser(p.getUserId(), event));
    }
    
    private ConversationDTO convertToDTO(Conversation conversation) {
        // Get participant info
        List<ParticipantDTO> participants = conversation.getParticipants().stream()
            .filter(ConversationParticipant::isActive)
            .map(this::convertParticipant)
            .collect(Collectors.toList());
        
        return ConversationDTO.builder()
            .id(conversation.getId())
            .type(conversation.getType())
            .name(conversation.getName())
            .description(conversation.getDescription())
            .avatarUrl(conversation.getAvatarUrl())
            .participants(participants)
            .participantCount(participants.size())
            .createdAt(conversation.getCreatedAt())
            .lastMessageAt(conversation.getLastMessageAt())
            .lastMessagePreview(conversation.getLastMessagePreview())
            .isEncrypted(conversation.getIsEncrypted())
            .encryptionType(conversation.getEncryptionType())
            .isArchived(conversation.getIsArchived())
            .isMuted(conversation.getIsMuted())
            .ephemeralMessagesEnabled(conversation.getEphemeralMessagesEnabled())
            .defaultEphemeralDuration(conversation.getDefaultEphemeralDuration())
            .build();
    }
    
    private ParticipantDTO convertParticipant(ConversationParticipant participant) {
        UserInfo userInfo = userService.getUserInfo(participant.getUserId());
        
        return ParticipantDTO.builder()
            .userId(participant.getUserId())
            .userName(userInfo.getName())
            .userAvatar(userInfo.getAvatarUrl())
            .role(participant.getRole())
            .joinedAt(participant.getJoinedAt())
            .isAdmin(participant.getIsAdmin())
            .nickname(participant.getNickname())
            .isOnline(webSocketHandler.isUserOnline(participant.getUserId()))
            .build();
    }
}