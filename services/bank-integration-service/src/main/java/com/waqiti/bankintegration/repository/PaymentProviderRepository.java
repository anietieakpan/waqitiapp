package com.waqiti.bankintegration.repository;

import com.waqiti.bankintegration.domain.PaymentProvider;
import com.waqiti.bankintegration.domain.ProviderType;
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
public interface PaymentProviderRepository extends JpaRepository<PaymentProvider, UUID> {
    
    Optional<PaymentProvider> findByProviderName(String providerName);
    
    Optional<PaymentProvider> findByProviderType(ProviderType providerType);
    
    List<PaymentProvider> findByIsActiveTrue();
    
    List<PaymentProvider> findByIsActiveTrueAndProviderType(ProviderType providerType);
    
    @Query("SELECT pp FROM PaymentProvider pp WHERE pp.isActive = true AND pp.priority > 0 ORDER BY pp.priority DESC")
    List<PaymentProvider> findActiveProvidersByPriority();
    
    @Query("SELECT pp FROM PaymentProvider pp WHERE pp.isActive = true AND " +
           "pp.providerType = :providerType AND pp.priority > 0 ORDER BY pp.priority DESC")
    List<PaymentProvider> findActiveProvidersByTypeAndPriority(@Param("providerType") ProviderType providerType);
    
    @Query("SELECT pp FROM PaymentProvider pp WHERE pp.isActive = true AND " +
           "pp.supportedCurrencies LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:currency, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%')")
    List<PaymentProvider> findProvidersByCurrency(@Param("currency") String currency);
    
    @Query("SELECT pp FROM PaymentProvider pp WHERE pp.isActive = true AND " +
           "pp.supportedCountries LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:country, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%')")
    List<PaymentProvider> findProvidersByCountry(@Param("country") String country);
    
    @Query("SELECT pp FROM PaymentProvider pp WHERE pp.isActive = true AND " +
           "pp.minTransactionAmount <= :amount AND pp.maxTransactionAmount >= :amount")
    List<PaymentProvider> findProvidersByTransactionAmount(@Param("amount") BigDecimal amount);
    
    @Query("SELECT pp FROM PaymentProvider pp WHERE pp.isActive = true AND " +
           "pp.supportedPaymentMethods LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:paymentMethod, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%')")
    List<PaymentProvider> findProvidersByPaymentMethod(@Param("paymentMethod") String paymentMethod);
    
    @Query("SELECT pp FROM PaymentProvider pp WHERE pp.healthStatus = 'HEALTHY' AND pp.isActive = true")
    List<PaymentProvider> findHealthyProviders();
    
    @Query("SELECT pp FROM PaymentProvider pp WHERE pp.healthStatus IN ('UNHEALTHY', 'DEGRADED') " +
           "AND pp.lastHealthCheck < :threshold")
    List<PaymentProvider> findUnhealthyProvidersNeedingCheck(@Param("threshold") LocalDateTime threshold);
    
    @Query("SELECT COUNT(pp) FROM PaymentProvider pp WHERE pp.isActive = true")
    Long countActiveProviders();
    
    @Query("SELECT pp.providerType, COUNT(pp) FROM PaymentProvider pp WHERE pp.isActive = true " +
           "GROUP BY pp.providerType")
    List<Object[]> countProvidersByType();
    
    @Modifying
    @Query("UPDATE PaymentProvider pp SET pp.healthStatus = :status, pp.lastHealthCheck = :checkTime " +
           "WHERE pp.id = :providerId")
    void updateHealthStatus(@Param("providerId") UUID providerId, 
                          @Param("status") String status, 
                          @Param("checkTime") LocalDateTime checkTime);
    
    @Modifying
    @Query("UPDATE PaymentProvider pp SET pp.successRate = :successRate, pp.averageResponseTime = :responseTime, " +
           "pp.totalTransactions = pp.totalTransactions + 1 WHERE pp.id = :providerId")
    void updateProviderMetrics(@Param("providerId") UUID providerId, 
                             @Param("successRate") Double successRate, 
                             @Param("responseTime") Long responseTime);
    
    @Query("SELECT pp FROM PaymentProvider pp WHERE pp.isActive = true AND " +
           "pp.providerType = :providerType AND pp.healthStatus = 'HEALTHY' " +
           "ORDER BY pp.successRate DESC, pp.averageResponseTime ASC")
    List<PaymentProvider> findBestPerformingProviders(@Param("providerType") ProviderType providerType);
    
    Page<PaymentProvider> findByIsActive(boolean isActive, Pageable pageable);
    
    @Query("SELECT pp FROM PaymentProvider pp WHERE " +
           "(:providerType IS NULL OR pp.providerType = :providerType) AND " +
           "(:isActive IS NULL OR pp.isActive = :isActive) AND " +
           "(:healthStatus IS NULL OR pp.healthStatus = :healthStatus)")
    Page<PaymentProvider> findByFilters(@Param("providerType") ProviderType providerType,
                                       @Param("isActive") Boolean isActive,
                                       @Param("healthStatus") String healthStatus,
                                       Pageable pageable);
    
    @Query("SELECT pp FROM PaymentProvider pp WHERE pp.maintenanceMode = true")
    List<PaymentProvider> findProvidersInMaintenance();
    
    @Query("SELECT pp FROM PaymentProvider pp WHERE pp.rateLimitPerMinute > 0 AND " +
           "pp.currentRateUsage >= pp.rateLimitPerMinute * 0.8")
    List<PaymentProvider> findProvidersNearRateLimit();
}