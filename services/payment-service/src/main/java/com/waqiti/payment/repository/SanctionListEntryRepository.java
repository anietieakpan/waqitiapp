package com.waqiti.payment.repository;

import com.waqiti.payment.entity.SanctionListEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SanctionListEntryRepository extends JpaRepository<SanctionListEntry, UUID> {
    
    Optional<SanctionListEntry> findByEntityId(String entityId);
    
    List<SanctionListEntry> findByStatus(String status);
    
    List<SanctionListEntry> findByListType(String listType);
    
    List<SanctionListEntry> findByCountryCode(String countryCode);
    
    @Query("SELECT s FROM SanctionListEntry s WHERE s.status = 'ACTIVE'")
    List<SanctionListEntry> findAllActiveEntries();
    
    @Query("SELECT s FROM SanctionListEntry s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%')) AND s.status = 'ACTIVE'")
    List<SanctionListEntry> searchByName(@Param("name") String name);
    
    @Query("SELECT s FROM SanctionListEntry s WHERE (LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(s.aliases) LIKE LOWER(CONCAT('%', :query, '%'))) AND s.status = 'ACTIVE'")
    List<SanctionListEntry> searchByNameOrAlias(@Param("query") String query);
    
    @Query("SELECT s FROM SanctionListEntry s WHERE s.entityType = :entityType AND s.status = 'ACTIVE'")
    List<SanctionListEntry> findByEntityType(@Param("entityType") String entityType);
    
    @Query("SELECT s FROM SanctionListEntry s WHERE s.listType = :listType AND s.countryCode = :countryCode AND s.status = 'ACTIVE'")
    List<SanctionListEntry> findByListTypeAndCountry(@Param("listType") String listType, @Param("countryCode") String countryCode);
    
    @Query("SELECT COUNT(s) FROM SanctionListEntry s WHERE s.status = 'ACTIVE'")
    long countActiveEntries();
    
    @Query("SELECT COUNT(s) FROM SanctionListEntry s WHERE s.listType = :listType AND s.status = 'ACTIVE'")
    long countByListType(@Param("listType") String listType);
}