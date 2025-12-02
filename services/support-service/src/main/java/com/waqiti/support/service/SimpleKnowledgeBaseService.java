package com.waqiti.support.service;

import com.waqiti.support.domain.KnowledgeArticle;
import com.waqiti.support.repository.KnowledgeArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimpleKnowledgeBaseService {
    
    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final EmbeddingClient embeddingClient;
    
    /**
     * Search articles using text similarity
     */
    public List<KnowledgeArticle> searchArticles(String query, int limit) {
        log.debug("Searching knowledge articles for query: {}", query);
        
        try {
            // Generate embedding for the query
            float[] queryEmbedding = generateEmbedding(query);
            
            // In production, this would use vector similarity search
            // For now, we'll use text-based search as fallback
            List<KnowledgeArticle> results = knowledgeArticleRepository.findByContentContainingIgnoreCase(
                query, PageRequest.of(0, limit)
            );
            
            if (results.isEmpty()) {
                // Try searching by title and summary
                results = knowledgeArticleRepository.findByTitleContainingOrSummaryContainingIgnoreCase(
                    query, query, PageRequest.of(0, limit)
                );
            }
            
            return results;
            
        } catch (Exception e) {
            log.error("Error searching knowledge articles", e);
            // Fallback to simple text search
            return knowledgeArticleRepository.findByTitleContainingIgnoreCase(
                query, PageRequest.of(0, limit)
            );
        }
    }
    
    /**
     * Find articles by category
     */
    public List<KnowledgeArticle> findByCategory(String category, int limit) {
        return knowledgeArticleRepository.findByCategoryIgnoreCase(
            category, PageRequest.of(0, limit)
        );
    }
    
    /**
     * Find popular articles
     */
    public List<KnowledgeArticle> findPopularArticles(int limit) {
        return knowledgeArticleRepository.findTopByOrderByViewCountDesc(
            PageRequest.of(0, limit)
        );
    }
    
    /**
     * Find recent articles
     */
    public List<KnowledgeArticle> findRecentArticles(int limit) {
        return knowledgeArticleRepository.findTopByOrderByCreatedAtDesc(
            PageRequest.of(0, limit)
        );
    }
    
    /**
     * Find featured articles
     */
    public List<KnowledgeArticle> findFeaturedArticles(int limit) {
        return knowledgeArticleRepository.findByIsFeaturedTrueOrderByCreatedAtDesc(
            PageRequest.of(0, limit)
        );
    }
    
    @Cacheable(value = "embeddings", key = "#text.hashCode()")
    private float[] generateEmbedding(String text) {
        try {
            EmbeddingResponse response = embeddingClient.embed(text);
            return response.getResult().getOutput();
        } catch (Exception e) {
            log.error("Error generating embedding for text: {}", text, e);
            return new float[0];
        }
    }
}