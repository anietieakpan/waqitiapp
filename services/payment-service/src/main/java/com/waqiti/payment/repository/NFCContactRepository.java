package com.waqiti.payment.repository;

import com.waqiti.payment.entity.NFCContact;
import com.waqiti.payment.entity.NFCContactStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for NFC contact management and social payment features
 */
@Repository
public interface NFCContactRepository extends JpaRepository<NFCContact, UUID>, JpaSpecificationExecutor<NFCContact> {

    /**
     * Find contact by user ID and contact user ID
     */
    Optional<NFCContact> findByUserIdAndContactUserId(String userId, String contactUserId);
    
    /**
     * Check if contact relationship exists
     */
    boolean existsByUserIdAndContactUserId(String userId, String contactUserId);
    
    /**
     * Find all contacts for a user
     */
    Page<NFCContact> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    /**
     * Find active contacts for a user
     */
    List<NFCContact> findByUserIdAndStatus(String userId, NFCContactStatus status);
    
    /**
     * Find contacts by status
     */
    Page<NFCContact> findByStatusOrderByLastInteractionAtDesc(NFCContactStatus status, Pageable pageable);
    
    /**
     * Find favorite contacts for a user
     */
    List<NFCContact> findByUserIdAndIsFavoriteOrderByDisplayNameAsc(String userId, boolean isFavorite);
    
    /**
     * Find contacts by nickname or display name (search)
     */
    @Query("SELECT c FROM NFCContact c WHERE c.userId = :userId " +
           "AND (LOWER(c.displayName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(c.nickname) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY c.displayName")
    List<NFCContact> searchContacts(@Param("userId") String userId, 
                                   @Param("searchTerm") String searchTerm);
    
    /**
     * Find mutual contacts between two users
     */
    @Query("SELECT c1.contactUserId FROM NFCContact c1 " +
           "WHERE c1.userId = :userId1 AND c1.status = 'ACTIVE' " +
           "AND EXISTS (SELECT c2 FROM NFCContact c2 " +
           "WHERE c2.userId = :userId2 AND c2.contactUserId = c1.contactUserId " +
           "AND c2.status = 'ACTIVE')")
    List<String> findMutualContacts(@Param("userId1") String userId1, 
                                   @Param("userId2") String userId2);
    
    /**
     * Find contacts with recent interactions
     */
    @Query("SELECT c FROM NFCContact c WHERE c.userId = :userId " +
           "AND c.lastInteractionAt > :sinceTime " +
           "ORDER BY c.lastInteractionAt DESC")
    List<NFCContact> findRecentInteractions(@Param("userId") String userId, 
                                          @Param("sinceTime") Instant sinceTime,
                                          Pageable pageable);
    
    /**
     * Find contacts by interaction frequency
     */
    @Query("SELECT c FROM NFCContact c WHERE c.userId = :userId " +
           "AND c.interactionCount > :minInteractions " +
           "ORDER BY c.interactionCount DESC")
    List<NFCContact> findFrequentContacts(@Param("userId") String userId, 
                                         @Param("minInteractions") Long minInteractions,
                                         Pageable pageable);
    
    /**
     * Find contacts added via NFC
     */
    @Query("SELECT c FROM NFCContact c WHERE c.userId = :userId " +
           "AND c.addedViaNFC = true ORDER BY c.createdAt DESC")
    List<NFCContact> findNFCAddedContacts(@Param("userId") String userId);
    
    /**
     * Find contacts with pending invitations
     */
    @Query("SELECT c FROM NFCContact c WHERE c.contactUserId = :userId " +
           "AND c.status = 'PENDING' ORDER BY c.createdAt DESC")
    List<NFCContact> findPendingInvitations(@Param("userId") String userId);
    
    /**
     * Count contacts for a user
     */
    @Query("SELECT COUNT(c) FROM NFCContact c WHERE c.userId = :userId AND c.status = 'ACTIVE'")
    long countActiveContacts(@Param("userId") String userId);
    
