package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerRelationship;
import com.waqiti.customer.entity.CustomerRelationship.RelationshipStatus;
import com.waqiti.customer.entity.CustomerRelationship.RelationshipType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CustomerRelationship entity
 *
 * Provides data access methods for customer relationship management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerRelationshipRepository extends JpaRepository<CustomerRelationship, UUID> {

    /**
     * Find relationship by relationship ID
     *
     * @param relationshipId the unique relationship identifier
     * @return Optional containing the relationship if found
     */
    Optional<CustomerRelationship> findByRelationshipId(String relationshipId);

    /**
     * Find all relationships for a customer
     *
     * @param customerId the customer ID
     * @return list of relationships
     */
    List<CustomerRelationship> findByCustomerId(String customerId);

    /**
     * Find relationships by customer ID with pagination
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of relationships
     */
    Page<CustomerRelationship> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Find all relationships where customer is the related party
     *
     * @param relatedCustomerId the related customer ID
     * @return list of relationships
     */
    List<CustomerRelationship> findByRelatedCustomerId(String relatedCustomerId);

    /**
     * Find relationships by type for a customer
     *
     * @param customerId the customer ID
     * @param relationshipType the relationship type
     * @return list of relationships
     */
    List<CustomerRelationship> findByCustomerIdAndRelationshipType(String customerId, RelationshipType relationshipType);

    /**
     * Find relationships by status for a customer
     *
     * @param customerId the customer ID
     * @param status the relationship status
     * @return list of relationships
     */
    List<CustomerRelationship> findByCustomerIdAndRelationshipStatus(String customerId, RelationshipStatus status);

    /**
     * Find active relationships for a customer
     *
     * @param customerId the customer ID
     * @return list of active relationships
     */
    @Query("SELECT r FROM CustomerRelationship r WHERE r.customerId = :customerId AND r.relationshipStatus = 'ACTIVE'")
    List<CustomerRelationship> findActiveRelationships(@Param("customerId") String customerId);

    /**
     * Find currently valid relationships for a customer
     *
     * @param customerId the customer ID
     * @param currentDate the current date
     * @return list of valid relationships
     */
    @Query("SELECT r FROM CustomerRelationship r WHERE r.customerId = :customerId " +
           "AND r.relationshipStatus = 'ACTIVE' " +
           "AND r.validFrom <= :currentDate " +
           "AND (r.validTo IS NULL OR r.validTo >= :currentDate)")
    List<CustomerRelationship> findValidRelationships(
        @Param("customerId") String customerId,
        @Param("currentDate") LocalDate currentDate
    );

    /**
     * Find beneficial owners for a customer (ownership >= 25%)
     *
     * @param customerId the customer ID
     * @param threshold the ownership threshold (default 0.25 for 25%)
     * @return list of beneficial owners
     */
    @Query("SELECT r FROM CustomerRelationship r WHERE r.customerId = :customerId " +
           "AND r.relationshipStatus = 'ACTIVE' " +
           "AND r.ownershipPercentage >= :threshold")
    List<CustomerRelationship> findBeneficialOwners(
        @Param("customerId") String customerId,
        @Param("threshold") BigDecimal threshold
    );

    /**
     * Find authorized signers for a customer
     *
     * @param customerId the customer ID
     * @return list of authorized signers
     */
    @Query("SELECT r FROM CustomerRelationship r WHERE r.customerId = :customerId " +
           "AND r.relationshipStatus = 'ACTIVE' " +
           "AND r.relationshipType IN ('AUTHORIZED_SIGNER', 'POWER_OF_ATTORNEY')")
    List<CustomerRelationship> findAuthorizedSigners(@Param("customerId") String customerId);

    /**
     * Find shareholders for a business customer
     *
     * @param customerId the customer ID (business)
     * @return list of shareholders
     */
    @Query("SELECT r FROM CustomerRelationship r WHERE r.customerId = :customerId " +
           "AND r.relationshipStatus = 'ACTIVE' " +
           "AND r.relationshipType = 'SHAREHOLDER'")
    List<CustomerRelationship> findShareholders(@Param("customerId") String customerId);

    /**
     * Find directors for a business customer
     *
     * @param customerId the customer ID (business)
     * @return list of directors
     */
    @Query("SELECT r FROM CustomerRelationship r WHERE r.customerId = :customerId " +
           "AND r.relationshipStatus = 'ACTIVE' " +
           "AND r.relationshipType = 'DIRECTOR'")
    List<CustomerRelationship> findDirectors(@Param("customerId") String customerId);

    /**
     * Find officers for a business customer
     *
     * @param customerId the customer ID (business)
     * @return list of officers
     */
    @Query("SELECT r FROM CustomerRelationship r WHERE r.customerId = :customerId " +
           "AND r.relationshipStatus = 'ACTIVE' " +
           "AND r.relationshipType = 'OFFICER'")
    List<CustomerRelationship> findOfficers(@Param("customerId") String customerId);

    /**
     * Find family relationships for a customer
     *
     * @param customerId the customer ID
     * @return list of family relationships
     */
    @Query("SELECT r FROM CustomerRelationship r WHERE r.customerId = :customerId " +
           "AND r.relationshipStatus = 'ACTIVE' " +
           "AND r.relationshipType IN ('FAMILY_MEMBER', 'SPOUSE', 'PARENT', 'CHILD', 'SIBLING')")
    List<CustomerRelationship> findFamilyRelationships(@Param("customerId") String customerId);

    /**
     * Find relationship between two customers
     *
     * @param customerId the customer ID
     * @param relatedCustomerId the related customer ID
     * @return Optional containing the relationship if found
     */
    @Query("SELECT r FROM CustomerRelationship r WHERE " +
           "r.customerId = :customerId AND r.relatedCustomerId = :relatedCustomerId")
    Optional<CustomerRelationship> findRelationshipBetween(
        @Param("customerId") String customerId,
        @Param("relatedCustomerId") String relatedCustomerId
    );

    /**
     * Find bidirectional relationship between two customers
     *
     * @param customerId1 the first customer ID
     * @param customerId2 the second customer ID
     * @return list of relationships (0, 1, or 2 results)
     */
    @Query("SELECT r FROM CustomerRelationship r WHERE " +
           "(r.customerId = :customerId1 AND r.relatedCustomerId = :customerId2) OR " +
           "(r.customerId = :customerId2 AND r.relatedCustomerId = :customerId1)")
    List<CustomerRelationship> findBidirectionalRelationship(
        @Param("customerId1") String customerId1,
        @Param("customerId2") String customerId2
    );

    /**
     * Find expired relationships
     *
     * @param currentDate the current date
     * @param pageable pagination information
     * @return page of expired relationships
     */
    @Query("SELECT r FROM CustomerRelationship r WHERE " +
           "r.validTo IS NOT NULL AND r.validTo < :currentDate")
    Page<CustomerRelationship> findExpiredRelationships(
        @Param("currentDate") LocalDate currentDate,
        Pageable pageable
    );

    /**
     * Find relationships expiring soon
     *
     * @param currentDate the current date
     * @param expiryThreshold the expiry threshold date
     * @param pageable pagination information
     * @return page of relationships expiring soon
     */
    @Query("SELECT r FROM CustomerRelationship r WHERE " +
           "r.validTo IS NOT NULL AND " +
           "r.validTo > :currentDate AND " +
           "r.validTo <= :expiryThreshold AND " +
           "r.relationshipStatus = 'ACTIVE'")
    Page<CustomerRelationship> findExpiringSoon(
        @Param("currentDate") LocalDate currentDate,
        @Param("expiryThreshold") LocalDate expiryThreshold,
        Pageable pageable
    );

    /**
     * Find all relationships by type
     *
     * @param relationshipType the relationship type
     * @param pageable pagination information
     * @return page of relationships
     */
    Page<CustomerRelationship> findByRelationshipType(RelationshipType relationshipType, Pageable pageable);

    /**
     * Find all relationships by status
     *
     * @param status the relationship status
     * @param pageable pagination information
     * @return page of relationships
     */
    Page<CustomerRelationship> findByRelationshipStatus(RelationshipStatus status, Pageable pageable);

    /**
     * Count relationships by customer ID
     *
     * @param customerId the customer ID
     * @return count of relationships
     */
    long countByCustomerId(String customerId);

    /**
     * Count active relationships by customer ID
     *
     * @param customerId the customer ID
     * @return count of active relationships
     */
    @Query("SELECT COUNT(r) FROM CustomerRelationship r WHERE r.customerId = :customerId AND r.relationshipStatus = 'ACTIVE'")
    long countActiveByCustomerId(@Param("customerId") String customerId);

    /**
     * Count relationships by type
     *
     * @param relationshipType the relationship type
     * @return count of relationships
     */
    long countByRelationshipType(RelationshipType relationshipType);

    /**
     * Get total ownership percentage for a customer
     *
     * @param customerId the customer ID
     * @return total ownership percentage
     */
    @Query("SELECT COALESCE(SUM(r.ownershipPercentage), 0) FROM CustomerRelationship r " +
           "WHERE r.customerId = :customerId AND r.relationshipStatus = 'ACTIVE'")
    BigDecimal getTotalOwnership(@Param("customerId") String customerId);
}
