package com.waqiti.common.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standard paged response DTO
 * Used across all services for consistent pagination
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedResponse<T> {
    
    private List<T> content;
    private PageMetadata page;
    private SortMetadata sort;
    private FilterMetadata filters;
    private LocalDateTime timestamp;
    private String traceId;
    
    // Performance and caching
    private Long executionTimeMs;
    private String cacheStatus;
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageMetadata {
        private int number;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;
        private boolean hasNext;
        private boolean hasPrevious;
        private int numberOfElements;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SortMetadata {
        private List<SortOrder> orders;
        private boolean sorted;
        private boolean unsorted;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SortOrder {
            private String property;
            private String direction; // ASC, DESC
            private boolean ignoreCase;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterMetadata {
        private Map<String, Object> activeFilters;
        private int filterCount;
        private List<String> availableFilters;
        private Map<String, Object> filterStatistics;
    }
    
    // Static factory methods
    public static <T> PagedResponse<T> of(List<T> content, int page, int size, 
                                         long totalElements, int totalPages) {
        PageMetadata pageMetadata = PageMetadata.builder()
            .number(page)
            .size(size)
            .totalElements(totalElements)
            .totalPages(totalPages)
            .first(page == 0)
            .last(page >= totalPages - 1)
            .hasNext(page < totalPages - 1)
            .hasPrevious(page > 0)
            .numberOfElements(content != null ? content.size() : 0)
            .build();
            
        return PagedResponse.<T>builder()
            .content(content)
            .page(pageMetadata)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> PagedResponse<T> empty(int page, int size) {
        return of(List.of(), page, size, 0, 0);
    }
    
    public static <T> PagedResponse<T> single(List<T> content) {
        return of(content, 0, content.size(), content.size(), 1);
    }
    
    // Helper methods
    public boolean isEmpty() {
        return content == null || content.isEmpty();
    }
    
    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }
    
    public boolean isFirstPage() {
        return page != null && page.isFirst();
    }
    
    public boolean isLastPage() {
        return page != null && page.isLast();
    }
    
    public int getContentSize() {
        return content != null ? content.size() : 0;
    }
    
    public PagedResponse<T> withSort(List<SortMetadata.SortOrder> orders) {
        this.sort = SortMetadata.builder()
            .orders(orders)
            .sorted(orders != null && !orders.isEmpty())
            .unsorted(orders == null || orders.isEmpty())
            .build();
        return this;
    }
    
    public PagedResponse<T> withFilters(Map<String, Object> activeFilters, 
                                       List<String> availableFilters) {
        this.filters = FilterMetadata.builder()
            .activeFilters(activeFilters)
            .filterCount(activeFilters != null ? activeFilters.size() : 0)
            .availableFilters(availableFilters)
            .build();
        return this;
    }
    
    public PagedResponse<T> withPerformance(Long executionTimeMs, String cacheStatus) {
        this.executionTimeMs = executionTimeMs;
        this.cacheStatus = cacheStatus;
        return this;
    }
    
    public PagedResponse<T> withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }
    
    public PagedResponse<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}