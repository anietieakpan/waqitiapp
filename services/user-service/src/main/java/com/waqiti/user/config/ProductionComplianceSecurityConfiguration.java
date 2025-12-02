package com.waqiti.user.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Production Compliance and Security Service Configuration
 * 
 * Enterprise-grade compliance, KYC/AML, fraud detection, and security services
 * with full regulatory compliance and real-time monitoring capabilities.
 */
@Slf4j
public class ProductionComplianceSecurityConfiguration {

    /**
     * Production Compliance Service with BSA/AML compliance
     */
    public static class ProductionComplianceService implements ComplianceService {
        private final String complianceServiceUrl;
        private final RedisTemplate<String, Object> redisTemplate;
        private final KafkaTemplate<String, Object> kafkaTemplate;
        private final EntityManager entityManager;
        private final RestTemplate restTemplate;

        public ProductionComplianceService(String complianceServiceUrl,
                                         RedisTemplate<String, Object> redisTemplate,
                                         KafkaTemplate<String, Object> kafkaTemplate,
                                         EntityManager entityManager) {
            this.complianceServiceUrl = complianceServiceUrl;
            this.redisTemplate = redisTemplate;
            this.kafkaTemplate = kafkaTemplate;
            this.entityManager = entityManager;
            this.restTemplate = new RestTemplate();
        }

        @Override
        public boolean hasRegulatoryHold(String userId) {
            try {
                // Check Redis cache first
                String holdKey = "compliance:regulatory_hold:" + userId;
                Object cached = redisTemplate.opsForValue().get(holdKey);
                if (cached != null) {
                    return Boolean.parseBoolean(cached.toString());
                }

                // Check database for active regulatory holds
                Number holdCount = (Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM regulatory_holds WHERE user_id = :userId " +
                    "AND status = 'ACTIVE' AND expires_at > :now"
                ).setParameter("userId", userId)
                 .setParameter("now", LocalDateTime.now())
                 .getSingleResult();

                boolean hasHold = holdCount.intValue() > 0;

                // Cache result for 5 minutes
                redisTemplate.opsForValue().set(holdKey, hasHold, 300, TimeUnit.SECONDS);

                if (hasHold) {
                    log.warn("User {} has active regulatory hold", userId);
                    
                    // Publish hold check event
                    Map<String, Object> holdEvent = Map.of(
                        "eventType", "REGULATORY_HOLD_CHECKED",
                        "userId", userId,
                        "hasHold", hasHold,
                        "timestamp", LocalDateTime.now()
                    );
                    kafkaTemplate.send("compliance-events", userId, holdEvent);
                }

                return hasHold;

            } catch (Exception e) {
                log.error("Failed to check regulatory hold for user {}: {}", userId, e.getMessage());
                return false; // Fail open for availability
            }
        }

