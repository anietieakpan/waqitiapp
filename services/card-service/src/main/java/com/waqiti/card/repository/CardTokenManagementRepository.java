package com.waqiti.card.repository;

import com.waqiti.card.entity.CardTokenManagement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CardTokenManagementRepository - Spring Data JPA repository for CardTokenManagement entity
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Repository
public interface CardTokenManagementRepository extends JpaRepository<CardTokenManagement, UUID>, JpaSpecificationExecutor<CardTokenManagement> {

    Optional<CardTokenManagement> findByTokenId(String tokenId);

    Optional<CardTokenManagement> findByPanToken(String panToken);

    List<CardTokenManagement> findByCardId(UUID cardId);

    List<CardTokenManagement> findByUserId(UUID userId);

    List<CardTokenManagement> findByTokenType(String tokenType);

    List<CardTokenManagement> findByTokenStatus(String tokenStatus);

    @Query("SELECT t FROM CardTokenManagement t WHERE t.cardId = :cardId AND t.tokenStatus = 'ACTIVE' AND " +
           "(t.expiresAt IS NULL OR t.expiresAt > :currentDateTime) AND t.deletedAt IS NULL")
    List<CardTokenManagement> findActiveTokensByCardId(@Param("cardId") UUID cardId, @Param("currentDateTime") LocalDateTime currentDateTime);

    @Query("SELECT t FROM CardTokenManagement t WHERE t.tokenStatus = 'ACTIVE' AND t.expiresAt < :currentDateTime AND t.isExpired = false AND t.deletedAt IS NULL")
    List<CardTokenManagement> findExpiredTokens(@Param("currentDateTime") LocalDateTime currentDateTime);

    @Query("SELECT t FROM CardTokenManagement t WHERE t.deviceBound = true AND t.deviceId = :deviceId AND t.deletedAt IS NULL")
    List<CardTokenManagement> findTokensByDeviceId(@Param("deviceId") String deviceId);

    @Query("SELECT t FROM CardTokenManagement t WHERE t.isWalletToken = true AND t.walletProvider = :provider AND t.deletedAt IS NULL")
    List<CardTokenManagement> findWalletTokensByProvider(@Param("provider") String provider);

    @Query("SELECT t FROM CardTokenManagement t WHERE t.singleUse = true AND t.currentUsageCount > 0 AND t.deletedAt IS NULL")
    List<CardTokenManagement> findUsedSingleUseTokens();

    @Query("SELECT t FROM CardTokenManagement t WHERE t.isSuspended = true AND t.deletedAt IS NULL")
    List<CardTokenManagement> findSuspendedTokens();

    long countByCardId(UUID cardId);

    long countByTokenStatus(String tokenStatus);
}
