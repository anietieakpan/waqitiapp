package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.SanctionedAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing sanctioned cryptocurrency addresses
 */
@Repository
public interface SanctionedAddressRepository extends JpaRepository<SanctionedAddress, Long> {
    
    /**
     * Find a sanctioned address by address string
     */
    Optional<SanctionedAddress> findByAddress(String address);
    
    /**
     * Find all addresses by sanctions list
     */
    List<SanctionedAddress> findBySanctionsList(String sanctionsList);
    
    /**
     * Find all active sanctioned addresses
     */
    List<SanctionedAddress> findByActiveTrue();
    
    /**
     * Check if an address exists and is active
     */
    boolean existsByAddressAndActiveTrue(String address);
    
    /**
     * Find addresses by jurisdiction
     */
    List<SanctionedAddress> findByJurisdictionAndActiveTrue(String jurisdiction);
    
    /**
     * Find recently added sanctioned addresses
     */
    @Query("SELECT s FROM SanctionedAddress s WHERE s.createdAt >= CURRENT_TIMESTAMP - INTERVAL '7' DAY ORDER BY s.createdAt DESC")
    List<SanctionedAddress> findRecentlyAdded();
    
    /**
     * Find addresses by multiple sanctions lists
     */
    @Query("SELECT s FROM SanctionedAddress s WHERE s.sanctionsList IN :lists AND s.active = true")
    List<SanctionedAddress> findBySanctionsLists(@Param("lists") List<String> lists);
}