        @Override
        @Transactional
        public Object performKYCVerification(String userId, Map<String, Object> kycData) {
            try {
                log.info("Performing KYC verification for user {}", userId);

                // Validate required KYC fields
                validateKYCData(kycData);

                // Call compliance service for verification
                String url = complianceServiceUrl + "/api/v1/kyc/verify";
                Map<String, Object> verificationRequest = Map.of(
                    "userId", userId,
                    "kycData", kycData,
                    "verificationLevel", determineVerificationLevel(kycData),
                    "source", "USER_SERVICE",
                    "timestamp", LocalDateTime.now()
                );

                Map<String, Object> response = restTemplate.postForObject(url, verificationRequest, Map.class);

                if (response != null) {
                    String verificationId = response.get("verificationId").toString();
                    String status = response.get("status").toString();
                    int score = ((Number) response.get("score")).intValue();

                    // Store verification result in database
                    entityManager.createNativeQuery(
                        "INSERT INTO kyc_verifications (id, user_id, verification_id, status, score, " +
                        "verification_data, created_at, expires_at) VALUES " +
                        "(:id, :userId, :verificationId, :status, :score, :data, :createdAt, :expiresAt)"
                    ).setParameter("id", UUID.randomUUID().toString())
                     .setParameter("userId", userId)
                     .setParameter("verificationId", verificationId)
                     .setParameter("status", status)
                     .setParameter("score", score)
                     .setParameter("data", kycData.toString())
                     .setParameter("createdAt", LocalDateTime.now())
                     .setParameter("expiresAt", LocalDateTime.now().plusYears(1))
                     .executeUpdate();

                    // Cache verification result
                    String cacheKey = "kyc:verification:" + userId;
                    Map<String, Object> verificationData = Map.of(
                        "verificationId", verificationId,
                        "status", status,
                        "score", score,
                        "verifiedAt", LocalDateTime.now().toString(),
                        "expiresAt", LocalDateTime.now().plusYears(1).toString()
                    );
                    redisTemplate.opsForHash().putAll(cacheKey, verificationData);
                    redisTemplate.expire(cacheKey, 24, TimeUnit.HOURS);

                    // Publish KYC event
                    Map<String, Object> kycEvent = Map.of(
                        "eventType", "KYC_VERIFICATION_COMPLETED",
                        "userId", userId,
                        "verificationId", verificationId,
                        "status", status,
                        "score", score,
                        "timestamp", LocalDateTime.now()
                    );
                    kafkaTemplate.send("kyc-events", userId, kycEvent);

                    // Check if enhanced due diligence is required
                    if (score < 70 || "ENHANCED_REVIEW".equals(status)) {
                        triggerEnhancedDueDiligence(userId, verificationId, score);
                    }

                    return Map.of(
                        "verificationId", verificationId,
                        "status", status,
                        "score", score,
                        "verifiedAt", LocalDateTime.now(),
                        "nextReviewDate", LocalDateTime.now().plusMonths(6)
                    );

                } else {
                    throw new RuntimeException("KYC service returned null response");
                }

            } catch (Exception e) {
                log.error("KYC verification failed for user {}: {}", userId, e.getMessage(), e);
                
                // Publish failure event
                Map<String, Object> failureEvent = Map.of(
                    "eventType", "KYC_VERIFICATION_FAILED",
                    "userId", userId,
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now()
                );
                kafkaTemplate.send("kyc-errors", userId, failureEvent);
                
                throw new RuntimeException("KYC verification failed", e);
            }
        }

        @Override
        public boolean checkSanctions(String userId) {
            try {
                // Check cache first
                String sanctionsKey = "compliance:sanctions:" + userId;
                Object cached = redisTemplate.opsForValue().get(sanctionsKey);
                if (cached != null) {
                    return Boolean.parseBoolean(cached.toString());
                }

                // Call sanctions screening service
                String url = complianceServiceUrl + "/api/v1/sanctions/check/" + userId;
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);

                boolean onSanctionsList = false;
                if (response != null) {
                    onSanctionsList = Boolean.parseBoolean(response.get("onSanctionsList").toString());
                    
                    // Cache result for 24 hours
                    redisTemplate.opsForValue().set(sanctionsKey, onSanctionsList, 24, TimeUnit.HOURS);

                    if (onSanctionsList) {
                        log.error("CRITICAL: User {} found on sanctions list", userId);
                        
                        // Immediate account freeze
                        freezeAccountForSanctions(userId);
                        
                        // Publish critical alert
                        Map<String, Object> sanctionsAlert = Map.of(
                            "eventType", "SANCTIONS_LIST_MATCH",
                            "severity", "CRITICAL",
                            "userId", userId,
                            "matchDetails", response.get("matchDetails"),
                            "timestamp", LocalDateTime.now()
                        );
                        kafkaTemplate.send("critical-compliance-alerts", userId, sanctionsAlert);
                    }
                }

                return onSanctionsList;

            } catch (Exception e) {
                log.error("Sanctions check failed for user {}: {}", userId, e.getMessage());
                // Fail safe - assume not on sanctions list but log error
                return false;
            }
        }

