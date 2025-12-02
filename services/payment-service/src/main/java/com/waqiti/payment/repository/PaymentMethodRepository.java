package com.waqiti.payment.repository;

import com.waqiti.payment.domain.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {
    
    Optional<PaymentMethod> findByMethodId(String methodId);
    
    Optional<PaymentMethod> findByIdAndUserId(UUID id, UUID userId);
    
    Optional<PaymentMethod> findByMethodIdAndUserId(String methodId, UUID userId);
    
    List<PaymentMethod> findByUserIdAndStatus(UUID userId, PaymentMethod.PaymentMethodStatus status);
    
    Page<PaymentMethod> findByUserId(UUID userId, Pageable pageable);
    
    List<PaymentMethod> findByUserIdAndIsDefaultTrue(UUID userId);
    
    Optional<PaymentMethod> findFirstByUserIdAndIsDefaultTrue(UUID userId);
    
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.userId = :userId AND pm.methodType = :type AND pm.status = 'ACTIVE'")
    List<PaymentMethod> findActiveMethodsByUserIdAndType(@Param("userId") UUID userId, @Param("type") PaymentMethod.PaymentMethodType type);
    
    @Query("SELECT COUNT(pm) FROM PaymentMethod pm WHERE pm.userId = :userId AND pm.status = 'ACTIVE'")
    long countActiveMethodsByUserId(@Param("userId") UUID userId);
    
    @Modifying
    @Query("UPDATE PaymentMethod pm SET pm.isDefault = false WHERE pm.userId = :userId AND pm.id != :excludeId")
    void clearDefaultExcept(@Param("userId") UUID userId, @Param("excludeId") UUID excludeId);
    
    @Modifying
    @Query("UPDATE PaymentMethod pm SET pm.status = 'EXPIRED' WHERE pm.expiresAt < :date AND pm.status = 'ACTIVE'")
    int markExpiredMethods(@Param("date") LocalDate date);
    
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.expiresAt BETWEEN :startDate AND :endDate AND pm.status = 'ACTIVE'")
    List<PaymentMethod> findExpiringMethods(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    boolean existsByUserIdAndEncryptedDetailsAndStatus(UUID userId, String encryptedDetails, PaymentMethod.PaymentMethodStatus status);
}