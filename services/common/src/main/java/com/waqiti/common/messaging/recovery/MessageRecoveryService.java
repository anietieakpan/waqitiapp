package com.waqiti.common.messaging.recovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * CRITICAL PRODUCTION FIX - MessageRecoveryService  
 * Handles recovery of failed messages from dead letter queue
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageRecoveryService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Recover a failed message by republishing to original topic
     */
    public CompletableFuture<RecoveryResult> recoverMessage(String messageId, Object message, String originalTopic) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Attempting to recover message {} to topic {}", messageId, originalTopic);
                
                // Add recovery headers
                Map<String, Object> headers = Map.of(
                    "X-Recovery-Timestamp", LocalDateTime.now().toString(),
                    "X-Recovery-Attempt", "true",
                    "X-Original-Message-Id", messageId
                );
                
                // Republish message
                kafkaTemplate.send(originalTopic, message);
                
                log.info("Successfully recovered message {} to topic {}", messageId, originalTopic);
                
                return RecoveryResult.builder()
                    .messageId(messageId)
                    .success(true)
                    .originalTopic(originalTopic)
                    .recoveredAt(LocalDateTime.now())
                    .build();
                    
            } catch (Exception e) {
                log.error("Failed to recover message {} to topic {}", messageId, originalTopic, e);
                
                return RecoveryResult.builder()
                    .messageId(messageId)
                    .success(false)
                    .originalTopic(originalTopic)
                    .failureReason(e.getMessage())
                    .failedAt(LocalDateTime.now())
                    .build();
            }
        });
    }
    
    /**
     * Bulk recover multiple messages
     */
    public CompletableFuture<BulkRecoveryResult> recoverMessages(Map<String, Object> messages, String originalTopic) {
        return CompletableFuture.supplyAsync(() -> {
            BulkRecoveryResult result = BulkRecoveryResult.builder().build();
            
            messages.forEach((messageId, message) -> {
                try {
                    RecoveryResult recovery = recoverMessage(messageId, message, originalTopic).get();
                    if (recovery.isSuccess()) {
                        result.addSuccess(recovery);
                    } else {
                        result.addFailure(recovery);
                    }
                } catch (Exception e) {
                    log.error("Bulk recovery failed for message {}", messageId, e);
                    result.addFailure(RecoveryResult.builder()
                        .messageId(messageId)
                        .success(false)
                        .failureReason(e.getMessage())
                        .build());
                }
            });
            
            return result;
        });
    }
    
    @lombok.Data
    @lombok.Builder
    public static class BulkRecoveryResult {
        @lombok.Builder.Default
        private java.util.List<RecoveryResult> successes = new java.util.ArrayList<>();
        @lombok.Builder.Default
        private java.util.List<RecoveryResult> failures = new java.util.ArrayList<>();
        
        public void addSuccess(RecoveryResult result) {
            successes.add(result);
        }
        
        public void addFailure(RecoveryResult result) {
            failures.add(result);
        }
        
        public int getSuccessCount() {
            return successes.size();
        }
        
        public int getFailureCount() {
            return failures.size();
        }
    }
}