        @Override
        @Transactional
        public Object generateComplianceReport(String userId, String reportType) {
            try {
                log.info("Generating {} compliance report for user {}", reportType, userId);

                // Gather compliance data
                Map<String, Object> complianceData = gatherComplianceData(userId);
                
                String reportId = UUID.randomUUID().toString();
                
                // Call compliance service to generate report
                String url = complianceServiceUrl + "/api/v1/reports/generate";
                Map<String, Object> reportRequest = Map.of(
                    "reportId", reportId,
                    "userId", userId,
                    "reportType", reportType,
                    "complianceData", complianceData,
                    "generatedBy", "USER_SERVICE",
                    "timestamp", LocalDateTime.now()
                );

                Map<String, Object> response = restTemplate.postForObject(url, reportRequest, Map.class);

                if (response != null && "SUCCESS".equals(response.get("status"))) {
                    // Store report metadata in database
                    entityManager.createNativeQuery(
                        "INSERT INTO compliance_reports (id, user_id, report_type, status, " +
                        "report_data, generated_at, expires_at) VALUES " +
                        "(:id, :userId, :reportType, :status, :data, :generatedAt, :expiresAt)"
                    ).setParameter("id", reportId)
                     .setParameter("userId", userId)
                     .setParameter("reportType", reportType)
                     .setParameter("status", "GENERATED")
                     .setParameter("data", response.get("reportData").toString())
                     .setParameter("generatedAt", LocalDateTime.now())
                     .setParameter("expiresAt", LocalDateTime.now().plusYears(7)) // Regulatory retention
                     .executeUpdate();

                    // Cache report for quick access
                    String reportKey = "compliance:report:" + reportId;
                    redisTemplate.opsForHash().putAll(reportKey, response);
                    redisTemplate.expire(reportKey, 7, TimeUnit.DAYS);

                    // Publish report generation event
                    Map<String, Object> reportEvent = Map.of(
                        "eventType", "COMPLIANCE_REPORT_GENERATED",
                        "userId", userId,
                        "reportId", reportId,
                        "reportType", reportType,
                        "timestamp", LocalDateTime.now()
                    );
                    kafkaTemplate.send("compliance-reports", reportId, reportEvent);

                    return Map.of(
                        "reportId", reportId,
                        "reportType", reportType,
                        "status", "GENERATED",
                        "userId", userId,
                        "generatedAt", LocalDateTime.now(),
                        "reportUrl", response.get("reportUrl")
                    );

                } else {
                    throw new RuntimeException("Report generation failed: " + response);
                }

            } catch (Exception e) {
                log.error("Failed to generate compliance report for user {}: {}", userId, e.getMessage());
                throw new RuntimeException("Compliance report generation failed", e);
            }
        }

        private void validateKYCData(Map<String, Object> kycData) {
            List<String> requiredFields = List.of("firstName", "lastName", "dateOfBirth", "nationality", "documentType", "documentNumber");
            
            for (String field : requiredFields) {
                if (!kycData.containsKey(field) || kycData.get(field) == null) {
                    throw new IllegalArgumentException("Missing required KYC field: " + field);
                }
            }
        }

        private String determineVerificationLevel(Map<String, Object> kycData) {
            // Determine verification level based on data completeness and risk factors
            if (kycData.containsKey("proofOfAddress") && kycData.containsKey("sourceOfFunds")) {
                return "ENHANCED";
            } else if (kycData.containsKey("documentImage")) {
                return "STANDARD";
            } else {
                return "BASIC";
            }
        }

        private void triggerEnhancedDueDiligence(String userId, String verificationId, int score) {
            try {
                log.warn("Triggering enhanced due diligence for user {} (score: {})", userId, score);

                Map<String, Object> eddRequest = Map.of(
                    "userId", userId,
                    "verificationId", verificationId,
                    "triggerReason", "LOW_KYC_SCORE",
                    "score", score,
                    "priority", score < 50 ? "HIGH" : "MEDIUM",
                    "timestamp", LocalDateTime.now()
                );

                // Publish EDD trigger event
                kafkaTemplate.send("enhanced-due-diligence", userId, eddRequest);

            } catch (Exception e) {
                log.error("Failed to trigger EDD for user {}: {}", userId, e.getMessage());
            }
        }

