package com.waqiti.support.domain;

import com.waqiti.support.dto.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "chat_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    
    @Id
    private String sessionId;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private LocalDateTime startTime;
    
    private LocalDateTime endTime;
    
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.ACTIVE;
    
    private String agentId;
    
    @Transient
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "chat_session_metadata", joinColumns = @JoinColumn(name = "session_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    private LocalDateTime lastActivity;
    
    public enum Status {
        ACTIVE, TRANSFERRED, ENDED, EXPIRED
    }
    
    public void addMessage(ChatMessage message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
        this.lastActivity = LocalDateTime.now();
    }
    
    public int getMessageCount() {
        return messages != null ? messages.size() : 0;
    }
    
    public List<ChatMessage> getRecentMessages(int count) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        int start = Math.max(0, messages.size() - count);
        return messages.subList(start, messages.size());
    }
    
    public boolean isActive() {
        return status == Status.ACTIVE;
    }
    
    public boolean isExpired(int timeoutMinutes) {
        if (lastActivity == null) {
            return false;
        }
        return lastActivity.isBefore(LocalDateTime.now().minusMinutes(timeoutMinutes));
    }
}