    /**
     * Update last interaction
     */
    @Modifying
    @Transactional
    @Query("UPDATE NFCContact c SET c.lastInteractionAt = :timestamp, " +
           "c.interactionCount = c.interactionCount + 1, " +
           "c.lastTransactionAmount = :amount, " +
           "c.lastTransactionType = :transactionType " +
           "WHERE c.userId = :userId AND c.contactUserId = :contactUserId")
    int updateInteraction(@Param("userId") String userId, 
                         @Param("contactUserId") String contactUserId,
                         @Param("timestamp") Instant timestamp,
                         @Param("amount") java.math.BigDecimal amount,
                         @Param("transactionType") String transactionType);
    
    /**
     * Update contact status
     */
    @Modifying
    @Transactional
    @Query("UPDATE NFCContact c SET c.status = :status, c.updatedAt = :updatedAt " +
           "WHERE c.userId = :userId AND c.contactUserId = :contactUserId")
    int updateContactStatus(@Param("userId") String userId, 
                          @Param("contactUserId") String contactUserId,
                          @Param("status") NFCContactStatus status,
                          @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Toggle favorite status
     */
    @Modifying
    @Transactional
    @Query("UPDATE NFCContact c SET c.isFavorite = :isFavorite " +
           "WHERE c.userId = :userId AND c.contactUserId = :contactUserId")
    int toggleFavorite(@Param("userId") String userId, 
                      @Param("contactUserId") String contactUserId,
                      @Param("isFavorite") boolean isFavorite);
    
    /**
     * Update trust score
     */
    @Modifying
    @Transactional
    @Query("UPDATE NFCContact c SET c.trustScore = :trustScore " +
           "WHERE c.userId = :userId AND c.contactUserId = :contactUserId")
    int updateTrustScore(@Param("userId") String userId, 
                        @Param("contactUserId") String contactUserId,
                        @Param("trustScore") Double trustScore);
    
    /**
     * Get contact statistics
     */
    @Query("SELECT NEW com.waqiti.payment.dto.ContactStatistics(" +
           "COUNT(c), " +
           "SUM(CASE WHEN c.status = 'ACTIVE' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN c.status = 'BLOCKED' THEN 1 ELSE 0 END), " +
           "SUM(c.transactionCount), " +
           "SUM(c.totalTransactionAmount), " +
           "AVG(c.trustScore)) " +
           "FROM NFCContact c WHERE c.userId = :userId")
    Object getContactStatistics(@Param("userId") String userId);
    
    /**
     * Find contacts with transaction history
     */
    @Query("SELECT c FROM NFCContact c WHERE c.userId = :userId " +
           "AND c.transactionCount > 0 ORDER BY c.totalTransactionAmount DESC")
    List<NFCContact> findContactsWithTransactionHistory(@Param("userId") String userId, Pageable pageable);
    
    /**
     * Find blocked contacts
     */
    List<NFCContact> findByUserIdAndStatusOrderByUpdatedAtDesc(String userId, NFCContactStatus status, Pageable pageable);
    
    /**
     * Delete contact relationship
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM NFCContact c WHERE c.userId = :userId AND c.contactUserId = :contactUserId")
    int deleteContact(@Param("userId") String userId, @Param("contactUserId") String contactUserId);
    
    /**
     * Find contacts by proximity (for nearby NFC exchanges)
     */
    @Query("SELECT c FROM NFCContact c WHERE c.userId = :userId " +
           "AND c.lastKnownLatitude IS NOT NULL AND c.lastKnownLongitude IS NOT NULL " +
           "AND 6371 * acos(cos(radians(:lat)) * cos(radians(c.lastKnownLatitude)) * " +
           "cos(radians(c.lastKnownLongitude) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(c.lastKnownLatitude))) <= :radiusKm " +
           "ORDER BY c.lastInteractionAt DESC")
    List<NFCContact> findNearbyContacts(@Param("userId") String userId,
                                       @Param("lat") Double latitude,
                                       @Param("lng") Double longitude,
                                       @Param("radiusKm") Double radiusKm);
    
    /**
     * Get top transaction partners
     */
    @Query("SELECT c FROM NFCContact c WHERE c.userId = :userId " +
           "ORDER BY c.totalTransactionAmount DESC")
    List<NFCContact> findTopTransactionPartners(@Param("userId") String userId, Pageable pageable);
    
    /**
     * Clean up expired pending invitations
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM NFCContact c WHERE c.status = 'PENDING' " +
           "AND c.createdAt < :cutoffTime")
    int deleteExpiredInvitations(@Param("cutoffTime") LocalDateTime cutoffTime);
}