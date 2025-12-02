package com.waqiti.payment.saga.model;

import com.waqiti.payment.core.model.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SAGA State persistence model for tracking distributed transaction progress
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "saga_states")
@Document(collection = "saga_states") // For MongoDB if preferred
public class SagaState {
    
    @Id
    @jakarta.persistence.Id
    private String sagaId;
    
    private String paymentId;
    
    @Enumerated(EnumType.STRING)
    private PaymentType paymentType;
    
    @Enumerated(EnumType.STRING)
    private SagaStatus status;
    
    private String currentStep;
    
    @ElementCollection
    @CollectionTable(name = "saga_completed_steps")
    @Builder.Default
    private List<String> completedSteps = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "saga_compensated_steps")
    @Builder.Default
    private List<String> compensatedSteps = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "saga_step_results")
    @Builder.Default
    private Map<String, Object> stepResults = new HashMap<>();
    
    @ElementCollection
    @CollectionTable(name = "saga_metadata")
    private Map<String, Object> metadata;
    
    private String errorMessage;
    private String errorStep;
    
    private LocalDateTime startedAt;
    private LocalDateTime lastUpdated;
    private LocalDateTime completedAt;
    
    private int retryCount;
    private boolean compensationInProgress;
    
    @Version
    private Long version; // For optimistic locking
}