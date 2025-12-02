package com.waqiti.support.repository;

import com.waqiti.support.domain.FAQ;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FAQRepository extends JpaRepository<FAQ, String> {
    
    // Find published FAQs
    Page<FAQ> findByPublishedTrueOrderByOrderIndexAsc(Pageable pageable);
    
    // Find by category
    Page<FAQ> findByCategoryAndPublishedTrueOrderByOrderIndexAsc(
        FAQ.FAQCategory category, 
        Pageable pageable
    );
    
    // Find featured FAQs
    List<FAQ> findByFeaturedTrueAndPublishedTrueOrderByOrderIndexAsc();
    
    // Search FAQs
    @Query("SELECT f FROM FAQ f WHERE f.published = true AND " +
           "(LOWER(f.question) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(f.answer) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<FAQ> searchFAQs(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    // Find FAQs by tag
    @Query("SELECT DISTINCT f FROM FAQ f JOIN f.tags t WHERE " +
           "f.published = true AND LOWER(t) = LOWER(:tag)")
    Page<FAQ> findByTag(@Param("tag") String tag, Pageable pageable);
    
    // Most viewed FAQs
    List<FAQ> findTop10ByPublishedTrueOrderByViewCountDesc();
    
    // Most helpful FAQs
    @Query("SELECT f FROM FAQ f WHERE f.published = true AND " +
           "(f.helpfulCount + f.notHelpfulCount) > 10 " +
           "ORDER BY (CAST(f.helpfulCount AS double) / (f.helpfulCount + f.notHelpfulCount)) DESC")
    List<FAQ> findMostHelpfulFAQs(Pageable pageable);
    
    // Increment view count
    @Modifying
    @Query("UPDATE FAQ f SET f.viewCount = f.viewCount + 1 WHERE f.id = :id")
    void incrementViewCount(@Param("id") String id);
    
    // Increment helpful count
    @Modifying
    @Query("UPDATE FAQ f SET f.helpfulCount = f.helpfulCount + 1 WHERE f.id = :id")
    void incrementHelpfulCount(@Param("id") String id);
    
    // Increment not helpful count
    @Modifying
    @Query("UPDATE FAQ f SET f.notHelpfulCount = f.notHelpfulCount + 1 WHERE f.id = :id")
    void incrementNotHelpfulCount(@Param("id") String id);
    
    // Count by category
    @Query("SELECT f.category, COUNT(f) FROM FAQ f WHERE f.published = true " +
           "GROUP BY f.category")
    List<Object[]> countByCategory();
    
    // Find by category count
    long countByCategoryAndPublishedTrue(FAQ.FAQCategory category);
    
    // Get next order index for category
    @Query("SELECT COALESCE(MAX(f.orderIndex), 0) + 1 FROM FAQ f WHERE f.category = :category")
    Integer getNextOrderIndex(@Param("category") FAQ.FAQCategory category);
}