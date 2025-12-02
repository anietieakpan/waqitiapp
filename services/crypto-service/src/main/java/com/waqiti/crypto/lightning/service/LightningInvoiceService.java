package com.waqiti.crypto.lightning.service;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ErrorCode;
import com.waqiti.crypto.lightning.LightningNetworkService.LightningInvoice;
import com.waqiti.crypto.lightning.entity.InvoiceEntity;
import com.waqiti.crypto.lightning.entity.InvoiceStatus;
import com.waqiti.crypto.lightning.repository.InvoiceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade service for managing Lightning invoices
 * Handles invoice lifecycle, persistence, caching, and monitoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LightningInvoiceService {
    
    @org.springframework.context.annotation.Lazy
    private final LightningInvoiceService self;

    private final InvoiceRepository invoiceRepository;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, InvoiceEntity> pendingInvoices = new ConcurrentHashMap<>();
    
    private Counter invoiceCreatedCounter;
    private Counter invoicePaidCounter;
    private Counter invoiceExpiredCounter;
    private Counter invoiceCancelledCounter;

    @jakarta.annotation.PostConstruct
    public void init() {
        invoiceCreatedCounter = Counter.builder("lightning.invoice.created")
            .description("Number of Lightning invoices created")
            .register(meterRegistry);
            
        invoicePaidCounter = Counter.builder("lightning.invoice.paid")
            .description("Number of Lightning invoices paid")
            .register(meterRegistry);
            
        invoiceExpiredCounter = Counter.builder("lightning.invoice.expired")
            .description("Number of Lightning invoices expired")
            .register(meterRegistry);
            
        invoiceCancelledCounter = Counter.builder("lightning.invoice.cancelled")
            .description("Number of Lightning invoices cancelled")
            .register(meterRegistry);
    }

    /**
     * Save a new Lightning invoice
     */
    public InvoiceEntity saveInvoice(LightningInvoice invoice, String userId) {
        log.debug("Saving Lightning invoice for user: {}, hash: {}", userId, invoice.getPaymentHash());
        
        // Check for duplicate invoice
        Optional<InvoiceEntity> existing = invoiceRepository.findByPaymentHash(invoice.getPaymentHash());
        if (existing.isPresent()) {
            log.warn("Duplicate invoice attempt for hash: {}", invoice.getPaymentHash());
            throw new BusinessException(ErrorCode.PAYMENT_DUPLICATE_REQUEST, "Invoice already exists");
        }
        
        InvoiceEntity entity = InvoiceEntity.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .paymentRequest(invoice.getPaymentRequest())
            .paymentHash(invoice.getPaymentHash())
            .paymentSecret(invoice.getPaymentSecret())
            .amountSat(invoice.getAmountSat())
            .description(invoice.getDescription())
            .status(InvoiceStatus.PENDING)
            .expiresAt(Instant.now().plusSeconds(invoice.getExpiry()))
            .createdAt(Instant.now())
            .metadata(invoice.getMetadata())
            .build();
        
        entity = invoiceRepository.save(entity);
        
        // Add to pending cache for monitoring
        pendingInvoices.put(entity.getId(), entity);
        
        // Increment metrics
        invoiceCreatedCounter.increment();
        
        // Schedule expiry check
        scheduleExpiryCheck(entity);
        
        log.info("Created invoice: {} for user: {}, amount: {} sats", 
            entity.getId(), userId, invoice.getAmountSat());
        
        return entity;
    }

    /**
     * Get invoice by ID
     */
    @Cacheable(value = "invoices", key = "#invoiceId")
    public InvoiceEntity getInvoice(String invoiceId) {
        return invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RES_NOT_FOUND, "Invoice not found"));
    }

    /**
     * Get invoice by payment hash
     */
    @Cacheable(value = "invoices", key = "#paymentHash")
    public Optional<InvoiceEntity> getInvoiceByPaymentHash(String paymentHash) {
        return invoiceRepository.findByPaymentHash(paymentHash);
    }

    /**
     * Get user's invoices with filtering
     */
    public Page<InvoiceEntity> getUserInvoices(String userId, InvoiceStatus status, 
                                               LocalDateTime fromDate, LocalDateTime toDate, 
                                               Pageable pageable) {
        Specification<InvoiceEntity> spec = buildInvoiceSpecification(userId, status, fromDate, toDate);
        return invoiceRepository.findAll(spec, pageable);
    }

    /**
     * Update invoice status
     */
    @CacheEvict(value = "invoices", key = "#invoiceId")
    public InvoiceEntity updateInvoiceStatus(String invoiceId, InvoiceStatus newStatus) {
        InvoiceEntity invoice = self.getInvoice(invoiceId);
        InvoiceStatus oldStatus = invoice.getStatus();
        
        // Validate status transition
        validateStatusTransition(oldStatus, newStatus);
        
        invoice.setStatus(newStatus);
        invoice.setUpdatedAt(Instant.now());
        
        if (newStatus == InvoiceStatus.PAID) {
            invoice.setSettledAt(Instant.now());
            pendingInvoices.remove(invoiceId);
            invoicePaidCounter.increment();
        } else if (newStatus == InvoiceStatus.EXPIRED) {
            pendingInvoices.remove(invoiceId);
            invoiceExpiredCounter.increment();
        } else if (newStatus == InvoiceStatus.CANCELLED) {
            pendingInvoices.remove(invoiceId);
            invoiceCancelledCounter.increment();
        }
        
        invoice = invoiceRepository.save(invoice);
        
        log.info("Updated invoice {} status from {} to {}", invoiceId, oldStatus, newStatus);
        
        return invoice;
    }

    /**
     * Mark invoice as paid
     */
    @CacheEvict(value = "invoices", key = "#invoiceId")
    public InvoiceEntity markInvoicePaid(String invoiceId, String paymentPreimage, long amountPaidSat) {
        InvoiceEntity invoice = self.getInvoice(invoiceId);
        
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE, 
                "Invoice is not in pending state");
        }
        
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setSettledAt(Instant.now());
        invoice.setPaymentPreimage(paymentPreimage);
        invoice.setAmountPaidSat(amountPaidSat);
        invoice.setUpdatedAt(Instant.now());
        
        invoice = invoiceRepository.save(invoice);
        
        // Remove from pending cache
        pendingInvoices.remove(invoiceId);
        
        // Update metrics
        invoicePaidCounter.increment();
        
        log.info("Invoice {} marked as paid, amount: {} sats", invoiceId, amountPaidSat);
        
        return invoice;
    }

    /**
     * Cancel a pending invoice
     */
    @CacheEvict(value = "invoices", key = "#invoiceId")
    public void cancelInvoice(String invoiceId) {
        InvoiceEntity invoice = self.getInvoice(invoiceId);
        
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE, 
                "Only pending invoices can be cancelled");
        }
        
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setUpdatedAt(Instant.now());
        invoice.setCancelledAt(Instant.now());
        
        invoiceRepository.save(invoice);
        
        // Remove from pending cache
        pendingInvoices.remove(invoiceId);
        
        // Update metrics
        invoiceCancelledCounter.increment();
        
        log.info("Invoice {} cancelled", invoiceId);
    }

    /**
     * Check and expire old invoices
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    public void expireOldInvoices() {
        log.debug("Checking for expired invoices");
        
        Instant now = Instant.now();
        List<InvoiceEntity> expiredInvoices = invoiceRepository.findExpiredInvoices(now);
        
        for (InvoiceEntity invoice : expiredInvoices) {
            try {
                updateInvoiceStatus(invoice.getId(), InvoiceStatus.EXPIRED);
                log.info("Expired invoice: {}", invoice.getId());
            } catch (Exception e) {
                log.error("Error expiring invoice: {}", invoice.getId(), e);
            }
        }
        
        // Also check cached pending invoices
        pendingInvoices.values().stream()
            .filter(invoice -> invoice.getExpiresAt().isBefore(now))
            .forEach(invoice -> {
                try {
                    updateInvoiceStatus(invoice.getId(), InvoiceStatus.EXPIRED);
                } catch (Exception e) {
                    log.error("Error expiring cached invoice: {}", invoice.getId(), e);
                }
            });
    }

    /**
     * Get invoice statistics for a user
     */
    public InvoiceStatistics getUserInvoiceStatistics(String userId, LocalDateTime from, LocalDateTime to) {
        List<Object[]> stats = invoiceRepository.getUserInvoiceStatistics(
            userId, 
            from.toInstant(ZoneOffset.UTC), 
            to.toInstant(ZoneOffset.UTC)
        );
        
        InvoiceStatistics statistics = new InvoiceStatistics();
        
        for (Object[] row : stats) {
            InvoiceStatus status = (InvoiceStatus) row[0];
            Long count = (Long) row[1];
            Long totalAmount = (Long) row[2];
            
            switch (status) {
                case PAID -> {
                    statistics.setPaidCount(count.intValue());
                    statistics.setPaidAmount(totalAmount);
                }
                case PENDING -> {
                    statistics.setPendingCount(count.intValue());
                    statistics.setPendingAmount(totalAmount);
                }
                case EXPIRED -> {
                    statistics.setExpiredCount(count.intValue());
                    statistics.setExpiredAmount(totalAmount);
                }
                case CANCELLED -> {
                    statistics.setCancelledCount(count.intValue());
                    statistics.setCancelledAmount(totalAmount);
                }
            }
        }
        
        statistics.setTotalCount(
            statistics.getPaidCount() + statistics.getPendingCount() + 
            statistics.getExpiredCount() + statistics.getCancelledCount()
        );
        
        statistics.setTotalAmount(
            statistics.getPaidAmount() + statistics.getPendingAmount() + 
            statistics.getExpiredAmount() + statistics.getCancelledAmount()
        );
        
        return statistics;
    }

    /**
     * Bulk update invoice statuses (for reconciliation)
     */
    @Transactional
    public void bulkUpdateInvoiceStatuses(List<InvoiceStatusUpdate> updates) {
        log.info("Performing bulk invoice status update for {} invoices", updates.size());
        
        for (InvoiceStatusUpdate update : updates) {
            try {
                InvoiceEntity invoice = invoiceRepository.findByPaymentHash(update.getPaymentHash())
                    .orElse(null);
                    
                if (invoice != null && invoice.getStatus() != update.getNewStatus()) {
                    invoice.setStatus(update.getNewStatus());
                    invoice.setUpdatedAt(Instant.now());
                    
                    if (update.getNewStatus() == InvoiceStatus.PAID) {
                        invoice.setSettledAt(Instant.now());
                        invoice.setPaymentPreimage(update.getPaymentPreimage());
                        invoice.setAmountPaidSat(update.getAmountPaid());
                    }
                    
                    invoiceRepository.save(invoice);
                    log.debug("Updated invoice {} to status {}", invoice.getId(), update.getNewStatus());
                }
            } catch (Exception e) {
                log.error("Error updating invoice with hash: {}", update.getPaymentHash(), e);
            }
        }
    }

    /**
     * Clean up old invoices
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @Transactional
    public void cleanupOldInvoices() {
        log.info("Starting old invoice cleanup");
        
        // Delete cancelled/expired invoices older than 30 days
        Instant cutoffDate = Instant.now().minusSeconds(30 * 24 * 60 * 60);
        int deletedCount = invoiceRepository.deleteOldInvoices(cutoffDate);
        
        log.info("Deleted {} old invoices", deletedCount);
    }

    // Helper methods

    private Specification<InvoiceEntity> buildInvoiceSpecification(String userId, InvoiceStatus status,
                                                                   LocalDateTime fromDate, LocalDateTime toDate) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            
            if (fromDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("createdAt"), fromDate.toInstant(ZoneOffset.UTC)));
            }
            
            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                    root.get("createdAt"), toDate.toInstant(ZoneOffset.UTC)));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void validateStatusTransition(InvoiceStatus from, InvoiceStatus to) {
        // Define valid status transitions
        boolean isValid = switch (from) {
            case PENDING -> to == InvoiceStatus.PAID || to == InvoiceStatus.EXPIRED || 
                           to == InvoiceStatus.CANCELLED;
            case PAID, EXPIRED, CANCELLED -> false; // Terminal states
            default -> false;
        };
        
        if (!isValid) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE, 
                String.format("Invalid status transition from %s to %s", from, to));
        }
    }

    private void scheduleExpiryCheck(InvoiceEntity invoice) {
        long delaySeconds = invoice.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
        
        if (delaySeconds > 0) {
            CompletableFuture.delayedExecutor(delaySeconds, TimeUnit.SECONDS)
                .execute(() -> {
                    try {
                        if (pendingInvoices.containsKey(invoice.getId())) {
                            InvoiceEntity current = self.getInvoice(invoice.getId());
                            if (current.getStatus() == InvoiceStatus.PENDING) {
                                updateInvoiceStatus(invoice.getId(), InvoiceStatus.EXPIRED);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error in scheduled expiry check for invoice: {}", invoice.getId(), e);
                    }
                });
        }
    }

    /**
     * Invoice statistics DTO
     */
    public static class InvoiceStatistics {
        private int totalCount;
        private long totalAmount;
        private int paidCount;
        private long paidAmount;
        private int pendingCount;
        private long pendingAmount;
        private int expiredCount;
        private long expiredAmount;
        private int cancelledCount;
        private long cancelledAmount;

        // Getters and setters
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        
        public long getTotalAmount() { return totalAmount; }
        public void setTotalAmount(long totalAmount) { this.totalAmount = totalAmount; }
        
        public int getPaidCount() { return paidCount; }
        public void setPaidCount(int paidCount) { this.paidCount = paidCount; }
        
        public long getPaidAmount() { return paidAmount; }
        public void setPaidAmount(long paidAmount) { this.paidAmount = paidAmount; }
        
        public int getPendingCount() { return pendingCount; }
        public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
        
        public long getPendingAmount() { return pendingAmount; }
        public void setPendingAmount(long pendingAmount) { this.pendingAmount = pendingAmount; }
        
        public int getExpiredCount() { return expiredCount; }
        public void setExpiredCount(int expiredCount) { this.expiredCount = expiredCount; }
        
        public long getExpiredAmount() { return expiredAmount; }
        public void setExpiredAmount(long expiredAmount) { this.expiredAmount = expiredAmount; }
        
        public int getCancelledCount() { return cancelledCount; }
        public void setCancelledCount(int cancelledCount) { this.cancelledCount = cancelledCount; }
        
        public long getCancelledAmount() { return cancelledAmount; }
        public void setCancelledAmount(long cancelledAmount) { this.cancelledAmount = cancelledAmount; }
    }

    /**
     * Invoice status update DTO for bulk operations
     */
    public static class InvoiceStatusUpdate {
        private String paymentHash;
        private InvoiceStatus newStatus;
        private String paymentPreimage;
        private Long amountPaid;

        // Getters and setters
        public String getPaymentHash() { return paymentHash; }
        public void setPaymentHash(String paymentHash) { this.paymentHash = paymentHash; }
        
        public InvoiceStatus getNewStatus() { return newStatus; }
        public void setNewStatus(InvoiceStatus newStatus) { this.newStatus = newStatus; }
        
        public String getPaymentPreimage() { return paymentPreimage; }
        public void setPaymentPreimage(String paymentPreimage) { this.paymentPreimage = paymentPreimage; }
        
        public Long getAmountPaid() { return amountPaid; }
        public void setAmountPaid(Long amountPaid) { this.amountPaid = amountPaid; }
    }
}