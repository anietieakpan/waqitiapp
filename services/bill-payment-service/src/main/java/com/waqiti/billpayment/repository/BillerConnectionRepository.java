package com.waqiti.billpayment.repository;

import com.waqiti.billpayment.entity.BillerConnection;
import com.waqiti.billpayment.entity.ConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for BillerConnection entity operations
 */
@Repository
public interface BillerConnectionRepository extends JpaRepository<BillerConnection, UUID> {

    List<BillerConnection> findByUserId(String userId);

    List<BillerConnection> findByUserIdAndStatus(String userId, ConnectionStatus status);

    Optional<BillerConnection> findByUserIdAndBillerIdAndAccountNumber(String userId, UUID billerId, String accountNumber);

    List<BillerConnection> findByBillerId(UUID billerId);

    @Query("SELECT bc FROM BillerConnection bc WHERE bc.autoImportEnabled = true " +
           "AND bc.status = 'ACTIVE' AND bc.nextImportAt <= :now")
    List<BillerConnection> findConnectionsDueForImport(@Param("now") LocalDateTime now);

    @Query("SELECT bc FROM BillerConnection bc WHERE bc.status = 'REAUTH_REQUIRED'")
    List<BillerConnection> findConnectionsNeedingReauth();

    long countByUserIdAndStatus(String userId, ConnectionStatus status);

    boolean existsByUserIdAndBillerIdAndAccountNumber(String userId, UUID billerId, String accountNumber);

    @Query("UPDATE BillerConnection bc SET bc.deletedAt = :now, bc.deletedBy = :deletedBy WHERE bc.id = :id")
    void softDelete(@Param("id") UUID id, @Param("deletedBy") String deletedBy, @Param("now") LocalDateTime now);

    /**
     * Find connection by user ID and biller ID and account number
     */
    Optional<BillerConnection> findByUserIdAndBillerIdAndAccountNumber(
            String userId, UUID billerId, String accountNumber);

    /**
     * Find connection by ID and user ID
     */
    Optional<BillerConnection> findByIdAndUserId(UUID id, String userId);

    /**
     * Find active connections for user
     */
    List<BillerConnection> findByUserIdAndIsActive(String userId, boolean isActive);

    /**
     * Unset default flag for all user's connections
     */
    @Query("UPDATE BillerConnection bc SET bc.isDefault = false WHERE bc.userId = :userId")
    void unsetDefaultsForUser(@Param("userId") String userId);
}
