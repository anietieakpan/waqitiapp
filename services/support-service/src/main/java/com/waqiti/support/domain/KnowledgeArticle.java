package com.waqiti.support.domain;

import com.waqiti.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "knowledge_articles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"category", "relatedArticles", "feedback"})
public class KnowledgeArticle extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String slug;
    
    @Column(nullable = false, length = 200)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String summary;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    
    @Column(name = "content_html", columnDefinition = "TEXT")
    private String contentHtml;
    
    @ManyToOne
    @JoinColumn(name = "category_id")
    private KnowledgeCategory category;
    
    @ElementCollection
    @CollectionTable(name = "article_tags", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();
    
    @Column(name = "author_id", nullable = false)
    private String authorId;
    
    @Column(name = "author_name", nullable = false)
    private String authorName;
    
    @Column(name = "last_updated_by")
    private String lastUpdatedBy;
    
    @Column(name = "last_updated_by_name")
    private String lastUpdatedByName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArticleStatus status = ArticleStatus.DRAFT;
    
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    
    @Column(name = "view_count")
    private Long viewCount = 0L;
    
    @Column(name = "helpful_count")
    private Long helpfulCount = 0L;
    
    @Column(name = "not_helpful_count")
    private Long notHelpfulCount = 0L;
    
    @Column(name = "average_rating")
    private Double averageRating;
    
    @Column(name = "is_featured")
    private boolean isFeatured;
    
    @Column(name = "is_internal")
    private boolean isInternal;
    
    @Column(name = "requires_login")
    private boolean requiresLogin;
    
    @ElementCollection
    @CollectionTable(name = "article_languages", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "language_code")
    private Set<String> availableLanguages = new HashSet<>();
    
    @Column(name = "primary_language")
    private String primaryLanguage = "en";
    
    @ManyToMany
    @JoinTable(
        name = "related_articles",
        joinColumns = @JoinColumn(name = "article_id"),
        inverseJoinColumns = @JoinColumn(name = "related_article_id")
    )
    private Set<KnowledgeArticle> relatedArticles = new HashSet<>();
    
    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ArticleFeedback> feedback = new ArrayList<>();
    
    @Column(name = "seo_title")
    private String seoTitle;
    
    @Column(name = "seo_description")
    private String seoDescription;
    
    @Column(name = "seo_keywords")
    private String seoKeywords;
    
    @Column(name = "estimated_read_time")
    private Integer estimatedReadTimeMinutes;
    
    @Column(name = "difficulty_level")
    @Enumerated(EnumType.STRING)
    private DifficultyLevel difficultyLevel = DifficultyLevel.BEGINNER;
    
    @ElementCollection
    @CollectionTable(name = "article_attachments", joinColumns = @JoinColumn(name = "article_id"))
    private List<ArticleAttachment> attachments = new ArrayList<>();
    
    @Column(name = "video_url")
    private String videoUrl;
    
    @Column(name = "external_url")
    private String externalUrl;
    
    @PrePersist
    public void prePersist() {
        if (this.slug == null) {
            this.slug = generateSlug(this.title);
        }
        calculateReadTime();
    }
    
    @PreUpdate
    public void preUpdate() {
        calculateReadTime();
    }
    
    private String generateSlug(String title) {
        return title.toLowerCase()
                   .replaceAll("[^a-z0-9\\s-]", "")
                   .replaceAll("\\s+", "-")
                   .replaceAll("-+", "-")
                   .replaceAll("^-|-$", "");
    }
    
    private void calculateReadTime() {
        if (this.content != null) {
            int wordCount = this.content.split("\\s+").length;
            this.estimatedReadTimeMinutes = Math.max(1, wordCount / 200); // Assuming 200 words per minute
        }
    }
    
    public void incrementViewCount() {
        this.viewCount++;
    }
    
    public void markAsHelpful() {
        this.helpfulCount++;
        updateAverageRating();
    }
    
    public void markAsNotHelpful() {
        this.notHelpfulCount++;
        updateAverageRating();
    }
    
    private void updateAverageRating() {
        if (helpfulCount + notHelpfulCount > 0) {
            this.averageRating = (double) helpfulCount / (helpfulCount + notHelpfulCount) * 5;
        }
    }
    
    public void publish() {
        this.status = ArticleStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }
    
    public void archive() {
        this.status = ArticleStatus.ARCHIVED;
    }
    
    public boolean isPublished() {
        return this.status == ArticleStatus.PUBLISHED;
    }
}

