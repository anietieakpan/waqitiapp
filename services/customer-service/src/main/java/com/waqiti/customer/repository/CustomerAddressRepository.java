package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerAddress;
import com.waqiti.customer.entity.CustomerAddress.AddressType;
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
 * Repository interface for CustomerAddress entity
 *
 * Provides data access methods for customer address management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    /**
     * Find address by address ID
     *
     * @param addressId the unique address identifier
     * @return Optional containing the address if found
     */
    Optional<CustomerAddress> findByAddressId(String addressId);

    /**
     * Find all addresses for a customer
     *
     * @param customerId the customer ID
     * @return list of addresses
     */
    List<CustomerAddress> findByCustomerId(String customerId);

    /**
     * Find addresses by customer ID with pagination
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of addresses
     */
    Page<CustomerAddress> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Find primary address for a customer
     *
     * @param customerId the customer ID
     * @return Optional containing the primary address if found
     */
    @Query("SELECT a FROM CustomerAddress a WHERE a.customerId = :customerId AND a.isPrimary = true")
    Optional<CustomerAddress> findPrimaryAddress(@Param("customerId") String customerId);

    /**
     * Find primary address by type for a customer
     *
     * @param customerId the customer ID
     * @param addressType the address type
     * @return Optional containing the primary address of the specified type
     */
    @Query("SELECT a FROM CustomerAddress a WHERE a.customerId = :customerId AND a.addressType = :type AND a.isPrimary = true")
    Optional<CustomerAddress> findPrimaryAddressByType(
        @Param("customerId") String customerId,
        @Param("type") AddressType addressType
    );

    /**
     * Find verified addresses for a customer
     *
     * @param customerId the customer ID
     * @return list of verified addresses
     */
    @Query("SELECT a FROM CustomerAddress a WHERE a.customerId = :customerId AND a.isVerified = true")
    List<CustomerAddress> findVerifiedAddresses(@Param("customerId") String customerId);

    /**
     * Find unverified addresses for a customer
     *
     * @param customerId the customer ID
     * @return list of unverified addresses
     */
    @Query("SELECT a FROM CustomerAddress a WHERE a.customerId = :customerId AND a.isVerified = false")
    List<CustomerAddress> findUnverifiedAddresses(@Param("customerId") String customerId);

    /**
     * Find addresses by type for a customer
     *
     * @param customerId the customer ID
     * @param addressType the address type
     * @return list of addresses
     */
    List<CustomerAddress> findByCustomerIdAndAddressType(String customerId, AddressType addressType);

    /**
     * Find addresses by country code
     *
     * @param countryCode the country code
     * @param pageable pagination information
     * @return page of addresses
     */
    Page<CustomerAddress> findByCountryCode(String countryCode, Pageable pageable);

    /**
     * Find addresses by country code for a customer
     *
     * @param customerId the customer ID
     * @param countryCode the country code
     * @return list of addresses
     */
    List<CustomerAddress> findByCustomerIdAndCountryCode(String customerId, String countryCode);

    /**
     * Find addresses by city
     *
     * @param city the city name
     * @param pageable pagination information
     * @return page of addresses
     */
    Page<CustomerAddress> findByCity(String city, Pageable pageable);

    /**
     * Find addresses by state
     *
     * @param state the state name
     * @param pageable pagination information
     * @return page of addresses
     */
    Page<CustomerAddress> findByState(String state, Pageable pageable);

    /**
     * Find addresses by postal code
     *
     * @param postalCode the postal code
     * @param pageable pagination information
     * @return page of addresses
     */
    Page<CustomerAddress> findByPostalCode(String postalCode, Pageable pageable);

    /**
     * Find currently valid addresses for a customer
     *
     * @param customerId the customer ID
     * @param currentDate the current date
     * @return list of valid addresses
     */
    @Query("SELECT a FROM CustomerAddress a WHERE a.customerId = :customerId " +
           "AND a.validFrom <= :currentDate " +
           "AND (a.validTo IS NULL OR a.validTo >= :currentDate)")
    List<CustomerAddress> findValidAddresses(
        @Param("customerId") String customerId,
        @Param("currentDate") LocalDate currentDate
    );

    /**
     * Find expired addresses for a customer
     *
     * @param customerId the customer ID
     * @param currentDate the current date
     * @return list of expired addresses
     */
    @Query("SELECT a FROM CustomerAddress a WHERE a.customerId = :customerId " +
           "AND a.validTo IS NOT NULL AND a.validTo < :currentDate")
    List<CustomerAddress> findExpiredAddresses(
        @Param("customerId") String customerId,
        @Param("currentDate") LocalDate currentDate
    );

    /**
     * Find addresses with coordinates
     *
     * @param pageable pagination information
     * @return page of addresses with coordinates
     */
    @Query("SELECT a FROM CustomerAddress a WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL")
    Page<CustomerAddress> findAddressesWithCoordinates(Pageable pageable);

    /**
     * Find addresses within a bounding box (geographical search)
     *
     * @param minLat minimum latitude
     * @param maxLat maximum latitude
     * @param minLon minimum longitude
     * @param maxLon maximum longitude
     * @param pageable pagination information
     * @return page of addresses within the bounding box
     */
    @Query("SELECT a FROM CustomerAddress a WHERE " +
           "a.latitude BETWEEN :minLat AND :maxLat AND " +
           "a.longitude BETWEEN :minLon AND :maxLon")
    Page<CustomerAddress> findAddressesInBoundingBox(
        @Param("minLat") java.math.BigDecimal minLat,
        @Param("maxLat") java.math.BigDecimal maxLat,
        @Param("minLon") java.math.BigDecimal minLon,
        @Param("maxLon") java.math.BigDecimal maxLon,
        Pageable pageable
    );

    /**
     * Count addresses by customer ID
     *
     * @param customerId the customer ID
     * @return count of addresses
     */
    long countByCustomerId(String customerId);

    /**
     * Count verified addresses by customer ID
     *
     * @param customerId the customer ID
     * @return count of verified addresses
     */
    @Query("SELECT COUNT(a) FROM CustomerAddress a WHERE a.customerId = :customerId AND a.isVerified = true")
    long countVerifiedByCustomerId(@Param("customerId") String customerId);

    /**
     * Count addresses by country code
     *
     * @param countryCode the country code
     * @return count of addresses
     */
    long countByCountryCode(String countryCode);

    /**
     * Find all addresses by address type
     *
     * @param addressType the address type
     * @param pageable pagination information
     * @return page of addresses
     */
    Page<CustomerAddress> findByAddressType(AddressType addressType, Pageable pageable);
}
