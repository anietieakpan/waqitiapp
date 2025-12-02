package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerIdentification;
import com.waqiti.customer.entity.CustomerIdentification.IdType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CustomerIdentification entity
 *
 * Provides data access methods for customer identification document management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerIdentificationRepository extends JpaRepository<CustomerIdentification, UUID> {

    /**
     * Find identification by identification ID
     *
     * @param identificationId the unique identification identifier
     * @return Optional containing the identification if found
     */
    Optional<CustomerIdentification> findByIdentificationId(String identificationId);

    /**
     * Find all identifications for a customer
     *
     * @param customerId the customer ID
     * @return list of identifications
     */
    List<CustomerIdentification> findByCustomerId(String customerId);

    /**
     * Find identifications by customer ID with pagination
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of identifications
     */
    Page<CustomerIdentification> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Find identifications by type for a customer
     *
     * @param customerId the customer ID
     * @param idType the identification type
     * @return list of identifications
     */
    List<CustomerIdentification> findByCustomerIdAndIdType(String customerId, IdType idType);

    /**
     * Find verified identifications for a customer
     *
     * @param customerId the customer ID
     * @return list of verified identifications
     */
    @Query("SELECT i FROM CustomerIdentification i WHERE i.customerId = :customerId AND i.isVerified = true")
    List<CustomerIdentification> findVerifiedIdentifications(@Param("customerId") String customerId);

    /**
     * Find unverified identifications for a customer
     *
     * @param customerId the customer ID
     * @return list of unverified identifications
     */
    @Query("SELECT i FROM CustomerIdentification i WHERE i.customerId = :customerId AND i.isVerified = false")
    List<CustomerIdentification> findUnverifiedIdentifications(@Param("customerId") String customerId);

    /**
     * Find expired identifications for a customer
     *
     * @param customerId the customer ID
     * @return list of expired identifications
     */
    @Query("SELECT i FROM CustomerIdentification i WHERE i.customerId = :customerId AND i.isExpired = true")
    List<CustomerIdentification> findExpiredIdentifications(@Param("customerId") String customerId);

    /**
     * Find all expired identifications
     *
     * @param pageable pagination information
     * @return page of expired identifications
     */
    @Query("SELECT i FROM CustomerIdentification i WHERE i.isExpired = true")
    Page<CustomerIdentification> findAllExpiredIdentifications(Pageable pageable);

    /**
     * Find identifications expiring soon (within specified days)
     *
     * @param currentDate the current date
     * @param expiryThreshold the expiry threshold date
     * @param pageable pagination information
     * @return page of identifications expiring soon
     */
    @Query("SELECT i FROM CustomerIdentification i WHERE " +
           "i.expiryDate IS NOT NULL AND " +
           "i.expiryDate > :currentDate AND " +
           "i.expiryDate <= :expiryThreshold AND " +
           "i.isExpired = false")
    Page<CustomerIdentification> findExpiringSoon(
        @Param("currentDate") LocalDate currentDate,
        @Param("expiryThreshold") LocalDate expiryThreshold,
        Pageable pageable
    );

    /**
     * Find identifications expiring within days for a customer
     *
     * @param customerId the customer ID
     * @param currentDate the current date
     * @param expiryThreshold the expiry threshold date
     * @return list of identifications expiring soon
     */
    @Query("SELECT i FROM CustomerIdentification i WHERE " +
           "i.customerId = :customerId AND " +
           "i.expiryDate IS NOT NULL AND " +
           "i.expiryDate > :currentDate AND " +
           "i.expiryDate <= :expiryThreshold AND " +
           "i.isExpired = false")
    List<CustomerIdentification> findExpiringSoonForCustomer(
        @Param("customerId") String customerId,
        @Param("currentDate") LocalDate currentDate,
        @Param("expiryThreshold") LocalDate expiryThreshold
    );

    /**
     * Find valid (verified and not expired) identifications for a customer
     *
     * @param customerId the customer ID
     * @return list of valid identifications
     */
    @Query("SELECT i FROM CustomerIdentification i WHERE " +
           "i.customerId = :customerId AND " +
           "i.isVerified = true AND " +
           "i.isExpired = false")
    List<CustomerIdentification> findValidIdentifications(@Param("customerId") String customerId);

    /**
     * Find identification by ID number hash
     *
     * @param idNumberHash the ID number hash
     * @return Optional containing the identification if found
     */
    Optional<CustomerIdentification> findByIdNumberHash(String idNumberHash);

    /**
     * Find identifications by issuing country
     *
     * @param issuingCountry the issuing country code
     * @param pageable pagination information
     * @return page of identifications
     */
    Page<CustomerIdentification> findByIssuingCountry(String issuingCountry, Pageable pageable);

    /**
     * Find identifications by type
     *
     * @param idType the identification type
     * @param pageable pagination information
     * @return page of identifications
     */
    Page<CustomerIdentification> findByIdType(IdType idType, Pageable pageable);

    /**
     * Find identifications issued within date range
     *
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of identifications
     */
    @Query("SELECT i FROM CustomerIdentification i WHERE i.issueDate BETWEEN :startDate AND :endDate")
    Page<CustomerIdentification> findByIssueDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );

    /**
     * Find identifications expiring within date range
     *
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of identifications
     */
    @Query("SELECT i FROM CustomerIdentification i WHERE i.expiryDate BETWEEN :startDate AND :endDate")
    Page<CustomerIdentification> findByExpiryDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );

    /**
     * Count identifications by customer ID
     *
     * @param customerId the customer ID
     * @return count of identifications
     */
    long countByCustomerId(String customerId);

    /**
     * Count verified identifications by customer ID
     *
     * @param customerId the customer ID
     * @return count of verified identifications
     */
    @Query("SELECT COUNT(i) FROM CustomerIdentification i WHERE i.customerId = :customerId AND i.isVerified = true")
    long countVerifiedByCustomerId(@Param("customerId") String customerId);

    /**
     * Count expired identifications by customer ID
     *
     * @param customerId the customer ID
     * @return count of expired identifications
     */
    @Query("SELECT COUNT(i) FROM CustomerIdentification i WHERE i.customerId = :customerId AND i.isExpired = true")
    long countExpiredByCustomerId(@Param("customerId") String customerId);

    /**
     * Count identifications by type
     *
     * @param idType the identification type
     * @return count of identifications
     */
    long countByIdType(IdType idType);

    /**
     * Check if customer has verified identification of type
     *
     * @param customerId the customer ID
     * @param idType the identification type
     * @return true if customer has verified identification of the specified type
     */
    @Query("SELECT COUNT(i) > 0 FROM CustomerIdentification i WHERE " +
           "i.customerId = :customerId AND i.idType = :idType AND i.isVerified = true AND i.isExpired = false")
    boolean hasVerifiedIdentificationOfType(@Param("customerId") String customerId, @Param("idType") IdType idType);
}
