package com.waqiti.payment.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupPaymentEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    
    private String eventType;
    
    private String groupPaymentId;
    
    private String createdBy;
    
    private String title;
    
    private String description;
    
    private BigDecimal totalAmount;
    
    private String currency;
    
    private String status;
    
    private String splitType;
    
    private List<GroupPaymentParticipant> participants;
    
    private List<GroupPaymentItem> items;
    
    private String category;
    
    private Instant dueDate;
    
    private String receiptImageUrl;
    
    private String unifiedTransactionId;
    
    private Map<String, Object> metadata;
    
    private String correlationId;
    
    private String version;
    
    private Instant timestamp;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupPaymentParticipant implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String userId;
        private String userName;
        private BigDecimal amountOwed;
        private BigDecimal amountPaid;
        private String paymentStatus;
        private String role;
        private Instant paidAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupPaymentItem implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String itemName;
        private BigDecimal itemAmount;
        private Integer quantity;
        private List<String> assignedTo;
    }
}