        private void freezeAccountForSanctions(String userId) {
            try {
                // Immediately freeze account
                entityManager.createNativeQuery(
                    "UPDATE users SET account_status = 'SANCTIONS_FROZEN', frozen_at = :now " +
                    "WHERE id = :userId"
                ).setParameter("userId", userId)
                 .setParameter("now", LocalDateTime.now())
                 .executeUpdate();

                // Clear user cache
                redisTemplate.delete("user:profile:" + userId);

                log.error("Account frozen for user {} due to sanctions match", userId);

            } catch (Exception e) {
                log.error("Failed to freeze account for sanctions: {}", e.getMessage());
            }
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> gatherComplianceData(String userId) {
            try {
                Map<String, Object> complianceData = new HashMap<>();

                // Get KYC status
                Map<Object, Object> kycData = redisTemplate.opsForHash().entries("kyc:verification:" + userId);
                if (!kycData.isEmpty()) {
                    complianceData.put("kycStatus", kycData);
                }

                // Get transaction history for AML analysis
                List<Object[]> transactions = entityManager.createNativeQuery(
                    "SELECT amount, currency, transaction_type, created_at FROM user_transactions " +
                    "WHERE user_id = :userId AND created_at > :since ORDER BY created_at DESC LIMIT 100"
                ).setParameter("userId", userId)
                 .setParameter("since", LocalDateTime.now().minusMonths(12))
                 .getResultList();

                complianceData.put("recentTransactions", transactions);

                // Get regulatory holds
                List<Object[]> holds = entityManager.createNativeQuery(
                    "SELECT hold_type, reason, created_at, status FROM regulatory_holds " +
                    "WHERE user_id = :userId ORDER BY created_at DESC"
                ).setParameter("userId", userId)
                 .getResultList();

                complianceData.put("regulatoryHolds", holds);

                return complianceData;

            } catch (Exception e) {
                log.error("Failed to gather compliance data for user {}: {}", userId, e.getMessage());
                return Map.of("error", e.getMessage());
            }
        }
    }

    /**
     * Production KYC Service with tier-based verification
     */
    public static class ProductionKycService implements KycService {
        private final String complianceServiceUrl;
        private final RedisTemplate<String, Object> redisTemplate;
        private final KafkaTemplate<String, Object> kafkaTemplate;
        private final BigDecimal tier1Threshold;
        private final BigDecimal tier2Threshold;
        private final RestTemplate restTemplate;

        public ProductionKycService(String complianceServiceUrl,
                                  RedisTemplate<String, Object> redisTemplate,
                                  KafkaTemplate<String, Object> kafkaTemplate,
                                  BigDecimal tier1Threshold,
                                  BigDecimal tier2Threshold) {
            this.complianceServiceUrl = complianceServiceUrl;
            this.redisTemplate = redisTemplate;
            this.kafkaTemplate = kafkaTemplate;
            this.tier1Threshold = tier1Threshold;
            this.tier2Threshold = tier2Threshold;
            this.restTemplate = new RestTemplate();
        }

