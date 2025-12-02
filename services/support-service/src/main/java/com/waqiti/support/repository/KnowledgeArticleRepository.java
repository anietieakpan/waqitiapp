package com.waqiti.support.repository;

import com.waqiti.support.domain.KnowledgeArticle;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, String> {
    
    /**
     * Find article by slug
     */
    Optional<KnowledgeArticle> findBySlug(String slug);
    
    /**
     * Check if article exists by slug
     */
    boolean existsBySlug(String slug);
    
    /**
     * Find articles by content containing text (case insensitive)
     */
    @Query("SELECT ka FROM KnowledgeArticle ka WHERE LOWER(ka.content) LIKE LOWER(CONCAT('%', :query, '%')) AND ka.isPublished = true ORDER BY ka.createdAt DESC")
    List<KnowledgeArticle> findByContentContainingIgnoreCase(@Param("query") String query, Pageable pageable);
    
    /**
     * Find articles by title or summary containing text
     */
    @Query("SELECT ka FROM KnowledgeArticle ka WHERE (LOWER(ka.title) LIKE LOWER(CONCAT('%', :title, '%')) OR LOWER(ka.summary) LIKE LOWER(CONCAT('%', :summary, '%'))) AND ka.isPublished = true ORDER BY ka.createdAt DESC")
    List<KnowledgeArticle> findByTitleContainingOrSummaryContainingIgnoreCase(@Param("title") String title, @Param("summary") String summary, Pageable pageable);
    
    /**
     * Find articles by title containing text
     */
    @Query("SELECT ka FROM KnowledgeArticle ka WHERE LOWER(ka.title) LIKE LOWER(CONCAT('%', :title, '%')) AND ka.isPublished = true ORDER BY ka.createdAt DESC")
    List<KnowledgeArticle> findByTitleContainingIgnoreCase(@Param("title") String title, Pageable pageable);
    
    /**
     * Find articles by category
     */
    @Query("SELECT ka FROM KnowledgeArticle ka WHERE LOWER(ka.category) LIKE LOWER(CONCAT('%', :category, '%')) AND ka.isPublished = true ORDER BY ka.createdAt DESC")
    List<KnowledgeArticle> findByCategoryIgnoreCase(@Param("category") String category, Pageable pageable);
    
    /**
     * Find top articles by view count
     */
    @Query("SELECT ka FROM KnowledgeArticle ka WHERE ka.isPublished = true ORDER BY ka.viewCount DESC")
    List<KnowledgeArticle> findTopByOrderByViewCountDesc(Pageable pageable);
    
    /**
     * Find recent articles
     */
    @Query("SELECT ka FROM KnowledgeArticle ka WHERE ka.isPublished = true ORDER BY ka.createdAt DESC")
    List<KnowledgeArticle> findTopByOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * Find featured articles
     */
    @Query("SELECT ka FROM KnowledgeArticle ka WHERE ka.isFeatured = true AND ka.isPublished = true ORDER BY ka.createdAt DESC")
    List<KnowledgeArticle> findByIsFeaturedTrueOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find articles by tags
     */
    // SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
    @Query("SELECT ka FROM KnowledgeArticle ka WHERE ka.tags LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:tag, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%') AND ka.isPublished = true")
    List<KnowledgeArticle> findByTagsContaining(@Param("tag") String tag, Pageable pageable);
    
    /**
     * Find articles created between dates
     */
    @Query("SELECT ka FROM KnowledgeArticle ka WHERE ka.createdAt BETWEEN :startDate AND :endDate")
    List<KnowledgeArticle> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Count published articles
     */
    @Query("SELECT COUNT(ka) FROM KnowledgeArticle ka WHERE ka.isPublished = true")
    long countPublishedArticles();
    
    /**
     * Sum total view count
     */
    @Query("SELECT COALESCE(SUM(ka.viewCount), 0) FROM KnowledgeArticle ka WHERE ka.isPublished = true")
    long sumViewCount();
}