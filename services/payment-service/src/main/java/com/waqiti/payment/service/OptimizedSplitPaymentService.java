package com.waqiti.payment.service;

import com.waqiti.payment.domain.SplitPayment;
import com.waqiti.payment.domain.SplitPaymentParticipant;
import com.waqiti.payment.dto.SplitPaymentResponse;
import com.waqiti.payment.dto.SplitPaymentSummary;
import com.waqiti.payment.repository.OptimizedSplitPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Optimized split payment service with N+1 query prevention
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OptimizedSplitPaymentService {
    
    private final OptimizedSplitPaymentRepository splitPaymentRepository;
    
    /**
     * Get split payment with participants - optimized to prevent N+1
     */
    public SplitPaymentResponse getSplitPayment(UUID splitPaymentId, UUID userId) {
        log.debug("Getting split payment {} for user {}", splitPaymentId, userId);
        
        SplitPayment splitPayment = splitPaymentRepository
            .findWithParticipantsById(splitPaymentId)
            .orElseThrow(() -> new IllegalArgumentException("Split payment not found"));
        
        // Verify user has access
        if (!hasAccess(splitPayment, userId)) {
            throw new SecurityException("User does not have access to this split payment");
        }
        
        return mapToResponse(splitPayment);
    }
    
    /**
     * Get user's split payments - optimized with pagination and projections
     */
    @Cacheable(value = "userSplitPayments", key = "#userId + '_' + #pageable.pageNumber")
    public Page<SplitPaymentSummary> getUserSplitPayments(UUID userId, Pageable pageable) {
        log.debug("Getting split payments for user {} page {}", userId, pageable.getPageNumber());
        
        // Use projection query to avoid loading full entities
        Page<Object> summaries = splitPaymentRepository
            .findRecentSummariesForUser(userId, pageable);
        
        return summaries.map(obj -> (SplitPaymentSummary) obj);
    }
    
    /**
     * Get pending split payments for user - batch loaded
     */
    public List<SplitPaymentResponse> getPendingSplitPayments(UUID userId) {
        log.debug("Getting pending split payments for user {}", userId);
        
        // Single query with join fetch
        List<SplitPayment> pendingPayments = splitPaymentRepository
            .findPendingForUserWithParticipants(userId);
        
        return pendingPayments.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Get split payments where user is participant - optimized
     */
    public Page<SplitPaymentResponse> getParticipantSplitPayments(UUID userId, Pageable pageable) {
        log.debug("Getting split payments where user {} is participant", userId);
        
        // Single query with join fetch
        Page<SplitPayment> splitPayments = splitPaymentRepository
            .findByParticipantUserIdWithParticipants(userId, pageable);
        
        return splitPayments.map(this::mapToResponse);
    }
    
    /**
     * Batch load split payments - prevents N+1 when loading multiple
     */
    public List<SplitPaymentResponse> getSplitPaymentsBatch(List<UUID> ids) {
        log.debug("Batch loading {} split payments", ids.size());
        
        if (ids.isEmpty()) {
            return List.of();
        }
        
        // Single query to load all with participants
        List<SplitPayment> splitPayments = splitPaymentRepository
            .findAllByIdInWithParticipants(ids);
        
        return splitPayments.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Get dashboard summary - uses aggregation queries
     */
    @Cacheable(value = "dashboardSummary", key = "#userId")
    public DashboardSummary getDashboardSummary(UUID userId) {
        log.debug("Getting dashboard summary for user {}", userId);
        
        // Use aggregation query instead of loading entities
        List<Object[]> statusCounts = splitPaymentRepository.countByStatusForUser(userId);
        
        DashboardSummary summary = new DashboardSummary();
        for (Object[] row : statusCounts) {
            summary.addStatusCount((String) row[0], (Long) row[1]);
        }
        
        // Get recent summaries without loading full entities
        Page<SplitPaymentSummary> recentSummaries = getUserSplitPayments(
            userId, PageRequest.of(0, 5)
        );
        summary.setRecentSplitPayments(recentSummaries.getContent());
        
        return summary;
    }
    
    /**
     * Process expired split payments - batch operation
     */
    @Transactional
    public int processExpiredSplitPayments() {
        log.info("Processing expired split payments");
        
        // Load all expired in one query
        List<SplitPayment> expiredPayments = splitPaymentRepository
            .findExpiredWithParticipants(java.time.LocalDateTime.now());
        
        if (expiredPayments.isEmpty()) {
            return 0;
        }
        
        // Batch update status
        for (SplitPayment payment : expiredPayments) {
            payment.expire();
        }
        
        // Save all at once (will be batched by Hibernate)
        splitPaymentRepository.saveAll(expiredPayments);
        
        log.info("Processed {} expired split payments", expiredPayments.size());
        return expiredPayments.size();
    }
    
    private boolean hasAccess(SplitPayment splitPayment, UUID userId) {
        if (splitPayment.getOrganizerId().equals(userId)) {
            return true;
        }
        
        return splitPayment.getParticipants().stream()
            .anyMatch(p -> p.getUserId().equals(userId));
    }
    
    private SplitPaymentResponse mapToResponse(SplitPayment splitPayment) {
        return SplitPaymentResponse.builder()
            .id(splitPayment.getId())
            .title(splitPayment.getTitle())
            .description(splitPayment.getDescription())
            .organizerId(splitPayment.getOrganizerId())
            .totalAmount(splitPayment.getTotalAmount())
            .currency(splitPayment.getCurrency())
            .status(splitPayment.getStatus())
            .participants(splitPayment.getParticipants().stream()
                .map(this::mapParticipant)
                .collect(Collectors.toList()))
            .createdAt(splitPayment.getCreatedAt())
            .expiresAt(splitPayment.getExpiresAt())
            .build();
    }
    
    private ParticipantResponse mapParticipant(SplitPaymentParticipant participant) {
        return ParticipantResponse.builder()
            .userId(participant.getUserId())
            .amount(participant.getAmount())
            .status(participant.getStatus())
            .paidAt(participant.getPaidAt())
            .build();
    }
    
    /**
     * Inner class for dashboard summary
     */
    @lombok.Data
    public static class DashboardSummary {
        private Map<String, Long> statusCounts = new HashMap<>();
        private List<SplitPaymentSummary> recentSplitPayments = new ArrayList<>();
        private BigDecimal totalPendingAmount = BigDecimal.ZERO;
        private BigDecimal totalPaidAmount = BigDecimal.ZERO;
        
        public void addStatusCount(String status, Long count) {
            statusCounts.put(status, count);
        }
        
        public long getTotalSplitPayments() {
            return statusCounts.values().stream().mapToLong(Long::longValue).sum();
        }
    }
}