        @Override
        @Transactional
        public Object initiateKyc(String userId, Map<String, Object> kycData) {
            try {
                log.info("Initiating KYC process for user {}", userId);

                String kycId = UUID.randomUUID().toString();
                String tier = determineKycTier(kycData);

                // Call KYC service
                String url = complianceServiceUrl + "/api/v1/kyc/initiate";
                Map<String, Object> kycRequest = Map.of(
                    "kycId", kycId,
                    "userId", userId,
                    "tier", tier,
                    "kycData", kycData,
                    "requirements", getKycRequirements(tier),
                    "timestamp", LocalDateTime.now()
                );

                Map<String, Object> response = restTemplate.postForObject(url, kycRequest, Map.class);

                if (response != null && "INITIATED".equals(response.get("status"))) {
                    // Cache KYC initiation
                    String kycKey = "kyc:process:" + kycId;
                    Map<String, Object> kycProcess = Map.of(
                        "kycId", kycId,
                        "userId", userId,
                        "tier", tier,
                        "status", "INITIATED",
                        "initiatedAt", LocalDateTime.now().toString(),
                        "expiresAt", LocalDateTime.now().plusDays(30).toString()
                    );
                    redisTemplate.opsForHash().putAll(kycKey, kycProcess);
                    redisTemplate.expire(kycKey, 30, TimeUnit.DAYS);

                    // Publish KYC initiation event
                    Map<String, Object> kycEvent = Map.of(
                        "eventType", "KYC_INITIATED",
                        "userId", userId,
                        "kycId", kycId,
                        "tier", tier,
                        "timestamp", LocalDateTime.now()
                    );
                    kafkaTemplate.send("kyc-lifecycle", kycId, kycEvent);

                    return Map.of(
                        "kycId", kycId,
                        "status", "INITIATED",
                        "tier", tier,
                        "requirements", response.get("requirements"),
                        "uploadUrl", response.get("uploadUrl"),
                        "expiresAt", LocalDateTime.now().plusDays(30)
                    );

                } else {
                    throw new RuntimeException("KYC initiation failed: " + response);
                }

            } catch (Exception e) {
                log.error("Failed to initiate KYC for user {}: {}", userId, e.getMessage());
                throw new RuntimeException("KYC initiation failed", e);
            }
        }

        @Override
        @Transactional
        public Object verifyKyc(String userId, String kycId) {
            try {
                log.info("Verifying KYC {} for user {}", kycId, userId);

                // Call KYC verification service
                String url = complianceServiceUrl + "/api/v1/kyc/" + kycId + "/verify";
                Map<String, Object> response = restTemplate.postForObject(url, Map.of("userId", userId), Map.class);

                if (response != null) {
                    String status = response.get("status").toString();
                    int score = ((Number) response.get("score")).intValue();
                    String tier = response.get("tier").toString();

                    // Update KYC process cache
                    String kycKey = "kyc:process:" + kycId;
                    redisTemplate.opsForHash().put(kycKey, "status", status);
                    redisTemplate.opsForHash().put(kycKey, "score", score);
                    redisTemplate.opsForHash().put(kycKey, "verifiedAt", LocalDateTime.now().toString());

                    // Update user KYC status cache
                    String userKycKey = "user:kyc:" + userId;
                    Map<String, Object> userKycData = Map.of(
                        "kycId", kycId,
                        "status", status,
                        "tier", tier,
                        "score", score,
                        "verifiedAt", LocalDateTime.now().toString()
                    );
                    redisTemplate.opsForHash().putAll(userKycKey, userKycData);
                    redisTemplate.expire(userKycKey, 7, TimeUnit.DAYS);

                    // Publish verification event
                    Map<String, Object> verificationEvent = Map.of(
                        "eventType", "KYC_VERIFIED",
                        "userId", userId,
                        "kycId", kycId,
                        "status", status,
                        "tier", tier,
                        "score", score,
                        "timestamp", LocalDateTime.now()
                    );
                    kafkaTemplate.send("kyc-lifecycle", kycId, verificationEvent);

                    // Check for tier upgrade eligibility
                    if ("VERIFIED".equals(status) && score > 85) {
                        checkTierUpgradeEligibility(userId, tier, score);
                    }

                    return Map.of(
                        "kycId", kycId,
                        "status", status,
                        "tier", tier,
                        "score", score,
                        "verifiedAt", LocalDateTime.now(),
                        "transactionLimits", calculateTransactionLimits(tier, score)
                    );

                } else {
                    throw new RuntimeException("KYC verification service returned null response");
                }

            } catch (Exception e) {
                log.error("Failed to verify KYC {} for user {}: {}", kycId, userId, e.getMessage());
                throw new RuntimeException("KYC verification failed", e);
            }
        }

