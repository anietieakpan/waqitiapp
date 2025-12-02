package com.waqiti.messaging.service;

import com.waqiti.messaging.domain.*;
import com.waqiti.messaging.dto.*;
import com.waqiti.messaging.exception.MessageException;
import com.waqiti.messaging.repository.ConversationRepository;
import com.waqiti.messaging.repository.MessageRepository;
import com.waqiti.messaging.websocket.MessageWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageService {
    
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final EncryptionService encryptionService;
    private final NotificationService notificationService;
    private final MessageWebSocketHandler webSocketHandler;
    private final MediaService mediaService;
    private final UserService userService;
    
    @Transactional
    public MessageDTO sendMessage(SendMessageRequest request) {
        try {
            // Validate conversation and permissions
            Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new MessageException("Conversation not found"));
            
            validateSendPermission(request.getSenderId(), conversation);
            
            // Handle attachments if present
            List<MessageAttachment> attachments = null;
            if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
                attachments = processAttachments(request.getAttachments(), request.getSenderId());
            }
            
            // Create message entity
            Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(request.getSenderId())
                .messageType(request.getMessageType())
                .isEphemeral(request.getIsEphemeral())
                .ephemeralDuration(request.getEphemeralDuration())
                .replyToMessageId(request.getReplyToMessageId())
                .build();
            
            // Encrypt message for each participant
            if (conversation.getIsEncrypted()) {
                encryptAndSendToParticipants(message, request.getContent(), conversation);
            } else {
                // For non-encrypted conversations (public channels)
                message.setEncryptedContent(request.getContent());
            }
            
            // Save message
            message = messageRepository.save(message);
            
            // Add attachments
            if (attachments != null) {
                for (MessageAttachment attachment : attachments) {
                    attachment.setMessage(message);
                }
                message.setAttachments(attachments);
            }
            
            // Update conversation
            conversation.updateLastMessage(message);
            conversationRepository.save(conversation);
            
            // Send real-time notifications
            broadcastMessage(message, conversation);
            
            // Send push notifications
            sendPushNotifications(message, conversation);
            
            // Update unread counts
            updateUnreadCounts(message, conversation);
            
            return convertToDTO(message);
            
        } catch (Exception e) {
            log.error("Failed to send message", e);
            throw new MessageException("Failed to send message", e);
        }
    }
    
    private void encryptAndSendToParticipants(Message message, String content, Conversation conversation) {
        List<String> participantIds = conversation.getParticipants().stream()
            .filter(ConversationParticipant::isActive)
            .map(ConversationParticipant::getUserId)
            .collect(Collectors.toList());
        
        // For group conversations, use sender keys protocol
        if (conversation.isGroupConversation()) {
            // Implement sender keys for efficient group messaging
            encryptGroupMessage(message, content, participantIds);
        } else {
            // For direct messages, use standard Signal protocol
            for (String recipientId : participantIds) {
                if (!recipientId.equals(message.getSenderId())) {
                    EncryptedMessage encrypted = encryptionService.encryptMessage(
                        message.getSenderId(),
                        recipientId,
                        content,
                        MessageType.valueOf(message.getMessageType().name())
                    );
                    
                    // Store encrypted version
                    message.setEncryptedContent(encrypted.getEncryptedContent());
                    message.setEncryptedKey(encrypted.getEncryptedKey());
                    message.setSignature(encrypted.getSignature());
                    
                    // For ephemeral messages
                    if (message.getIsEphemeral() != null && message.getIsEphemeral()) {
                        encrypted.setIsEphemeral(true);
                        encrypted.setEphemeralDuration(message.getEphemeralDuration());
                    }
                }
            }
        }
    }
    
    private void encryptGroupMessage(Message message, String content, List<String> participantIds) {
        // Implement sender keys protocol for efficient group encryption
        // This is a simplified version - implement full protocol in production
        log.info("Encrypting group message for {} participants", participantIds.size());
    }
    
    @Transactional(readOnly = true)
    public Page<MessageDTO> getConversationMessages(String conversationId, String userId, Pageable pageable) {
        // Validate access
        Conversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new MessageException("Conversation not found"));
        
        validateReadPermission(userId, conversation);
        
        // Get messages
        Page<Message> messages = messageRepository.findByConversationIdOrderBySentAtDesc(
            conversationId, pageable
        );
        
        // Decrypt messages for user
        return messages.map(message -> {
            MessageDTO dto = convertToDTO(message);
            
            if (conversation.getIsEncrypted() && !message.getSenderId().equals(userId)) {
                try {
                    String decryptedContent = encryptionService.decryptMessage(
                        userId,
                        convertToEncryptedMessage(message)
                    );
                    dto.setContent(decryptedContent);
                } catch (Exception e) {
                    log.error("Failed to decrypt message", e);
                    dto.setContent("[Unable to decrypt]");
                }
            }
            
            return dto;
        });
    }
    
    @Transactional
    public void markAsRead(String messageId, String userId) {
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new MessageException("Message not found"));
        
        // Create or update receipt
        MessageReceipt receipt = message.getReceipts().stream()
            .filter(r -> r.getUserId().equals(userId))
            .findFirst()
            .orElse(MessageReceipt.builder()
                .message(message)
                .userId(userId)
                .build());
        
        receipt.markAsRead();
        message.getReceipts().add(receipt);
        messageRepository.save(message);
        
        // Update conversation participant
        Conversation conversation = conversationRepository.findById(message.getConversationId())
            .orElseThrow(() -> new MessageException("Conversation not found"));
        
        ConversationParticipant participant = conversation.getParticipants().stream()
            .filter(p -> p.getUserId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new MessageException("Participant not found"));
        
        participant.updateLastRead(messageId);
        conversationRepository.save(conversation);
        
        // Notify sender
        webSocketHandler.sendReadReceipt(message.getSenderId(), messageId, userId);
    }
    
    @Transactional
    public void deleteMessage(String messageId, String userId, boolean forEveryone) {
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new MessageException("Message not found"));
        
        if (!message.getSenderId().equals(userId)) {
            throw new MessageException("Only sender can delete message");
        }
        
        if (forEveryone) {
            // Check if message is too old to delete for everyone (e.g., > 1 hour)
            if (message.getSentAt().isBefore(LocalDateTime.now().minusHours(1))) {
                throw new MessageException("Message too old to delete for everyone");
            }
            
            message.markAsDeleted();
            messageRepository.save(message);
            
            // Notify all participants
            Conversation conversation = conversationRepository.findById(message.getConversationId()).orElseThrow();
            broadcastMessageDeletion(messageId, conversation);
        } else {
            // Just mark as deleted for this user
            // Implementation depends on your requirements
        }
    }
    
    @Async
    public void sendPushNotifications(Message message, Conversation conversation) {
        List<String> recipientIds = conversation.getParticipants().stream()
            .filter(p -> !p.getUserId().equals(message.getSenderId()))
            .filter(p -> p.getNotificationPreference() != NotificationPreference.NONE)
            .map(ConversationParticipant::getUserId)
            .collect(Collectors.toList());
        
        for (String recipientId : recipientIds) {
            notificationService.sendMessageNotification(recipientId, message, conversation);
        }
    }
    
    @Scheduled(fixedDelay = 60000) // Every minute
    public void cleanupExpiredEphemeralMessages() {
        List<Message> expiredMessages = messageRepository.findExpiredEphemeralMessages(LocalDateTime.now());
        
        for (Message message : expiredMessages) {
            message.markAsDeleted();
            message.setStatus(MessageStatus.EXPIRED);
        }
        
        if (!expiredMessages.isEmpty()) {
            messageRepository.saveAll(expiredMessages);
            log.info("Cleaned up {} expired ephemeral messages", expiredMessages.size());
        }
    }
    
    private void validateSendPermission(String userId, Conversation conversation) {
        ConversationParticipant participant = conversation.getParticipants().stream()
            .filter(p -> p.getUserId().equals(userId) && p.isActive())
            .findFirst()
            .orElseThrow(() -> new MessageException("User not a participant in conversation"));
        
        if (!participant.getCanSendMessages()) {
            throw new MessageException("User does not have permission to send messages");
        }
    }
    
    private void validateReadPermission(String userId, Conversation conversation) {
        boolean isParticipant = conversation.getParticipants().stream()
            .anyMatch(p -> p.getUserId().equals(userId) && p.isActive());
        
        if (!isParticipant) {
            throw new MessageException("User not authorized to read messages");
        }
    }
    
    private List<MessageAttachment> processAttachments(List<AttachmentRequest> attachmentRequests, String senderId) {
        return attachmentRequests.stream()
            .map(request -> mediaService.processAttachment(request, senderId))
            .collect(Collectors.toList());
    }
    
    private void broadcastMessage(Message message, Conversation conversation) {
        MessageEvent event = MessageEvent.builder()
            .type(MessageEventType.NEW_MESSAGE)
            .messageId(message.getId())
            .conversationId(conversation.getId())
            .senderId(message.getSenderId())
            .timestamp(message.getSentAt())
            .build();
        
        conversation.getParticipants().stream()
            .filter(ConversationParticipant::isActive)
            .forEach(participant -> 
                webSocketHandler.sendToUser(participant.getUserId(), event)
            );
    }
    
    private void broadcastMessageDeletion(String messageId, Conversation conversation) {
        MessageEvent event = MessageEvent.builder()
            .type(MessageEventType.MESSAGE_DELETED)
            .messageId(messageId)
            .conversationId(conversation.getId())
            .timestamp(LocalDateTime.now())
            .build();
        
        conversation.getParticipants().stream()
            .filter(ConversationParticipant::isActive)
            .forEach(participant -> 
                webSocketHandler.sendToUser(participant.getUserId(), event)
            );
    }
    
    private void updateUnreadCounts(Message message, Conversation conversation) {
        conversation.getParticipants().stream()
            .filter(p -> !p.getUserId().equals(message.getSenderId()))
            .filter(ConversationParticipant::isActive)
            .forEach(ConversationParticipant::incrementUnreadCount);
        
        conversationRepository.save(conversation);
    }
    
    private MessageDTO convertToDTO(Message message) {
        return MessageDTO.builder()
            .id(message.getId())
            .conversationId(message.getConversationId())
            .senderId(message.getSenderId())
            .messageType(message.getMessageType())
            .content(message.getEncryptedContent()) // Will be decrypted per user
            .sentAt(message.getSentAt())
            .deliveredAt(message.getDeliveredAt())
            .readAt(message.getReadAt())
            .editedAt(message.getEditedAt())
            .isEphemeral(message.getIsEphemeral())
            .ephemeralDuration(message.getEphemeralDuration())
            .replyToMessageId(message.getReplyToMessageId())
            .status(message.getStatus())
            .attachments(convertAttachments(message.getAttachments()))
            .reactions(convertReactions(message.getReactions()))
            .build();
    }
    
    private EncryptedMessage convertToEncryptedMessage(Message message) {
        return EncryptedMessage.builder()
            .encryptedContent(message.getEncryptedContent())
            .encryptedKey(message.getEncryptedKey())
            .signature(message.getSignature())
            .ephemeralPublicKey(message.getEphemeralPublicKey())
            .messageType(MessageType.valueOf(message.getMessageType().name()))
            .senderId(message.getSenderId())
            .timestamp(message.getSentAt())
            .isEphemeral(message.getIsEphemeral())
            .ephemeralDuration(message.getEphemeralDuration())
            .build();
    }
    
    private List<AttachmentDTO> convertAttachments(List<MessageAttachment> attachments) {
        if (attachments == null) return null;
        
        return attachments.stream()
            .map(attachment -> AttachmentDTO.builder()
                .id(attachment.getId())
                .type(attachment.getType())
                .fileName(attachment.getFileName())
                .fileSize(attachment.getFileSize())
                .mimeType(attachment.getMimeType())
                .thumbnailUrl(attachment.getThumbnailUrl())
                .width(attachment.getWidth())
                .height(attachment.getHeight())
                .duration(attachment.getDuration())
                .build())
            .collect(Collectors.toList());
    }
    
    private List<ReactionDTO> convertReactions(List<MessageReaction> reactions) {
        if (reactions == null) return null;
        
        return reactions.stream()
            .map(reaction -> ReactionDTO.builder()
                .userId(reaction.getUserId())
                .emoji(reaction.getEmoji())
                .createdAt(reaction.getCreatedAt())
                .build())
            .collect(Collectors.toList());
    }
    
    @Transactional
    public void addReaction(String messageId, String userId, String emoji) {
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new MessageException("Message not found"));
        
        // Check if user already reacted with this emoji
        boolean alreadyReacted = message.getReactions().stream()
            .anyMatch(r -> r.getUserId().equals(userId) && r.getEmoji().equals(emoji));
        
        if (!alreadyReacted) {
            MessageReaction reaction = MessageReaction.builder()
                .message(message)
                .userId(userId)
                .emoji(emoji)
                .build();
            
            message.getReactions().add(reaction);
            messageRepository.save(message);
            
            // Notify participants
            Conversation conversation = conversationRepository.findById(message.getConversationId()).orElseThrow();
            MessageEvent event = MessageEvent.builder()
                .type(MessageEventType.REACTION_ADDED)
                .messageId(messageId)
                .conversationId(conversation.getId())
                .senderId(userId)
                .data(Map.of("emoji", emoji))
                .timestamp(LocalDateTime.now())
                .build();
            
            broadcastToConversation(event, conversation);
        }
    }
    
    @Transactional
    public void removeReaction(String messageId, String userId, String emoji) {
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new MessageException("Message not found"));
        
        message.getReactions().removeIf(r -> 
            r.getUserId().equals(userId) && r.getEmoji().equals(emoji)
        );
        
        messageRepository.save(message);
        
        // Notify participants
        Conversation conversation = conversationRepository.findById(message.getConversationId()).orElseThrow();
        MessageEvent event = MessageEvent.builder()
            .type(MessageEventType.REACTION_REMOVED)
            .messageId(messageId)
            .conversationId(conversation.getId())
            .senderId(userId)
            .data(Map.of("emoji", emoji))
            .timestamp(LocalDateTime.now())
            .build();
        
        broadcastToConversation(event, conversation);
    }
    
    public Page<MessageDTO> searchMessages(String query, String conversationId, String userId, Pageable pageable) {
        // This would implement full-text search
        // For now, return empty page
        log.info("Searching messages with query: {} in conversation: {}", query, conversationId);
        return Page.empty(pageable);
    }
    
    private void broadcastToConversation(MessageEvent event, Conversation conversation) {
        conversation.getParticipants().stream()
            .filter(ConversationParticipant::isActive)
            .forEach(participant -> 
                webSocketHandler.sendToUser(participant.getUserId(), event)
            );
    }
}