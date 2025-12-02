package com.waqiti.arpayment.repository;

import com.waqiti.arpayment.domain.ARPaymentExperience;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ARPaymentExperienceRepository extends JpaRepository<ARPaymentExperience, UUID> {
    
    Optional<ARPaymentExperience> findByExperienceId(String experienceId);
    
    List<ARPaymentExperience> findBySessionId(UUID sessionId);
    
    Page<ARPaymentExperience> findByUserId(UUID userId, Pageable pageable);
    
    List<ARPaymentExperience> findByUserIdAndStatus(UUID userId, ARPaymentExperience.ExperienceStatus status);
    
    @Query("SELECT e FROM ARPaymentExperience e WHERE e.sessionId = :sessionId " +
           "AND e.status IN ('INITIATED', 'SCANNING', 'PROCESSING', 'CONFIRMING')")
    List<ARPaymentExperience> findActiveExperiencesBySessionId(@Param("sessionId") UUID sessionId);
    
    @Query("SELECT e FROM ARPaymentExperience e WHERE e.userId = :userId " +
           "AND e.experienceType = :experienceType " +
           "AND e.status = 'COMPLETED' " +
           "ORDER BY e.completedAt DESC")
    Page<ARPaymentExperience> findCompletedExperiencesByTypeAndUser(
            @Param("userId") UUID userId,
            @Param("experienceType") ARPaymentExperience.ExperienceType experienceType,
            Pageable pageable);
    
    @Query("SELECT COUNT(e) FROM ARPaymentExperience e WHERE e.userId = :userId " +
           "AND e.status = 'COMPLETED' AND e.paymentId IS NOT NULL")
    long countSuccessfulPaymentsByUser(@Param("userId") UUID userId);
    
    @Query("SELECT SUM(e.amount) FROM ARPaymentExperience e WHERE e.userId = :userId " +
           "AND e.status = 'COMPLETED' AND e.paymentId IS NOT NULL")
    BigDecimal getTotalPaymentAmountByUser(@Param("userId") UUID userId);
    
    @Query("SELECT e.experienceType, COUNT(e), SUM(e.amount) FROM ARPaymentExperience e " +
           "WHERE e.userId = :userId AND e.status = 'COMPLETED' " +
           "GROUP BY e.experienceType")
    List<Object[]> getPaymentStatisticsByType(@Param("userId") UUID userId);
    
    @Query("SELECT AVG(e.interactionDurationSeconds) FROM ARPaymentExperience e " +
           "WHERE e.userId = :userId AND e.status = 'COMPLETED' " +
           "AND e.interactionDurationSeconds IS NOT NULL")
    Double getAverageInteractionDuration(@Param("userId") UUID userId);
    
    @Query("SELECT e FROM ARPaymentExperience e WHERE e.recipientId = :recipientId " +
           "AND e.status = 'COMPLETED' " +
           "ORDER BY e.completedAt DESC")
    Page<ARPaymentExperience> findReceivedPayments(@Param("recipientId") UUID recipientId, Pageable pageable);
    
    @Query("SELECT e FROM ARPaymentExperience e WHERE e.merchantId = :merchantId " +
           "AND e.startedAt >= :startDate AND e.startedAt <= :endDate " +
           "AND e.status = 'COMPLETED'")
    List<ARPaymentExperience> findMerchantPaymentsByDateRange(
            @Param("merchantId") UUID merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT e.paymentMethod, COUNT(e) FROM ARPaymentExperience e " +
           "WHERE e.userId = :userId AND e.status = 'COMPLETED' " +
           "GROUP BY e.paymentMethod")
    List<Object[]> getPaymentMethodDistribution(@Param("userId") UUID userId);
    
    @Query("SELECT e FROM ARPaymentExperience e WHERE e.isSharedToFeed = true " +
           "AND e.status = 'COMPLETED' " +
           "ORDER BY e.completedAt DESC")
    Page<ARPaymentExperience> findSocialSharedPayments(Pageable pageable);
    
    @Query("SELECT e FROM ARPaymentExperience e WHERE e.achievementUnlocked IS NOT NULL " +
           "AND e.userId = :userId")
    List<ARPaymentExperience> findUserAchievements(@Param("userId") UUID userId);
    
    @Query("SELECT SUM(e.pointsEarned) FROM ARPaymentExperience e " +
           "WHERE e.userId = :userId AND e.pointsEarned > 0")
    Integer getTotalPointsEarned(@Param("userId") UUID userId);
    
    @Query(value = "SELECT * FROM ar_payment_experiences WHERE " +
           "ST_DWithin(" +
           "ST_MakePoint(" +
           "CAST(jsonb_extract_path_text(spatial_payment_data, 'dropLocation', 'longitude') AS DOUBLE PRECISION), " +
           "CAST(jsonb_extract_path_text(spatial_payment_data, 'dropLocation', 'latitude') AS DOUBLE PRECISION)" +
           ")::geography, " +
           "ST_MakePoint(:longitude, :latitude)::geography, " +
           ":radiusMeters) " +
           "AND experience_type = 'SPATIAL_DROP' " +
           "AND status = 'INITIATED'", nativeQuery = true)
    List<ARPaymentExperience> findNearbySpatialPayments(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") double radiusMeters);
    
    @Query("SELECT e FROM ARPaymentExperience e WHERE e.qrCodeData = :qrCodeData " +
           "AND e.status IN ('INITIATED', 'PROCESSING') " +
           "ORDER BY e.startedAt DESC")
    Optional<ARPaymentExperience> findActiveByQRCodeData(@Param("qrCodeData") String qrCodeData);
    
    @Modifying
    @Query("UPDATE ARPaymentExperience e SET e.status = 'TIMEOUT' " +
           "WHERE e.status IN ('INITIATED', 'SCANNING', 'PROCESSING') " +
           "AND e.startedAt < :threshold")
    int timeoutOldExperiences(@Param("threshold") LocalDateTime threshold);
    
    @Query("SELECT e.errorMessage, COUNT(e) FROM ARPaymentExperience e " +
           "WHERE e.status = 'FAILED' AND e.errorMessage IS NOT NULL " +
           "GROUP BY e.errorMessage " +
           "ORDER BY COUNT(e) DESC")
    List<Object[]> getTopErrorMessages(Pageable pageable);
    
    @Query("SELECT AVG(e.gestureAccuracy) FROM ARPaymentExperience e " +
           "WHERE e.userId = :userId AND e.paymentMethod = 'GESTURE' " +
           "AND e.gestureAccuracy IS NOT NULL")
    Double getAverageGestureAccuracy(@Param("userId") UUID userId);
    
    @Query("SELECT DATE(e.startedAt), COUNT(e), SUM(e.amount) FROM ARPaymentExperience e " +
           "WHERE e.userId = :userId AND e.status = 'COMPLETED' " +
           "AND e.startedAt >= :startDate " +
           "GROUP BY DATE(e.startedAt) " +
           "ORDER BY DATE(e.startedAt)")
    List<Object[]> getDailyPaymentTrend(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate);
}