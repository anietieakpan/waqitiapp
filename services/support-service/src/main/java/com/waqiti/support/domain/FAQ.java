package com.waqiti.support.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "faqs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"tags"})
public class FAQ {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, length = 500)
    private String question;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String answer;
    
    @Column(name = "category", nullable = false)
    @Enumerated(EnumType.STRING)
    private FAQCategory category;
    
    @ElementCollection
    @CollectionTable(name = "faq_tags", joinColumns = @JoinColumn(name = "faq_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();
    
    @Column(name = "order_index")
    private Integer orderIndex;
    
    @Column(name = "view_count")
    @Builder.Default
    private Long viewCount = 0L;
    
    @Column(name = "helpful_count")
    @Builder.Default
    private Long helpfulCount = 0L;
    
    @Column(name = "not_helpful_count")
    @Builder.Default
    private Long notHelpfulCount = 0L;
    
    @Column(name = "is_published")
    @Builder.Default
    private boolean published = true;
    
    @Column(name = "is_featured")
    @Builder.Default
    private boolean featured = false;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    // Transient field for search relevance
    @Transient
    private Double relevanceScore;
    
    public enum FAQCategory {
        GENERAL,
        ACCOUNT,
        PAYMENTS,
        SECURITY,
        CARDS,
        TRANSFERS,
        FEES,
        TECHNICAL,
        VERIFICATION,
        LIMITS
    }
    
    // Helper methods
    public void incrementViewCount() {
        this.viewCount = (this.viewCount == null ? 0 : this.viewCount) + 1;
    }
    
    public void incrementHelpfulCount() {
        this.helpfulCount = (this.helpfulCount == null ? 0 : this.helpfulCount) + 1;
    }
    
    public void incrementNotHelpfulCount() {
        this.notHelpfulCount = (this.notHelpfulCount == null ? 0 : this.notHelpfulCount) + 1;
    }
    
    public double getHelpfulnessRatio() {
        long total = helpfulCount + notHelpfulCount;
        if (total == 0) return 0.0;
        return (double) helpfulCount / total;
    }
}