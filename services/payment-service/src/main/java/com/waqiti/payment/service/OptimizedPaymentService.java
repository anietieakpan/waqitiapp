package com.waqiti.payment.service;

import com.waqiti.common.client.AsyncServiceClient;
import com.waqiti.payment.domain.PaymentRequest;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.repository.PaymentRequestRepository;
import com.waqiti.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Optimized payment service with async operations and batch processing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OptimizedPaymentService {
    
    private final PaymentRequestRepository paymentRequestRepository;
    private final AsyncServiceClient asyncServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ExecutorService executorService = Executors.newFixedThreadPool(20);
    
    private static final String USER_SERVICE_URL = "http://user-service/api/v1/users";
    private static final int BATCH_SIZE = 100;
    
    /**
     * Create payment request with async user validation
     */
    @Async
    public CompletableFuture<PaymentRequestResponse> createPaymentRequestAsync(CreatePaymentRequestDto request) {
        // Parallel user validation
        CompletableFuture<UserResponse> requestorFuture = asyncServiceClient
            .getCachedAsync("user-service", USER_SERVICE_URL + "/" + request.getRequestorId(), 
                          UserResponse.class, null);
        
        CompletableFuture<UserResponse> recipientFuture = asyncServiceClient
            .getCachedAsync("user-service", USER_SERVICE_URL + "/" + request.getRecipientId(),
                          UserResponse.class, null);
        
        return CompletableFuture.allOf(requestorFuture, recipientFuture)
            .thenApply(v -> {
                UserResponse requestor = requestorFuture.join();
                UserResponse recipient = recipientFuture.join();
                
                // Validate users
                validateUsers(requestor, recipient);
                
                // Create payment request
                PaymentRequest paymentRequest = PaymentRequest.create(
                    request.getRequestorId(),
                    request.getRecipientId(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getDescription()
                );
                
                PaymentRequest saved = paymentRequestRepository.save(paymentRequest);
                
                // Async event publishing
                publishEventAsync(saved, "CREATED");
                
                return toResponse(saved, requestor, recipient);
            });
    }
    
    /**
     * Get payment requests with batch user enrichment
     */
    @Transactional(readOnly = true)
    public CompletableFuture<List<PaymentRequestResponse>> getPaymentRequestsOptimized(UUID userId, String status) {
        List<PaymentRequest> requests = paymentRequestRepository
            .findByUserIdAndStatus(userId, status, PageRequest.of(0, 100))
            .getContent();
        
        if (requests.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        // Collect unique user IDs
        Set<UUID> userIds = new HashSet<>();
        requests.forEach(req -> {
            userIds.add(req.getRequestorId());
            userIds.add(req.getRecipientId());
        });
        
        // Batch fetch all users
        return batchFetchUsers(userIds)
            .thenApply(userMap -> requests.stream()
                .map(req -> toResponse(req, 
                    userMap.get(req.getRequestorId()),
                    userMap.get(req.getRecipientId())))
                .collect(Collectors.toList()));
    }
    
    /**
     * Batch fetch users with caching
     */
    @Cacheable(value = "user-batch", key = "#userIds.hashCode()")
    private CompletableFuture<Map<UUID, UserResponse>> batchFetchUsers(Set<UUID> userIds) {
        List<CompletableFuture<UserResponse>> futures = userIds.stream()
            .map(userId -> asyncServiceClient.getCachedAsync(
                "user-service", 
                USER_SERVICE_URL + "/" + userId,
                UserResponse.class, 
                null
            ).exceptionally(ex -> {
                log.warn("Failed to fetch user: {}", userId, ex);
                return createUnknownUser(userId);
            }))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                Map<UUID, UserResponse> userMap = new HashMap<>();
                int index = 0;
                for (UUID userId : userIds) {
                    try {
                        userMap.put(userId, futures.get(index++).get(1, java.util.concurrent.TimeUnit.SECONDS));
                    } catch (Exception e) {
                        log.error("Failed to retrieve user data for userId: {}", userId, e);
                        userMap.put(userId, createUnknownUser(userId));
                    }
                }
                return userMap;
            });
    }
    
    /**
     * Process expired payment requests in batches
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    @Transactional
    public void processExpiredPaymentsBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<PaymentRequest> expiredRequests;
        int processedCount = 0;
        
        do {
            // Fetch batch of expired requests
            expiredRequests = paymentRequestRepository
                .findExpiredRequests(now, PageRequest.of(0, BATCH_SIZE))
                .getContent();
            
            if (!expiredRequests.isEmpty()) {
                // Batch update status
                List<UUID> requestIds = expiredRequests.stream()
                    .map(PaymentRequest::getId)
                    .collect(Collectors.toList());
                
                paymentRequestRepository.batchUpdateStatus(requestIds, "EXPIRED", now);
                
                // Batch publish events
                List<PaymentEvent> events = expiredRequests.stream()
                    .map(req -> createPaymentEvent(req, "EXPIRED"))
                    .collect(Collectors.toList());
                
                publishEventsBatch(events);
                
                processedCount += expiredRequests.size();
                log.info("Batch processed {} expired payment requests", expiredRequests.size());
            }
        } while (expiredRequests.size() == BATCH_SIZE);
        
        if (processedCount > 0) {
            log.info("Total expired payment requests processed: {}", processedCount);
        }
    }
    
    /**
     * Async event publishing
     */
    @Async
    public void publishEventAsync(PaymentRequest paymentRequest, String eventType) {
        try {
            PaymentEvent event = createPaymentEvent(paymentRequest, eventType);
            kafkaTemplate.send("payment-events", event.getPaymentId(), event);
        } catch (Exception e) {
            log.error("Failed to publish payment event", e);
        }
    }
    
    /**
     * Batch event publishing
     */
    private void publishEventsBatch(List<PaymentEvent> events) {
        // Kafka batch send
        events.forEach(event -> 
            kafkaTemplate.send("payment-events", event.getPaymentId(), event)
        );
        
        // Flush to ensure batch is sent
        kafkaTemplate.flush();
    }
    
    /**
     * Validate payment amount with caching
     */
    @Cacheable(value = "payment-limits", key = "#userId")
    public CompletableFuture<PaymentLimits> getUserPaymentLimits(UUID userId) {
        return asyncServiceClient.getAsync(
            "user-service",
            USER_SERVICE_URL + "/" + userId + "/payment-limits",
            PaymentLimits.class,
            null
        );
    }
    
    /**
     * Process scheduled payments with parallel execution
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void processScheduledPaymentsBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledPayment> duePayments = scheduledPaymentRepository
            .findDuePayments(now, PageRequest.of(0, BATCH_SIZE))
            .getContent();
        
        if (duePayments.isEmpty()) {
            return;
        }
        
        // Process in parallel
        List<CompletableFuture<ScheduledPaymentExecution>> futures = duePayments.stream()
            .map(payment -> CompletableFuture.supplyAsync(() -> 
                executeScheduledPayment(payment), executorService))
            .collect(Collectors.toList());
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenAccept(v -> {
                List<ScheduledPaymentExecution> executions = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                
                // Batch save executions
                if (!executions.isEmpty()) {
                    scheduledPaymentExecutionRepository.saveAll(executions);
                    log.info("Processed {} scheduled payments", executions.size());
                }
            });
    }
    
    private ScheduledPaymentExecution executeScheduledPayment(ScheduledPayment payment) {
        try {
            // Execute payment logic
            TransactionResponse transaction = executePaymentTransaction(payment);
            
            // Update next execution date
            payment.updateNextExecutionDate();
            scheduledPaymentRepository.save(payment);
            
            return ScheduledPaymentExecution.success(payment, transaction.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to execute scheduled payment: {}", payment.getId(), e);
            return ScheduledPaymentExecution.failure(payment, e.getMessage());
        }
    }
    
    // Helper methods
    private void validateUsers(UserResponse requestor, UserResponse recipient) {
        if (!"ACTIVE".equals(requestor.getStatus())) {
            throw new IllegalArgumentException("Requestor account is not active");
        }
        if (!"ACTIVE".equals(recipient.getStatus())) {
            throw new IllegalArgumentException("Recipient account is not active");
        }
    }
    
    private PaymentRequestResponse toResponse(PaymentRequest request, 
                                            UserResponse requestor, 
                                            UserResponse recipient) {
        return PaymentRequestResponse.builder()
            .id(request.getId())
            .requestorId(request.getRequestorId())
            .requestorName(requestor.getUsername())
            .recipientId(request.getRecipientId())
            .recipientName(recipient.getUsername())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .status(request.getStatus().name())
            .description(request.getDescription())
            .referenceNumber(request.getReferenceNumber())
            .createdAt(request.getCreatedAt())
            .expiryDate(request.getExpiryDate())
            .build();
    }
    
    private UserResponse createUnknownUser(UUID userId) {
        UserResponse user = new UserResponse();
        user.setId(userId);
        user.setUsername("Unknown User");
        user.setStatus("UNKNOWN");
        return user;
    }
    
    private PaymentEvent createPaymentEvent(PaymentRequest request, String eventType) {
        return PaymentEvent.builder()
            .paymentId(request.getId().toString())
            .eventType(eventType)
            .requestorId(request.getRequestorId())
            .recipientId(request.getRecipientId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    // Shutdown hook
    public void shutdown() {
        executorService.shutdown();
    }
}