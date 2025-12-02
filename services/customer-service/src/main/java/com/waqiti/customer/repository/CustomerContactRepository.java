package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerContact;
import com.waqiti.customer.entity.CustomerContact.ContactType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CustomerContact entity
 *
 * Provides data access methods for customer contact information management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerContactRepository extends JpaRepository<CustomerContact, UUID> {

    /**
     * Find contact by contact ID
     *
     * @param contactId the unique contact identifier
     * @return Optional containing the contact if found
     */
    Optional<CustomerContact> findByContactId(String contactId);

    /**
     * Find all contacts for a customer
     *
     * @param customerId the customer ID
     * @return list of contacts
     */
    List<CustomerContact> findByCustomerId(String customerId);

    /**
     * Find contacts by customer ID with pagination
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of contacts
     */
    Page<CustomerContact> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Find primary contact for a customer
     *
     * @param customerId the customer ID
     * @return Optional containing the primary contact if found
     */
    @Query("SELECT c FROM CustomerContact c WHERE c.customerId = :customerId AND c.isPrimary = true")
    Optional<CustomerContact> findPrimaryContact(@Param("customerId") String customerId);

    /**
     * Find primary contacts by type for a customer
     *
     * @param customerId the customer ID
     * @param contactType the contact type
     * @return Optional containing the primary contact of the specified type
     */
    @Query("SELECT c FROM CustomerContact c WHERE c.customerId = :customerId AND c.contactType = :type AND c.isPrimary = true")
    Optional<CustomerContact> findPrimaryContactByType(
        @Param("customerId") String customerId,
        @Param("type") ContactType contactType
    );

    /**
     * Find contact by email
     *
     * @param email the email address
     * @return Optional containing the contact if found
     */
    Optional<CustomerContact> findByEmail(String email);

    /**
     * Find contact by phone number
     *
     * @param phoneNumber the phone number
     * @return Optional containing the contact if found
     */
    Optional<CustomerContact> findByPhoneNumber(String phoneNumber);

    /**
     * Find verified contacts for a customer
     *
     * @param customerId the customer ID
     * @return list of verified contacts
     */
    @Query("SELECT c FROM CustomerContact c WHERE c.customerId = :customerId AND c.isVerified = true")
    List<CustomerContact> findVerifiedContacts(@Param("customerId") String customerId);

    /**
     * Find unverified contacts for a customer
     *
     * @param customerId the customer ID
     * @return list of unverified contacts
     */
    @Query("SELECT c FROM CustomerContact c WHERE c.customerId = :customerId AND c.isVerified = false")
    List<CustomerContact> findUnverifiedContacts(@Param("customerId") String customerId);

    /**
     * Find contacts by type for a customer
     *
     * @param customerId the customer ID
     * @param contactType the contact type
     * @return list of contacts
     */
    List<CustomerContact> findByCustomerIdAndContactType(String customerId, ContactType contactType);

    /**
     * Find contacts with marketing enabled for a customer
     *
     * @param customerId the customer ID
     * @return list of contacts
     */
    @Query("SELECT c FROM CustomerContact c WHERE c.customerId = :customerId AND c.isMarketingEnabled = true")
    List<CustomerContact> findMarketingEnabledContacts(@Param("customerId") String customerId);

    /**
     * Find all email contacts with marketing enabled
     *
     * @param pageable pagination information
     * @return page of contacts
     */
    @Query("SELECT c FROM CustomerContact c WHERE c.contactType = 'EMAIL' AND c.isMarketingEnabled = true AND c.isVerified = true")
    Page<CustomerContact> findMarketingEmailContacts(Pageable pageable);

    /**
     * Find all mobile contacts with marketing enabled
     *
     * @param pageable pagination information
     * @return page of contacts
     */
    @Query("SELECT c FROM CustomerContact c WHERE c.contactType = 'MOBILE' AND c.isMarketingEnabled = true AND c.isVerified = true")
    Page<CustomerContact> findMarketingSmsContacts(Pageable pageable);

    /**
     * Check if email exists
     *
     * @param email the email address
     * @return true if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Check if phone number exists
     *
     * @param phoneNumber the phone number
     * @return true if phone number exists
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Count contacts by customer ID
     *
     * @param customerId the customer ID
     * @return count of contacts
     */
    long countByCustomerId(String customerId);

    /**
     * Count verified contacts by customer ID
     *
     * @param customerId the customer ID
     * @return count of verified contacts
     */
    @Query("SELECT COUNT(c) FROM CustomerContact c WHERE c.customerId = :customerId AND c.isVerified = true")
    long countVerifiedByCustomerId(@Param("customerId") String customerId);

    /**
     * Find all contacts by contact type
     *
     * @param contactType the contact type
     * @param pageable pagination information
     * @return page of contacts
     */
    Page<CustomerContact> findByContactType(ContactType contactType, Pageable pageable);

    /**
     * Find contacts by country code
     *
     * @param countryCode the country code
     * @param pageable pagination information
     * @return page of contacts
     */
    Page<CustomerContact> findByCountryCode(String countryCode, Pageable pageable);
}