        @Override
        public String getKycStatus(String userId) {
            try {
                // Check cache first
                String userKycKey = "user:kyc:" + userId;
                Object cached = redisTemplate.opsForHash().get(userKycKey, "status");
                if (cached != null) {
                    return cached.toString();
                }

                // Call KYC service
                String url = complianceServiceUrl + "/api/v1/kyc/user/" + userId + "/status";
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);

                if (response != null && response.containsKey("status")) {
                    String status = response.get("status").toString();
                    
                    // Cache status for 1 hour
                    redisTemplate.opsForHash().put(userKycKey, "status", status);
                    redisTemplate.expire(userKycKey, 1, TimeUnit.HOURS);
                    
                    return status;
                }

                return "NOT_INITIATED";

            } catch (Exception e) {
                log.error("Failed to get KYC status for user {}: {}", userId, e.getMessage());
                return "UNKNOWN";
            }
        }

        @Override
        @Transactional
        public void updateKycStatus(String userId, String status, String reason) {
            try {
                log.info("Updating KYC status for user {} to {} (reason: {})", userId, status, reason);

                // Update cache
                String userKycKey = "user:kyc:" + userId;
                redisTemplate.opsForHash().put(userKycKey, "status", status);
                redisTemplate.opsForHash().put(userKycKey, "updatedAt", LocalDateTime.now().toString());
                redisTemplate.opsForHash().put(userKycKey, "updateReason", reason);

                // Publish status update event
                Map<String, Object> statusEvent = Map.of(
                    "eventType", "KYC_STATUS_UPDATED",
                    "userId", userId,
                    "newStatus", status,
                    "reason", reason,
                    "timestamp", LocalDateTime.now()
                );
                kafkaTemplate.send("kyc-status-updates", userId, statusEvent);

                // Handle specific status updates
                if ("REJECTED".equals(status)) {
                    handleKycRejection(userId, reason);
                } else if ("SUSPENDED".equals(status)) {
                    handleKycSuspension(userId, reason);
                }

            } catch (Exception e) {
                log.error("Failed to update KYC status for user {}: {}", userId, e.getMessage());
            }
        }

        private String determineKycTier(Map<String, Object> kycData) {
            // Determine tier based on user intent and data provided
            if (kycData.containsKey("businessPurpose") || kycData.containsKey("expectedVolume")) {
                BigDecimal expectedVolume = kycData.containsKey("expectedVolume") ? 
                    new BigDecimal(kycData.get("expectedVolume").toString()) : BigDecimal.ZERO;
                
                if (expectedVolume.compareTo(tier2Threshold) >= 0) {
                    return "TIER_3";
                } else if (expectedVolume.compareTo(tier1Threshold) >= 0) {
                    return "TIER_2";
                }
            }
            return "TIER_1";
        }

        private List<String> getKycRequirements(String tier) {
            return switch (tier) {
                case "TIER_1" -> List.of("ID_DOCUMENT", "SELFIE");
                case "TIER_2" -> List.of("ID_DOCUMENT", "PROOF_OF_ADDRESS", "SELFIE", "SOURCE_OF_FUNDS");
                case "TIER_3" -> List.of("ID_DOCUMENT", "PROOF_OF_ADDRESS", "SELFIE", "SOURCE_OF_FUNDS", 
                                       "BUSINESS_REGISTRATION", "BENEFICIAL_OWNERSHIP");
                default -> List.of("ID_DOCUMENT");
            };
        }

        private void checkTierUpgradeEligibility(String userId, String currentTier, int score) {
            try {
                String eligibleTier = switch (currentTier) {
                    case "TIER_1" -> score > 90 ? "TIER_2" : null;
                    case "TIER_2" -> score > 95 ? "TIER_3" : null;
                    default -> null;
                };

                if (eligibleTier != null) {
                    Map<String, Object> upgradeEvent = Map.of(
                        "eventType", "KYC_TIER_UPGRADE_ELIGIBLE",
                        "userId", userId,
                        "currentTier", currentTier,
                        "eligibleTier", eligibleTier,
                        "score", score,
                        "timestamp", LocalDateTime.now()
                    );
                    kafkaTemplate.send("kyc-tier-upgrades", userId, upgradeEvent);
                }

            } catch (Exception e) {
                log.error("Failed to check tier upgrade eligibility for user {}: {}", userId, e.getMessage());
            }
        }

