package com.waqiti.analytics.service;

import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for tracking and reporting business metrics
 * Provides real-time insights into platform performance and health
 */
@Service
public class BusinessMetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(BusinessMetricsService.class);
    
    private final MeterRegistry meterRegistry;
    private final JdbcTemplate jdbcTemplate;
    
    public BusinessMetricsService(MeterRegistry meterRegistry, JdbcTemplate jdbcTemplate) {
        this.meterRegistry = meterRegistry;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    // Business Metrics Gauges
    private AtomicInteger activeUsers;
    private AtomicInteger dailyActiveUsers;
    private AtomicInteger monthlyActiveUsers;
    private AtomicLong totalTransactionVolume;
    private AtomicLong dailyTransactionVolume;
    private AtomicInteger pendingKycVerifications;
    private AtomicInteger activeWallets;
    private AtomicInteger merchantAccounts;
    
    // Performance Metrics
    private Timer paymentProcessingTime;
    private Timer kycVerificationTime;
    private Timer walletCreationTime;
    private Timer userRegistrationTime;
    
    // Business Event Counters
    private Counter successfulPayments;
    private Counter failedPayments;
    private Counter newUserRegistrations;
    private Counter kycApprovals;
    private Counter kycRejections;
    private Counter fraudulentTransactions;
    private Counter chargebacks;
    private Counter refunds;
    
    // Revenue Metrics
    private Counter transactionFeeRevenue;
    private Counter subscriptionRevenue;
    private Counter interchangeFeeRevenue;
    
    // Conversion Metrics
    private Counter signupToKycConversions;
    private Counter kycToFirstTransactionConversions;
    private Counter freeToProConversions;
    
    @PostConstruct
    public void initializeMetrics() {
        // Initialize atomic counters
        activeUsers = new AtomicInteger(0);
        dailyActiveUsers = new AtomicInteger(0);
        monthlyActiveUsers = new AtomicInteger(0);
        totalTransactionVolume = new AtomicLong(0);
        dailyTransactionVolume = new AtomicLong(0);
        pendingKycVerifications = new AtomicInteger(0);
        activeWallets = new AtomicInteger(0);
        merchantAccounts = new AtomicInteger(0);
        
        // Register gauges for real-time monitoring
        registerGauges();
        
        // Initialize timers for performance tracking
        initializeTimers();
        
        // Initialize counters for business events
        initializeCounters();
        
        // Load initial metrics
        refreshMetrics();
        
        logger.info("Business metrics service initialized successfully");
    }
    
    private void registerGauges() {
        // User metrics
        Gauge.builder("business.users.active", activeUsers, AtomicInteger::get)
            .description("Number of currently active users")
            .tag("type", "realtime")
            .register(meterRegistry);
        
        Gauge.builder("business.users.daily_active", dailyActiveUsers, AtomicInteger::get)
            .description("Daily active users (DAU)")
            .tag("type", "daily")
            .register(meterRegistry);
        
        Gauge.builder("business.users.monthly_active", monthlyActiveUsers, AtomicInteger::get)
            .description("Monthly active users (MAU)")
            .tag("type", "monthly")
            .register(meterRegistry);
        
        // Transaction volume metrics
        Gauge.builder("business.transactions.total_volume", totalTransactionVolume, AtomicLong::get)
            .description("Total transaction volume in cents")
            .baseUnit("cents")
            .register(meterRegistry);
        
        Gauge.builder("business.transactions.daily_volume", dailyTransactionVolume, AtomicLong::get)
            .description("Daily transaction volume in cents")
            .baseUnit("cents")
            .register(meterRegistry);
        
        // Platform health metrics
        Gauge.builder("business.kyc.pending_verifications", pendingKycVerifications, AtomicInteger::get)
            .description("Number of pending KYC verifications")
            .register(meterRegistry);
        
        Gauge.builder("business.wallets.active", activeWallets, AtomicInteger::get)
            .description("Number of active wallets")
            .register(meterRegistry);
        
        Gauge.builder("business.merchants.active", merchantAccounts, AtomicInteger::get)
            .description("Number of active merchant accounts")
            .register(meterRegistry);
    }
    
    private void initializeTimers() {
        paymentProcessingTime = Timer.builder("business.payment.processing_time")
            .description("Time taken to process payments")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry);
        
        kycVerificationTime = Timer.builder("business.kyc.verification_time")
            .description("Time taken for KYC verification")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        
        walletCreationTime = Timer.builder("business.wallet.creation_time")
            .description("Time taken to create a wallet")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        
        userRegistrationTime = Timer.builder("business.user.registration_time")
            .description("Time taken for user registration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
    }
    
    private void initializeCounters() {
        // Payment metrics
        successfulPayments = Counter.builder("business.payments.successful")
            .description("Number of successful payments")
            .register(meterRegistry);
        
        failedPayments = Counter.builder("business.payments.failed")
            .description("Number of failed payments")
            .register(meterRegistry);
        
        // User lifecycle metrics
        newUserRegistrations = Counter.builder("business.users.registrations")
            .description("Number of new user registrations")
            .register(meterRegistry);
        
        // KYC metrics
        kycApprovals = Counter.builder("business.kyc.approvals")
            .description("Number of KYC approvals")
            .register(meterRegistry);
        
        kycRejections = Counter.builder("business.kyc.rejections")
            .description("Number of KYC rejections")
            .register(meterRegistry);
        
        // Risk metrics
        fraudulentTransactions = Counter.builder("business.fraud.detected")
            .description("Number of fraudulent transactions detected")
            .register(meterRegistry);
        
        chargebacks = Counter.builder("business.chargebacks")
            .description("Number of chargebacks")
            .register(meterRegistry);
        
        refunds = Counter.builder("business.refunds")
            .description("Number of refunds processed")
            .register(meterRegistry);
        
        // Revenue metrics
        transactionFeeRevenue = Counter.builder("business.revenue.transaction_fees")
            .description("Revenue from transaction fees in cents")
            .baseUnit("cents")
            .register(meterRegistry);
        
        subscriptionRevenue = Counter.builder("business.revenue.subscriptions")
            .description("Revenue from subscriptions in cents")
            .baseUnit("cents")
            .register(meterRegistry);
        
        interchangeFeeRevenue = Counter.builder("business.revenue.interchange")
            .description("Revenue from interchange fees in cents")
            .baseUnit("cents")
            .register(meterRegistry);
        
        // Conversion funnel metrics
        signupToKycConversions = Counter.builder("business.conversions.signup_to_kyc")
            .description("Users who completed KYC after signup")
            .register(meterRegistry);
        
        kycToFirstTransactionConversions = Counter.builder("business.conversions.kyc_to_transaction")
            .description("Users who made first transaction after KYC")
            .register(meterRegistry);
        
        freeToProConversions = Counter.builder("business.conversions.free_to_pro")
            .description("Users who upgraded from free to pro")
            .register(meterRegistry);
    }
    
    /**
     * Refresh metrics from database - runs every minute
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 5000)
    public void refreshMetrics() {
        try {
            updateUserMetrics();
            updateTransactionMetrics();
            updatePlatformHealthMetrics();
            updateRevenueMetrics();
            
            logger.debug("Business metrics refreshed successfully");
        } catch (Exception e) {
            logger.error("Error refreshing business metrics", e);
        }
    }
    
    private void updateUserMetrics() {
        try {
            // Active users (last 15 minutes)
            Integer active = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM user_sessions WHERE last_activity > ?",
                Integer.class,
                LocalDateTime.now().minusMinutes(15)
            );
            activeUsers.set(active != null ? active : 0);
            
            // Daily active users
            Integer dau = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM user_activity WHERE activity_date = CURRENT_DATE",
                Integer.class
            );
            dailyActiveUsers.set(dau != null ? dau : 0);
            
            // Monthly active users
            Integer mau = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM user_activity WHERE activity_date >= ?",
                Integer.class,
                LocalDateTime.now().minusDays(30)
            );
            monthlyActiveUsers.set(mau != null ? mau : 0);
            
        } catch (Exception e) {
            logger.error("Error updating user metrics", e);
        }
    }
    
    private void updateTransactionMetrics() {
        try {
            // Total transaction volume (all time)
            BigDecimal totalVolume = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE status = 'COMPLETED'",
                BigDecimal.class
            );
            totalTransactionVolume.set(totalVolume != null ? totalVolume.longValue() : 0);
            
            // Daily transaction volume
            BigDecimal dailyVolume = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE status = 'COMPLETED' AND DATE(created_at) = CURRENT_DATE",
                BigDecimal.class
            );
            dailyTransactionVolume.set(dailyVolume != null ? dailyVolume.longValue() : 0);
            
        } catch (Exception e) {
            logger.error("Error updating transaction metrics", e);
        }
    }
    
    private void updatePlatformHealthMetrics() {
        try {
            // Pending KYC verifications
            Integer pendingKyc = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kyc_verifications WHERE status = 'PENDING'",
                Integer.class
            );
            pendingKycVerifications.set(pendingKyc != null ? pendingKyc : 0);
            
            // Active wallets
            Integer wallets = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallets WHERE status = 'ACTIVE' AND balance > 0",
                Integer.class
            );
            activeWallets.set(wallets != null ? wallets : 0);
            
            // Merchant accounts
            Integer merchants = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE account_type = 'MERCHANT' AND status = 'ACTIVE'",
                Integer.class
            );
            merchantAccounts.set(merchants != null ? merchants : 0);
            
        } catch (Exception e) {
            logger.error("Error updating platform health metrics", e);
        }
    }
    
    private void updateRevenueMetrics() {
        try {
            // Calculate daily revenue from transaction fees
            BigDecimal dailyFees = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(fee_amount), 0) FROM transactions WHERE DATE(created_at) = CURRENT_DATE",
                BigDecimal.class
            );
            
            // Update counter with incremental value
            if (dailyFees != null && dailyFees.compareTo(BigDecimal.ZERO) > 0) {
                transactionFeeRevenue.increment(dailyFees.doubleValue());
            }
            
        } catch (Exception e) {
            logger.error("Error updating revenue metrics", e);
        }
    }
    
    // Public methods for recording business events
    
    public void recordPaymentSuccess(BigDecimal amount, long processingTimeMs) {
        successfulPayments.increment();
        paymentProcessingTime.record(processingTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        // Record custom metric for payment amount distribution
        meterRegistry.summary("business.payment.amount")
            .record(amount.doubleValue());
    }
    
    public void recordPaymentFailure(String reason) {
        failedPayments.increment();
        
        // Track failure reasons
        Counter.builder("business.payments.failed.by_reason")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
    }
    
    public void recordUserRegistration(long registrationTimeMs) {
        newUserRegistrations.increment();
        userRegistrationTime.record(registrationTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    public void recordKycApproval(long verificationTimeMs) {
        kycApprovals.increment();
        kycVerificationTime.record(verificationTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    public void recordKycRejection(String reason) {
        kycRejections.increment();
        
        // Track rejection reasons
        Counter.builder("business.kyc.rejected.by_reason")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
    }
    
    public void recordFraudDetection(String type) {
        fraudulentTransactions.increment();
        
        // Track fraud types
        Counter.builder("business.fraud.by_type")
            .tag("type", type)
            .register(meterRegistry)
            .increment();
    }
    
    public void recordChargeback(BigDecimal amount) {
        chargebacks.increment();
        
        // Track chargeback amounts
        meterRegistry.summary("business.chargeback.amount")
            .record(amount.doubleValue());
    }
    
    public void recordRefund(BigDecimal amount, String reason) {
        refunds.increment();
        
        // Track refund amounts and reasons
        meterRegistry.summary("business.refund.amount")
            .record(amount.doubleValue());
        
        Counter.builder("business.refunds.by_reason")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
    }
    
    public void recordConversion(String conversionType) {
        switch (conversionType) {
            case "SIGNUP_TO_KYC":
                signupToKycConversions.increment();
                break;
            case "KYC_TO_TRANSACTION":
                kycToFirstTransactionConversions.increment();
                break;
            case "FREE_TO_PRO":
                freeToProConversions.increment();
                break;
            default:
                logger.warn("Unknown conversion type: {}", conversionType);
        }
    }
    
    public void recordWalletCreation(long creationTimeMs) {
        walletCreationTime.record(creationTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    /**
     * Get current business health score (0-100)
     */
    public int getBusinessHealthScore() {
        int score = 100;
        
        // Deduct points for concerning metrics
        if (pendingKycVerifications.get() > 100) {
            score -= 10; // Too many pending verifications
        }
        
        if (dailyActiveUsers.get() < 100) {
            score -= 15; // Low user engagement
        }
        
        if (dailyTransactionVolume.get() < 10000) {
            score -= 10; // Low transaction volume
        }
        
        // Calculate payment success rate
        double successRate = successfulPayments.count() / 
            (successfulPayments.count() + failedPayments.count() + 1.0);
        
        if (successRate < 0.95) {
            score -= 20; // High payment failure rate
        }
        
        return Math.max(0, score);
    }
}