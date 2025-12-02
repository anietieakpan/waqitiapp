/**
 * Crypto Address Repository
 * JPA repository for cryptocurrency address operations
 */
package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.CryptoAddress;
import com.waqiti.crypto.entity.AddressType;
import com.waqiti.crypto.entity.AddressStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CryptoAddressRepository extends JpaRepository<CryptoAddress, UUID> {

    /**
     * Find address by address string
     */
    Optional<CryptoAddress> findByAddress(String address);

    /**
     * Find all addresses for a wallet
     */
    List<CryptoAddress> findByWalletId(UUID walletId);

    /**
     * Find addresses by wallet ID and status
     */
    List<CryptoAddress> findByWalletIdAndStatus(UUID walletId, AddressStatus status);

    /**
     * Find addresses by wallet ID and type
     */
    List<CryptoAddress> findByWalletIdAndAddressType(UUID walletId, AddressType addressType);

    /**
     * Find addresses by wallet ID, type, and status
     */
    List<CryptoAddress> findByWalletIdAndAddressTypeAndStatus(
        UUID walletId, AddressType addressType, AddressStatus status);

    /**
     * Find address by wallet ID and address index
     */
    Optional<CryptoAddress> findByWalletIdAndAddressIndex(UUID walletId, Integer addressIndex);

    /**
     * Find addresses by derivation path
     */
    List<CryptoAddress> findByDerivationPath(String derivationPath);

    /**
     * Find addresses by label
     */
    List<CryptoAddress> findByWalletIdAndLabel(UUID walletId, String label);

    /**
     * Get maximum address index for wallet
     */
    @Query("SELECT MAX(a.addressIndex) FROM CryptoAddress a WHERE a.walletId = :walletId")
    Optional<Integer> findMaxAddressIndexByWalletId(@Param("walletId") UUID walletId);

    /**
     * Get next available address index for wallet
     */
    @Query("SELECT COALESCE(MAX(a.addressIndex), -1) + 1 FROM CryptoAddress a WHERE a.walletId = :walletId")
    Integer getNextAddressIndex(@Param("walletId") UUID walletId);

    /**
     * Find unused receiving addresses for wallet
     */
    @Query("SELECT a FROM CryptoAddress a WHERE a.walletId = :walletId " +
           "AND a.addressType = 'RECEIVING' AND a.usedCount = 0 AND a.status = 'ACTIVE' " +
           "ORDER BY a.addressIndex ASC")
    List<CryptoAddress> findUnusedReceivingAddresses(@Param("walletId") UUID walletId);

    /**
     * Find next unused receiving address
     */
    @Query("SELECT a FROM CryptoAddress a WHERE a.walletId = :walletId " +
           "AND a.addressType = 'RECEIVING' AND a.usedCount = 0 AND a.status = 'ACTIVE' " +
           "ORDER BY a.addressIndex ASC LIMIT 1")
    Optional<CryptoAddress> findNextUnusedReceivingAddress(@Param("walletId") UUID walletId);

    /**
     * Find addresses used recently
     */
    @Query("SELECT a FROM CryptoAddress a WHERE a.walletId = :walletId " +
           "AND a.lastUsedAt >= :sinceTime ORDER BY a.lastUsedAt DESC")
    List<CryptoAddress> findRecentlyUsedAddresses(
        @Param("walletId") UUID walletId, 
        @Param("sinceTime") LocalDateTime sinceTime
    );

    /**
     * Find addresses by usage count
     */
    @Query("SELECT a FROM CryptoAddress a WHERE a.walletId = :walletId AND a.usedCount >= :minUsage " +
           "ORDER BY a.usedCount DESC")
    List<CryptoAddress> findAddressesByUsageCount(
        @Param("walletId") UUID walletId, 
        @Param("minUsage") Integer minUsage
    );

    /**
     * Count addresses by type for wallet
     */
    @Query("SELECT COUNT(a) FROM CryptoAddress a WHERE a.walletId = :walletId AND a.addressType = :addressType")
    long countByWalletIdAndAddressType(@Param("walletId") UUID walletId, @Param("addressType") AddressType addressType);

    /**
     * Count active addresses for wallet
     */
    @Query("SELECT COUNT(a) FROM CryptoAddress a WHERE a.walletId = :walletId AND a.status = 'ACTIVE'")
    long countActiveAddressByWalletId(@Param("walletId") UUID walletId);

    /**
     * Update address usage
     */
    @Modifying
    @Query("UPDATE CryptoAddress a SET a.usedCount = a.usedCount + 1, a.lastUsedAt = CURRENT_TIMESTAMP, " +
           "a.status = CASE WHEN a.usedCount = 0 THEN 'USED' ELSE a.status END " +
           "WHERE a.address = :address")
    int incrementAddressUsage(@Param("address") String address);

    /**
     * Update address status
     */
    @Modifying
    @Query("UPDATE CryptoAddress a SET a.status = :status WHERE a.id = :addressId")
    int updateAddressStatus(@Param("addressId") UUID addressId, @Param("status") AddressStatus status);

    /**
     * Update address label
     */
    @Modifying
    @Query("UPDATE CryptoAddress a SET a.label = :label WHERE a.id = :addressId")
    int updateAddressLabel(@Param("addressId") UUID addressId, @Param("label") String label);

    /**
     * Deactivate old unused addresses
     */
    @Modifying
    @Query("UPDATE CryptoAddress a SET a.status = 'INACTIVE' WHERE a.walletId = :walletId " +
           "AND a.usedCount = 0 AND a.createdAt < :cutoffDate AND a.status = 'ACTIVE'")
    int deactivateOldUnusedAddresses(
        @Param("walletId") UUID walletId, 
        @Param("cutoffDate") LocalDateTime cutoffDate
    );

    /**
     * Find addresses created after specific date
     */
    List<CryptoAddress> findByWalletIdAndCreatedAtAfter(UUID walletId, LocalDateTime createdAt);

    /**
     * Get address statistics for wallet
     */
    @Query("SELECT a.addressType, a.status, COUNT(a), COALESCE(SUM(a.usedCount), 0) " +
           "FROM CryptoAddress a WHERE a.walletId = :walletId GROUP BY a.addressType, a.status")
    List<Object[]> getAddressStatisticsByWalletId(@Param("walletId") UUID walletId);

    /**
     * Find duplicate addresses (should not happen, but for monitoring)
     */
    @Query("SELECT a.address, COUNT(a) FROM CryptoAddress a GROUP BY a.address HAVING COUNT(a) > 1")
    List<Object[]> findDuplicateAddresses();

    /**
     * Find orphaned addresses (addresses without valid wallet)
     */
    @Query("SELECT a FROM CryptoAddress a WHERE a.walletId NOT IN (SELECT w.id FROM CryptoWallet w)")
    List<CryptoAddress> findOrphanedAddresses();

    /**
     * Clean up old inactive addresses
     */
    @Modifying
    @Query("DELETE FROM CryptoAddress a WHERE a.status = 'INACTIVE' AND a.usedCount = 0 " +
           "AND a.createdAt < :cutoffDate")
    int deleteOldInactiveAddresses(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find addresses that need to be archived
     */
    @Query("SELECT a FROM CryptoAddress a WHERE a.status = 'USED' AND a.lastUsedAt < :archiveDate")
    List<CryptoAddress> findAddressesForArchiving(@Param("archiveDate") LocalDateTime archiveDate);
}