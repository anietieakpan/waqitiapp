package com.waqiti.support.service;

import com.waqiti.support.domain.*;
import com.waqiti.support.dto.*;
import com.waqiti.support.repository.*;
import com.waqiti.support.search.*;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService {
    
    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeCategoryRepository categoryRepository;
    private final ArticleFeedbackRepository feedbackRepository;
    private final ArticleSearchService searchService;
    private final AIAssistantService aiAssistantService;
    private final TranslationService translationService;
    
    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();
    
    // Article Management
    
    public ArticleDTO createArticle(CreateArticleRequest request) {
        log.info("Creating knowledge article: {}", request.getTitle());
        
        // Validate category
        KnowledgeCategory category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException("Category not found"));
        }
        
        // Generate slug if not provided
        String slug = request.getSlug() != null ? 
            request.getSlug() : generateSlug(request.getTitle());
        
        // Check for duplicate slug
        if (articleRepository.existsBySlug(slug)) {
            throw new BusinessException("Article with slug already exists: " + slug);
        }
        
        // Create article
        KnowledgeArticle article = KnowledgeArticle.builder()
            .slug(slug)
            .title(request.getTitle())
            .summary(request.getSummary())
            .content(request.getContent())
            .contentHtml(parseMarkdownToHtml(request.getContent()))
            .category(category)
            .tags(request.getTags())
            .authorId(request.getAuthorId())
            .authorName(request.getAuthorName())
            .status(ArticleStatus.DRAFT)
            .isFeatured(request.isFeatured())
            .isInternal(request.isInternal())
            .requiresLogin(request.isRequiresLogin())
            .primaryLanguage(request.getLanguage())
            .difficultyLevel(request.getDifficultyLevel())
            .seoTitle(request.getSeoTitle())
            .seoDescription(request.getSeoDescription())
            .seoKeywords(request.getSeoKeywords())
            .videoUrl(request.getVideoUrl())
            .externalUrl(request.getExternalUrl())
            .build();
        
        // Add initial language
        article.getAvailableLanguages().add(request.getLanguage());
        
        article = articleRepository.save(article);
        
        // Index for search
        searchService.indexArticle(article);
        
        // Generate AI summary if not provided
        if (article.getSummary() == null || article.getSummary().isEmpty()) {
            String aiSummary = aiAssistantService.generateArticleSummary(article.getContent());
            article.setSummary(aiSummary);
            articleRepository.save(article);
        }
        
        return mapToDTO(article);
    }
    
    public ArticleDTO updateArticle(String articleId, UpdateArticleRequest request) {
        KnowledgeArticle article = articleRepository.findById(articleId)
            .orElseThrow(() -> new ResourceNotFoundException("Article not found"));
        
        // Update fields
        if (request.getTitle() != null) {
            article.setTitle(request.getTitle());
        }
        if (request.getSummary() != null) {
            article.setSummary(request.getSummary());
        }
        if (request.getContent() != null) {
            article.setContent(request.getContent());
            article.setContentHtml(parseMarkdownToHtml(request.getContent()));
        }
        if (request.getCategoryId() != null) {
            KnowledgeCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException("Category not found"));
            article.setCategory(category);
        }
        if (request.getTags() != null) {
            article.setTags(request.getTags());
        }
        if (request.getStatus() != null) {
            handleStatusChange(article, request.getStatus());
        }
        
        article.setLastUpdatedBy(request.getUpdatedBy());
        article.setLastUpdatedByName(request.getUpdatedByName());
        
        article = articleRepository.save(article);
        
        // Re-index for search
        searchService.updateArticle(article);
        
        // Clear cache
        clearArticleCache(article.getSlug());
        
        return mapToDTO(article);
    }
    
    @Cacheable(value = "articles", key = "#slug")
    public ArticleDTO getArticleBySlug(String slug, String languageCode) {
        KnowledgeArticle article = articleRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Article not found"));
        
        // Check access permissions
        if (article.isInternal() && !isInternalUser()) {
            throw new BusinessException("Access denied to internal article");
        }
        
        if (article.isRequiresLogin() && !isAuthenticated()) {
            throw new BusinessException("Login required to view this article");
        }
        
        // Increment view count
        article.incrementViewCount();
        articleRepository.save(article);
        
        // Get translated version if requested
        if (languageCode != null && !languageCode.equals(article.getPrimaryLanguage())) {
            return getTranslatedArticle(article, languageCode);
        }
        
        return mapToDTO(article);
    }
    
    public void deleteArticle(String articleId) {
        KnowledgeArticle article = articleRepository.findById(articleId)
            .orElseThrow(() -> new ResourceNotFoundException("Article not found"));
        
        // Remove from search index
        searchService.deleteArticle(article.getId());
        
        // Soft delete - archive instead of hard delete
        article.archive();
        articleRepository.save(article);
        
        // Clear cache
        clearArticleCache(article.getSlug());
    }
    
    // Article Search and Discovery
    
    public Page<ArticleDTO> searchArticles(ArticleSearchRequest request, Pageable pageable) {
        // Use Elasticsearch or similar for full-text search
        SearchResult result = searchService.searchArticles(
            request.getQuery(),
            request.getCategory(),
            request.getTags(),
            request.getLanguage(),
            request.isIncludeInternal(),
            pageable
        );
        
        // Get articles from repository
        List<String> articleIds = result.getArticleIds();
        List<KnowledgeArticle> articles = articleRepository.findAllById(articleIds);
        
        // Preserve search result order
        Map<String, KnowledgeArticle> articleMap = articles.stream()
            .collect(Collectors.toMap(KnowledgeArticle::getId, a -> a));
        
        List<ArticleDTO> orderedResults = articleIds.stream()
            .map(articleMap::get)
            .filter(Objects::nonNull)
            .map(this::mapToDTO)
            .collect(Collectors.toList());
        
        return new PageImpl<>(orderedResults, pageable, result.getTotalHits());
    }
    
    public List<ArticleDTO> getSuggestedArticles(String currentArticleId, int limit) {
        KnowledgeArticle currentArticle = articleRepository.findById(currentArticleId)
            .orElseThrow(() -> new ResourceNotFoundException("Article not found"));
        
        // Get related articles based on tags and category
        List<KnowledgeArticle> suggestions = new ArrayList<>();
        
        // 1. Articles in same category
        if (currentArticle.getCategory() != null) {
            suggestions.addAll(articleRepository.findByCategoryAndStatusAndIdNot(
                currentArticle.getCategory(),
                ArticleStatus.PUBLISHED,
                currentArticleId,
                PageRequest.of(0, limit / 2)
            ));
        }
        
        // 2. Articles with similar tags
        if (!currentArticle.getTags().isEmpty()) {
            suggestions.addAll(articleRepository.findByTagsInAndStatusAndIdNot(
                currentArticle.getTags(),
                ArticleStatus.PUBLISHED,
                currentArticleId,
                PageRequest.of(0, limit / 2)
            ));
        }
        
        // 3. Use AI to find semantically similar articles
        List<String> similarArticleIds = aiAssistantService.findSimilarArticles(
            currentArticle.getContent(),
            limit
        );
        
        suggestions.addAll(articleRepository.findAllById(similarArticleIds));
        
        // Remove duplicates and limit
        return suggestions.stream()
            .distinct()
            .limit(limit)
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }
    
    public List<ArticleDTO> getFeaturedArticles(String languageCode, int limit) {
        return articleRepository.findByIsFeaturedTrueAndStatus(
            ArticleStatus.PUBLISHED,
            PageRequest.of(0, limit)
        ).stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }
    
    public List<ArticleDTO> getPopularArticles(String languageCode, int limit) {
        return articleRepository.findTopByStatusOrderByViewCountDesc(
            ArticleStatus.PUBLISHED,
            PageRequest.of(0, limit)
        ).stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }
    
    public List<ArticleDTO> getRecentArticles(String languageCode, int limit) {
        return articleRepository.findByStatusOrderByPublishedAtDesc(
            ArticleStatus.PUBLISHED,
            PageRequest.of(0, limit)
        ).stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }
    
    // Article Feedback
    
    public void submitArticleFeedback(String articleId, ArticleFeedbackRequest request) {
        KnowledgeArticle article = articleRepository.findById(articleId)
            .orElseThrow(() -> new ResourceNotFoundException("Article not found"));
        
        // Update article ratings
        if (request.isHelpful() != null) {
            if (request.isHelpful()) {
                article.markAsHelpful();
            } else {
                article.markAsNotHelpful();
            }
        }
        
        // Save detailed feedback
        if (request.getComment() != null || request.getRating() != null) {
            ArticleFeedback feedback = ArticleFeedback.builder()
                .article(article)
                .userId(request.getUserId())
                .rating(request.getRating())
                .isHelpful(request.isHelpful())
                .comment(request.getComment())
                .feedbackType(request.getFeedbackType())
                .build();
            
            feedbackRepository.save(feedback);
        }
        
        articleRepository.save(article);
        
        // Check if article needs improvement
        if (article.getAverageRating() != null && article.getAverageRating() < 3.0) {
            notifyContentTeam(article, "Low rating article needs improvement");
        }
    }
    
    // Category Management
    
    public CategoryDTO createCategory(CreateCategoryRequest request) {
        // Check for duplicate
        if (categoryRepository.existsBySlug(request.getSlug())) {
            throw new BusinessException("Category with slug already exists");
        }
        
        KnowledgeCategory parentCategory = null;
        if (request.getParentId() != null) {
            parentCategory = categoryRepository.findById(request.getParentId())
                .orElseThrow(() -> new BusinessException("Parent category not found"));
        }
        
        KnowledgeCategory category = KnowledgeCategory.builder()
            .name(request.getName())
            .slug(request.getSlug())
            .description(request.getDescription())
            .icon(request.getIcon())
            .parent(parentCategory)
            .displayOrder(request.getDisplayOrder())
            .isActive(true)
            .build();
        
        category = categoryRepository.save(category);
        
        return mapCategoryToDTO(category);
    }
    
    public List<CategoryDTO> getAllCategories(boolean includeInactive) {
        List<KnowledgeCategory> categories = includeInactive ?
            categoryRepository.findAll() :
            categoryRepository.findByIsActiveTrue();
        
        // Build hierarchical structure
        return buildCategoryTree(categories);
    }
    
    public CategoryDTO getCategoryBySlug(String slug) {
        KnowledgeCategory category = categoryRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        
        return mapCategoryToDTO(category);
    }
    
    public Page<ArticleDTO> getArticlesByCategory(String categorySlug, Pageable pageable) {
        KnowledgeCategory category = categoryRepository.findBySlug(categorySlug)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        
        return articleRepository.findByCategoryAndStatus(
            category,
            ArticleStatus.PUBLISHED,
            pageable
        ).map(this::mapToDTO);
    }
    
    // Analytics
    
    public KnowledgeBaseAnalyticsDTO getAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        List<KnowledgeArticle> articles = articleRepository.findByCreatedAtBetween(startDate, endDate);
        
        return KnowledgeBaseAnalyticsDTO.builder()
            .totalArticles(articleRepository.count())
            .publishedArticles(articleRepository.countByStatus(ArticleStatus.PUBLISHED))
            .totalViews(articleRepository.sumViewCount())
            .averageRating(calculateAverageRating())
            .topArticles(getTopArticles(10))
            .articlesByCategory(getArticleCountByCategory())
            .searchTerms(getTopSearchTerms(20))
            .feedbackSummary(getFeedbackSummary())
            .contentGaps(identifyContentGaps())
            .build();
    }
    
    // Translation Support
    
    public ArticleDTO translateArticle(String articleId, String targetLanguage) {
        KnowledgeArticle article = articleRepository.findById(articleId)
            .orElseThrow(() -> new ResourceNotFoundException("Article not found"));
        
        // Check if translation already exists
        if (article.getAvailableLanguages().contains(targetLanguage)) {
            return getTranslatedArticle(article, targetLanguage);
        }
        
        // Translate content
        TranslationResult result = translationService.translateArticle(
            article,
            article.getPrimaryLanguage(),
            targetLanguage
        );
        
        // Store translation
        storeTranslation(article, targetLanguage, result);
        
        // Update available languages
        article.getAvailableLanguages().add(targetLanguage);
        articleRepository.save(article);
        
        return getTranslatedArticle(article, targetLanguage);
    }
    
    // Import/Export
    
    public void importArticles(ImportArticlesRequest request) {
        log.info("Importing {} articles", request.getArticles().size());
        
        for (ArticleImportData data : request.getArticles()) {
            try {
                CreateArticleRequest createRequest = CreateArticleRequest.builder()
                    .title(data.getTitle())
                    .summary(data.getSummary())
                    .content(data.getContent())
                    .slug(data.getSlug())
                    .categoryId(findOrCreateCategory(data.getCategory()))
                    .tags(data.getTags())
                    .authorId(request.getImportedBy())
                    .authorName(request.getImportedByName())
                    .language(data.getLanguage())
                    .build();
                
                ArticleDTO article = createArticle(createRequest);
                
                // Auto-publish if requested
                if (request.isAutoPublish()) {
                    publishArticle(article.getId());
                }
                
            } catch (Exception e) {
                log.error("Failed to import article: {}", data.getTitle(), e);
            }
        }
    }
    
    public ExportResult exportArticles(ExportArticlesRequest request) {
        List<KnowledgeArticle> articles;
        
        if (request.getArticleIds() != null && !request.getArticleIds().isEmpty()) {
            articles = articleRepository.findAllById(request.getArticleIds());
        } else if (request.getCategoryId() != null) {
            KnowledgeCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException("Category not found"));
            articles = articleRepository.findByCategory(category);
        } else {
            articles = articleRepository.findAll();
        }
        
        // Convert to export format
        List<ArticleExportData> exportData = articles.stream()
            .map(this::mapToExportData)
            .collect(Collectors.toList());
        
        return ExportResult.builder()
            .articles(exportData)
            .exportedAt(LocalDateTime.now())
            .totalCount(exportData.size())
            .format(request.getFormat())
            .build();
    }
    
    // Helper methods
    
    private void handleStatusChange(KnowledgeArticle article, ArticleStatus newStatus) {
        ArticleStatus oldStatus = article.getStatus();
        
        if (newStatus == ArticleStatus.PUBLISHED && oldStatus != ArticleStatus.PUBLISHED) {
            article.publish();
            
            // Notify subscribers
            notifyArticlePublished(article);
            
            // Update search index
            searchService.publishArticle(article);
        } else if (newStatus == ArticleStatus.ARCHIVED) {
            article.archive();
            
            // Remove from search
            searchService.unpublishArticle(article);
        } else {
            article.setStatus(newStatus);
        }
    }
    
    private String generateSlug(String title) {
        return title.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }
    
    private String parseMarkdownToHtml(String markdown) {
        if (markdown == null) return null;
        
        var document = markdownParser.parse(markdown);
        return htmlRenderer.render(document);
    }
    
    private boolean isInternalUser() {
        // Check if current user is internal (employee, admin, etc.)
        // Implementation would check security context
        return false;
    }
    
    private boolean isAuthenticated() {
        // Check if user is authenticated
        // Implementation would check security context
        return true;
    }
    
    @CacheEvict(value = "articles", key = "#slug")
    private void clearArticleCache(String slug) {
        log.debug("Clearing cache for article: {}", slug);
    }
    
    private ArticleDTO getTranslatedArticle(KnowledgeArticle article, String languageCode) {
        // Fetch translated content from translation service/storage
        TranslatedContent translated = translationService.getTranslation(
            article.getId(),
            languageCode
        );
        
        if (translated == null) {
            // Fall back to primary language
            return mapToDTO(article);
        }
        
        // Create DTO with translated content
        ArticleDTO dto = mapToDTO(article);
        dto.setTitle(translated.getTitle());
        dto.setSummary(translated.getSummary());
        dto.setContent(translated.getContent());
        dto.setContentHtml(parseMarkdownToHtml(translated.getContent()));
        dto.setLanguageCode(languageCode);
        
        return dto;
    }
    
    private void storeTranslation(KnowledgeArticle article, String languageCode, TranslationResult result) {
        translationService.storeTranslation(
            article.getId(),
            languageCode,
            result.getTranslatedTitle(),
            result.getTranslatedSummary(),
            result.getTranslatedContent()
        );
    }
    
    private List<CategoryDTO> buildCategoryTree(List<KnowledgeCategory> categories) {
        Map<String, CategoryDTO> categoryMap = categories.stream()
            .collect(Collectors.toMap(
                KnowledgeCategory::getId,
                this::mapCategoryToDTO
            ));
        
        List<CategoryDTO> roots = new ArrayList<>();
        
        for (KnowledgeCategory category : categories) {
            CategoryDTO dto = categoryMap.get(category.getId());
            
            if (category.getParent() == null) {
                roots.add(dto);
            } else {
                CategoryDTO parent = categoryMap.get(category.getParent().getId());
                if (parent != null) {
                    parent.getSubCategories().add(dto);
                }
            }
        }
        
        // Sort by display order
        roots.sort(Comparator.comparing(CategoryDTO::getDisplayOrder));
        
        return roots;
    }
    
    private String findOrCreateCategory(String categoryName) {
        String slug = generateSlug(categoryName);
        
        return categoryRepository.findBySlug(slug)
            .map(KnowledgeCategory::getId)
            .orElseGet(() -> {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                    .name(categoryName)
                    .slug(slug)
                    .displayOrder(999)
                    .build();
                
                CategoryDTO created = createCategory(request);
                return created.getId();
            });
    }
    
    private void notifyContentTeam(KnowledgeArticle article, String reason) {
        // Send notification to content team about article issues
        log.warn("Article {} needs attention: {}", article.getId(), reason);
    }
    
    private void notifyArticlePublished(KnowledgeArticle article) {
        // Notify subscribers about new article
        log.info("Article published: {}", article.getSlug());
    }
    
    private void publishArticle(String articleId) {
        UpdateArticleRequest request = new UpdateArticleRequest();
        request.setStatus(ArticleStatus.PUBLISHED);
        request.setUpdatedBy("SYSTEM");
        request.setUpdatedByName("System");
        
        updateArticle(articleId, request);
    }
    
    // Analytics helper methods
    
    private double calculateAverageRating() {
        List<KnowledgeArticle> ratedArticles = articleRepository.findByAverageRatingIsNotNull();
        
        if (ratedArticles.isEmpty()) {
            return 0.0;
        }
        
        return ratedArticles.stream()
            .mapToDouble(KnowledgeArticle::getAverageRating)
            .average()
            .orElse(0.0);
    }
    
    private List<ArticleDTO> getTopArticles(int limit) {
        return articleRepository.findTopByStatusOrderByViewCountDesc(
            ArticleStatus.PUBLISHED,
            PageRequest.of(0, limit)
        ).stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }
    
    private Map<String, Long> getArticleCountByCategory() {
        return articleRepository.countArticlesByCategory();
    }
    
    private List<SearchTermDTO> getTopSearchTerms(int limit) {
        return searchService.getTopSearchTerms(limit);
    }
    
    private FeedbackSummaryDTO getFeedbackSummary() {
        List<ArticleFeedback> recentFeedback = feedbackRepository.findTop100ByOrderByCreatedAtDesc();
        
        long totalFeedback = recentFeedback.size();
        long positiveFeedback = recentFeedback.stream()
            .filter(f -> Boolean.TRUE.equals(f.isHelpful()))
            .count();
        
        double averageRating = recentFeedback.stream()
            .filter(f -> f.getRating() != null)
            .mapToInt(ArticleFeedback::getRating)
            .average()
            .orElse(0.0);
        
        Map<FeedbackType, Long> feedbackByType = recentFeedback.stream()
            .filter(f -> f.getFeedbackType() != null)
            .collect(Collectors.groupingBy(
                ArticleFeedback::getFeedbackType,
                Collectors.counting()
            ));
        
        return FeedbackSummaryDTO.builder()
            .totalFeedback(totalFeedback)
            .positiveFeedback(positiveFeedback)
            .negativeFeedback(totalFeedback - positiveFeedback)
            .averageRating(averageRating)
            .feedbackByType(feedbackByType)
            .build();
    }
    
    private List<ContentGapDTO> identifyContentGaps() {
        // Analyze search queries that returned no results
        List<String> noResultQueries = searchService.getNoResultQueries(30);
        
        // Analyze frequently asked questions in support tickets
        List<String> frequentQuestions = aiAssistantService.getFrequentQuestions();
        
        // Find topics without articles
        List<ContentGapDTO> gaps = new ArrayList<>();
        
        for (String query : noResultQueries) {
            gaps.add(ContentGapDTO.builder()
                .topic(query)
                .frequency(searchService.getQueryFrequency(query))
                .source("Search")
                .suggestedCategory(aiAssistantService.suggestCategory(query))
                .build());
        }
        
        for (String question : frequentQuestions) {
            if (!hasArticleForQuestion(question)) {
                gaps.add(ContentGapDTO.builder()
                    .topic(question)
                    .frequency(aiAssistantService.getQuestionFrequency(question))
                    .source("Support")
                    .suggestedCategory(aiAssistantService.suggestCategory(question))
                    .build());
            }
        }
        
        // Sort by frequency
        gaps.sort((a, b) -> b.getFrequency().compareTo(a.getFrequency()));
        
        return gaps.stream().limit(20).collect(Collectors.toList());
    }
    
    private boolean hasArticleForQuestion(String question) {
        SearchResult result = searchService.searchArticles(
            question,
            null,
            null,
            null,
            false,
            PageRequest.of(0, 1)
        );
        
        return result.getTotalHits() > 0;
    }
    
    // DTO mapping methods
    
    private ArticleDTO mapToDTO(KnowledgeArticle article) {
        return ArticleDTO.builder()
            .id(article.getId())
            .slug(article.getSlug())
            .title(article.getTitle())
            .summary(article.getSummary())
            .content(article.getContent())
            .contentHtml(article.getContentHtml())
            .categoryId(article.getCategory() != null ? article.getCategory().getId() : null)
            .categoryName(article.getCategory() != null ? article.getCategory().getName() : null)
            .tags(article.getTags())
            .authorId(article.getAuthorId())
            .authorName(article.getAuthorName())
            .status(article.getStatus())
            .viewCount(article.getViewCount())
            .helpfulCount(article.getHelpfulCount())
            .notHelpfulCount(article.getNotHelpfulCount())
            .averageRating(article.getAverageRating())
            .isFeatured(article.isFeatured())
            .isInternal(article.isInternal())
            .requiresLogin(article.isRequiresLogin())
            .languageCode(article.getPrimaryLanguage())
            .availableLanguages(article.getAvailableLanguages())
            .estimatedReadTime(article.getEstimatedReadTimeMinutes())
            .difficultyLevel(article.getDifficultyLevel())
            .videoUrl(article.getVideoUrl())
            .externalUrl(article.getExternalUrl())
            .createdAt(article.getCreatedAt())
            .updatedAt(article.getUpdatedAt())
            .publishedAt(article.getPublishedAt())
            .build();
    }
    
    private CategoryDTO mapCategoryToDTO(KnowledgeCategory category) {
        return CategoryDTO.builder()
            .id(category.getId())
            .name(category.getName())
            .slug(category.getSlug())
            .description(category.getDescription())
            .icon(category.getIcon())
            .parentId(category.getParent() != null ? category.getParent().getId() : null)
            .displayOrder(category.getDisplayOrder())
            .articleCount(category.getArticles().size())
            .isActive(category.isActive())
            .subCategories(new ArrayList<>())
            .build();
    }
    
    private ArticleExportData mapToExportData(KnowledgeArticle article) {
        return ArticleExportData.builder()
            .title(article.getTitle())
            .slug(article.getSlug())
            .summary(article.getSummary())
            .content(article.getContent())
            .category(article.getCategory() != null ? article.getCategory().getName() : null)
            .tags(article.getTags())
            .language(article.getPrimaryLanguage())
            .status(article.getStatus().toString())
            .author(article.getAuthorName())
            .createdAt(article.getCreatedAt())
            .publishedAt(article.getPublishedAt())
            .viewCount(article.getViewCount())
            .averageRating(article.getAverageRating())
            .build();
    }
}