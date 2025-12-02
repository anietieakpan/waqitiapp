package com.waqiti.messaging.service;

import com.waqiti.messaging.domain.Conversation;
import com.waqiti.messaging.domain.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public void sendMessageNotification(String recipientId, Message message, Conversation conversation) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NEW_MESSAGE");
        notification.put("recipientId", recipientId);
        notification.put("messageId", message.getId());
        notification.put("conversationId", conversation.getId());
        notification.put("conversationName", conversation.getName());
        notification.put("preview", generatePreview(message));
        notification.put("timestamp", message.getSentAt());
        
        kafkaTemplate.send("notifications", notification);
        log.debug("Sent notification for message {} to user {}", message.getId(), recipientId);
    }
    
    private String generatePreview(Message message) {
        switch (message.getMessageType()) {
            case IMAGE:
                return "📷 Photo";
            case VIDEO:
                return "🎥 Video";
            case AUDIO:
                return "🎵 Audio";
            case FILE:
                return "📎 File";
            case LOCATION:
                return "📍 Location";
            case PAYMENT:
                return "💰 Payment";
            default:
                return "New message";
        }
    }
}