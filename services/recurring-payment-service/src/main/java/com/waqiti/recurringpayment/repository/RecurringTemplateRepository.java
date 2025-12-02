package com.waqiti.recurringpayment.repository;

import com.waqiti.recurringpayment.domain.RecurringTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecurringTemplateRepository extends JpaRepository<RecurringTemplate, String> {
    
    Optional<RecurringTemplate> findByIdAndUserId(String id, String userId);
    
    Page<RecurringTemplate> findByUserId(String userId, Pageable pageable);
    
    Page<RecurringTemplate> findByUserIdAndCategory(String userId, String category, Pageable pageable);
    
    List<RecurringTemplate> findByUserIdAndFavoriteTrue(String userId);
    
    @Query("SELECT t FROM RecurringTemplate t WHERE t.userId = :userId " +
           "ORDER BY t.usageCount DESC, t.lastUsedAt DESC")
    List<RecurringTemplate> findMostUsedByUser(@Param("userId") String userId, Pageable pageable);
    
    @Query("SELECT DISTINCT t.category FROM RecurringTemplate t WHERE t.userId = :userId " +
           "AND t.category IS NOT NULL")
    List<String> findDistinctCategoriesByUser(@Param("userId") String userId);
    
    @Query("SELECT t FROM RecurringTemplate t WHERE t.userId = :userId " +
           "AND (LOWER(t.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<RecurringTemplate> searchByNameOrDescription(@Param("userId") String userId, 
                                                     @Param("searchTerm") String searchTerm);
    
    @Modifying
    @Query("UPDATE RecurringTemplate t SET t.usageCount = t.usageCount + 1, " +
           "t.lastUsedAt = :now WHERE t.id = :id")
    void incrementUsage(@Param("id") String id, @Param("now") Instant now);
    
    @Modifying
    @Query("UPDATE RecurringTemplate t SET t.favorite = :favorite WHERE t.id = :id " +
           "AND t.userId = :userId")
    void updateFavoriteStatus(@Param("id") String id, 
                             @Param("userId") String userId, 
                             @Param("favorite") boolean favorite);
    
    long countByUserId(String userId);
    
    @Query("SELECT COUNT(t) FROM RecurringTemplate t WHERE t.userId = :userId " +
           "AND t.category = :category")
    long countByUserIdAndCategory(@Param("userId") String userId, 
                                 @Param("category") String category);
}