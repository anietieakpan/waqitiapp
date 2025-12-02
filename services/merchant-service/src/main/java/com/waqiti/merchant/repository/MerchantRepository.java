package com.waqiti.merchant.repository;

import com.waqiti.merchant.domain.Merchant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByMerchantId(String merchantId);
    
    Optional<Merchant> findByUserId(UUID userId);
    
    List<Merchant> findByVerificationStatus(Merchant.VerificationStatus verificationStatus);
    
    List<Merchant> findByStatus(Merchant.MerchantStatus status);
    
    Page<Merchant> findByBusinessNameContainingIgnoreCaseOrBusinessCategoryContainingIgnoreCase(
            String businessName, String businessCategory, Pageable pageable);
    
    @Query("SELECT m FROM Merchant m WHERE m.verificationStatus = :status AND m.createdAt < :cutoffDate")
    List<Merchant> findPendingVerificationOlderThan(
            @Param("status") Merchant.VerificationStatus status, 
            @Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Query("SELECT m FROM Merchant m WHERE m.totalVolume > :minVolume AND m.status = 'ACTIVE'")
    List<Merchant> findHighVolumeMerchants(@Param("minVolume") java.math.BigDecimal minVolume);
    
    @Query("SELECT m FROM Merchant m WHERE m.lastTransactionAt < :cutoffDate AND m.status = 'ACTIVE'")
    List<Merchant> findInactiveMerchants(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.status = 'ACTIVE'")
    long countActiveMerchants();
    
    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.verificationStatus = :status")
    long countByVerificationStatus(@Param("status") Merchant.VerificationStatus status);
    
    @Query("SELECT m FROM Merchant m WHERE m.businessAddress.city = :city AND m.status = 'ACTIVE'")
    List<Merchant> findByCity(@Param("city") String city);
    
    @Query("SELECT m FROM Merchant m WHERE m.businessAddress.state = :state AND m.status = 'ACTIVE'")
    List<Merchant> findByState(@Param("state") String state);
    
    @Query("SELECT m FROM Merchant m WHERE m.businessCategory = :category AND m.status = 'ACTIVE'")
    Page<Merchant> findByBusinessCategory(@Param("category") String category, Pageable pageable);
    
    boolean existsByTaxId(String taxId);
    
    boolean existsByRegistrationNumber(String registrationNumber);
    
    boolean existsByContactEmail(String contactEmail);
    
    @Query("SELECT m FROM Merchant m WHERE m.riskLevel = :riskLevel")
    List<Merchant> findByRiskLevel(@Param("riskLevel") String riskLevel);
    
    @Query("SELECT m FROM Merchant m WHERE m.createdAt BETWEEN :startDate AND :endDate")
    List<Merchant> findByCreatedAtBetween(
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);
    
    // N+1 Query optimizations
    
    /**
     * Find merchants with their payment methods using JOIN FETCH to avoid N+1
     */
    @Query("SELECT DISTINCT m FROM Merchant m " +
           "LEFT JOIN FETCH m.paymentMethods pm " +
           "WHERE m.id IN :merchantIds")
    List<Merchant> findByIdsWithPaymentMethods(@Param("merchantIds") List<UUID> merchantIds);
    
    /**
     * Find merchants with their transactions for analytics
     */
    @Query("SELECT DISTINCT m FROM Merchant m " +
           "LEFT JOIN FETCH m.transactions t " +
           "WHERE m.id IN :merchantIds " +
           "AND t.createdAt >= :fromDate")
    List<Merchant> findByIdsWithRecentTransactions(
            @Param("merchantIds") List<UUID> merchantIds,
            @Param("fromDate") LocalDateTime fromDate);
    
    /**
     * Find merchants with their verification documents
     */
    @Query("SELECT DISTINCT m FROM Merchant m " +
           "LEFT JOIN FETCH m.verificationDocuments vd " +
           "WHERE m.id IN :merchantIds")
    List<Merchant> findByIdsWithVerificationDocuments(@Param("merchantIds") List<UUID> merchantIds);
    
    /**
     * Optimized search with geographic proximity
     */
    @Query(value = "SELECT m.* FROM merchants m " +
           "WHERE (:query IS NULL OR " +
           "  to_tsvector('english', m.business_name || ' ' || m.business_category || ' ' || m.business_description) " +
           "  @@ plainto_tsquery('english', :query)) " +
           "AND (:category IS NULL OR m.business_category = :category) " +
           "AND (:location IS NULL OR m.business_address_city ILIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:location, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%') " +
           "                      OR m.business_address_state ILIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:location, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%')) " +
           "AND (:latitude IS NULL OR :longitude IS NULL OR :radiusKm IS NULL OR " +
           "  ST_DWithin(ST_SetSRID(ST_MakePoint(m.longitude, m.latitude), 4326), " +
           "             ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326), :radiusKm * 1000)) " +
           "AND m.status = 'ACTIVE' " +
           "ORDER BY " +
           "  CASE WHEN :latitude IS NOT NULL AND :longitude IS NOT NULL THEN " +
           "    ST_Distance(ST_SetSRID(ST_MakePoint(m.longitude, m.latitude), 4326), " +
           "                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)) " +
           "  ELSE 0 END, " +
           "  CASE WHEN :query IS NOT NULL THEN " +
           "    ts_rank(to_tsvector('english', m.business_name || ' ' || m.business_category), " +
           "            plainto_tsquery('english', :query)) " +
           "  ELSE 0 END DESC",
           nativeQuery = true)
    Page<Merchant> searchMerchants(
            @Param("query") String query,
            @Param("category") String category,
            @Param("location") String location,
            @Param("latitude") java.math.BigDecimal latitude,
            @Param("longitude") java.math.BigDecimal longitude,
            @Param("radiusKm") Integer radiusKm,
            Pageable pageable);
    
    /**
     * Check if merchant exists by business email or phone
     */
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END " +
           "FROM Merchant m WHERE m.businessEmail = :email OR m.businessPhone = :phone")
    boolean existsByBusinessEmailOrBusinessPhone(
            @Param("email") String email, 
            @Param("phone") String phone);
    
    /**
     * Get merchant risk profile efficiently
     */
    @Query("SELECT m.id, m.riskLevel, m.riskScore, m.lastRiskAssessment, " +
           "m.totalVolume, m.transactionCount, m.chargebackRate " +
           "FROM Merchant m WHERE m.id = :merchantId")
    Optional<MerchantRiskProfile> getMerchantRiskProfile(@Param("merchantId") UUID merchantId);
}