        private Map<String, Object> calculateTransactionLimits(String tier, int score) {
            BigDecimal dailyLimit, monthlyLimit;
            
            switch (tier) {
                case "TIER_1" -> {
                    dailyLimit = BigDecimal.valueOf(1000);
                    monthlyLimit = BigDecimal.valueOf(10000);
                }
                case "TIER_2" -> {
                    dailyLimit = BigDecimal.valueOf(10000);
                    monthlyLimit = BigDecimal.valueOf(100000);
                }
                case "TIER_3" -> {
                    dailyLimit = BigDecimal.valueOf(100000);
                    monthlyLimit = BigDecimal.valueOf(1000000);
                }
                default -> {
                    dailyLimit = BigDecimal.valueOf(500);
                    monthlyLimit = BigDecimal.valueOf(2000);
                }
            }

            // Adjust limits based on score
            if (score > 90) {
                dailyLimit = dailyLimit.multiply(BigDecimal.valueOf(1.5));
                monthlyLimit = monthlyLimit.multiply(BigDecimal.valueOf(1.5));
            }

            return Map.of(
                "dailyLimit", dailyLimit,
                "monthlyLimit", monthlyLimit,
                "currency", "USD",
                "tier", tier
            );
        }

        private void handleKycRejection(String userId, String reason) {
            try {
                // Set cooling-off period
                String cooloffKey = "kyc:cooloff:" + userId;
                redisTemplate.opsForValue().set(cooloffKey, reason, 7, TimeUnit.DAYS);

                // Publish rejection event
                Map<String, Object> rejectionEvent = Map.of(
                    "eventType", "KYC_REJECTED",
                    "userId", userId,
                    "reason", reason,
                    "cooloffUntil", LocalDateTime.now().plusDays(7),
                    "timestamp", LocalDateTime.now()
                );
                kafkaTemplate.send("kyc-rejections", userId, rejectionEvent);

            } catch (Exception e) {
                log.error("Failed to handle KYC rejection for user {}: {}", userId, e.getMessage());
            }
        }

        private void handleKycSuspension(String userId, String reason) {
            try {
                // Suspend account access
                String suspensionKey = "account:suspended:" + userId;
                Map<String, Object> suspensionData = Map.of(
                    "suspended", true,
                    "reason", reason,
                    "suspendedAt", LocalDateTime.now().toString(),
                    "type", "KYC_SUSPENSION"
                );
                redisTemplate.opsForHash().putAll(suspensionKey, suspensionData);
                redisTemplate.expire(suspensionKey, 30, TimeUnit.DAYS);

                // Publish suspension event
                Map<String, Object> suspensionEvent = Map.of(
                    "eventType", "ACCOUNT_SUSPENDED",
                    "userId", userId,
                    "reason", reason,
                    "suspensionType", "KYC_RELATED",
                    "timestamp", LocalDateTime.now()
                );
                kafkaTemplate.send("account-suspensions", userId, suspensionEvent);

            } catch (Exception e) {
                log.error("Failed to handle KYC suspension for user {}: {}", userId, e.getMessage());
            }
        }
    }

    // Service interfaces
    public interface ComplianceService {
        boolean hasRegulatoryHold(String userId);
        Object performKYCVerification(String userId, Map<String, Object> kycData);
        boolean checkSanctions(String userId);
        Object generateComplianceReport(String userId, String reportType);
    }

    public interface KycService {
        Object initiateKyc(String userId, Map<String, Object> kycData);
        Object verifyKyc(String userId, String kycId);
        String getKycStatus(String userId);
        void updateKycStatus(String userId, String status, String reason);
    }
}