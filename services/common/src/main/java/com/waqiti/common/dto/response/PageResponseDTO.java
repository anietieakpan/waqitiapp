package com.waqiti.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enterprise-grade paginated response DTO with comprehensive metadata
 * 
 * This class provides a robust implementation of paginated responses with:
 * - Complete pagination metadata
 * - Content transformation support
 * - Navigation helpers
 * - Performance metrics
 * 
 * @param <T> The type of content in the page
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "Paginated response with metadata")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponseDTO<T> implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    @Schema(description = "Page content")
    private final List<T> content;
    
    @Schema(description = "Current page number (0-indexed)")
    private final int pageNumber;
    
    @Schema(description = "Page size")
    private final int pageSize;
    
    @Schema(description = "Total number of elements across all pages")
    private final long totalElements;
    
    @Schema(description = "Total number of pages")
    private final int totalPages;
    
    @Schema(description = "Whether this is the first page")
    private final boolean first;
    
    @Schema(description = "Whether this is the last page")
    private final boolean last;
    
    @Schema(description = "Whether the page is empty")
    private final boolean empty;
    
    @Schema(description = "Number of elements in current page")
    private final int numberOfElements;
    
    @Schema(description = "Sort information")
    private final SortInfo sort;
    
    @Schema(description = "Additional metadata")
    private final ResponseMetadata metadata;
    
    /**
     * Sort information for the page
     */
    @Data
    @Builder
    public static class SortInfo implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;
        
        @Schema(description = "Whether the results are sorted")
        private final boolean sorted;
        
        @Schema(description = "Whether the results are unsorted")
        private final boolean unsorted;
        
        @Schema(description = "Sort orders applied")
        private final List<SortOrder> orders;
        
        /**
         * Individual sort order
         */
        @Data
        @Builder
        public static class SortOrder implements Serializable {
            
            @Serial
            private static final long serialVersionUID = 1L;
            
            @Schema(description = "Property name")
            private final String property;
            
            @Schema(description = "Sort direction", allowableValues = {"ASC", "DESC"})
            private final String direction;
            
            @Schema(description = "Whether null handling is specified")
            private final boolean ignoreCase;
            
            @Schema(description = "Null handling", allowableValues = {"NATIVE", "NULLS_FIRST", "NULLS_LAST"})
            private final String nullHandling;
            
            /**
             * Creates from Spring Sort.Order
             */
            public static SortOrder from(Sort.Order order) {
                return SortOrder.builder()
                        .property(order.getProperty())
                        .direction(order.getDirection().name())
                        .ignoreCase(order.isIgnoreCase())
                        .nullHandling(order.getNullHandling().name())
                        .build();
            }
        }
        
        /**
         * Creates from Spring Sort
         */
        public static SortInfo from(Sort sort) {
            if (sort == null || sort.isUnsorted()) {
                return SortInfo.builder()
                        .sorted(false)
                        .unsorted(true)
                        .orders(new ArrayList<>())
                        .build();
            }
            
            List<SortOrder> orders = sort.stream()
                    .map(SortOrder::from)
                    .collect(Collectors.toList());
            
            return SortInfo.builder()
                    .sorted(true)
                    .unsorted(false)
                    .orders(orders)
                    .build();
        }
    }
    
    /**
     * Response metadata for tracking and debugging
     */
    @Data
    @Builder
    public static class ResponseMetadata implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;
        
        @Schema(description = "Response timestamp")
        private final Long timestamp;
        
        @Schema(description = "Processing time in milliseconds")
        private final Long processingTimeMs;
        
        @Schema(description = "Request ID for tracing")
        private final String requestId;
        
        @Schema(description = "Additional custom metadata")
        private final java.util.Map<String, Object> additional;
        
        /**
         * Creates default metadata
         */
        public static ResponseMetadata defaultMetadata() {
            return ResponseMetadata.builder()
                    .timestamp(System.currentTimeMillis())
                    .requestId(java.util.UUID.randomUUID().toString())
                    .build();
        }
        
        /**
         * Creates metadata with processing time
         */
        public static ResponseMetadata withProcessingTime(long startTime) {
            return ResponseMetadata.builder()
                    .timestamp(System.currentTimeMillis())
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .requestId(java.util.UUID.randomUUID().toString())
                    .build();
        }
    }
    
    /**
     * Creates PageResponseDTO from Spring Data Page
     * 
     * @param page Spring Data Page
     * @param <T> Content type
     * @return PageResponseDTO instance
     */
    public static <T> PageResponseDTO<T> from(Page<T> page) {
        return from(page, ResponseMetadata.defaultMetadata());
    }
    
    /**
     * Creates PageResponseDTO from Spring Data Page with metadata
     * 
     * @param page Spring Data Page
     * @param metadata Response metadata
     * @param <T> Content type
     * @return PageResponseDTO instance
     */
    public static <T> PageResponseDTO<T> from(Page<T> page, ResponseMetadata metadata) {
        return PageResponseDTO.<T>builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .numberOfElements(page.getNumberOfElements())
                .sort(SortInfo.from(page.getSort()))
                .metadata(metadata)
                .build();
    }
    
    /**
     * Creates PageResponseDTO with transformed content
     * 
     * @param page Spring Data Page
     * @param mapper Function to transform content
     * @param <T> Original content type
     * @param <R> Transformed content type
     * @return PageResponseDTO with transformed content
     */
    public static <T, R> PageResponseDTO<R> from(Page<T> page, Function<T, R> mapper) {
        List<R> transformedContent = page.getContent().stream()
                .map(mapper)
                .collect(Collectors.toList());
        
        return PageResponseDTO.<R>builder()
                .content(transformedContent)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .numberOfElements(page.getNumberOfElements())
                .sort(SortInfo.from(page.getSort()))
                .metadata(ResponseMetadata.defaultMetadata())
                .build();
    }
    
    /**
     * Creates an empty page response
     * 
     * @param pageable Pageable parameters
     * @param <T> Content type
     * @return Empty PageResponseDTO
     */
    public static <T> PageResponseDTO<T> empty(Pageable pageable) {
        return PageResponseDTO.<T>builder()
                .content(new ArrayList<>())
                .pageNumber(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalElements(0L)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .numberOfElements(0)
                .sort(SortInfo.from(pageable.getSort()))
                .metadata(ResponseMetadata.defaultMetadata())
                .build();
    }
    
    /**
     * Maps the content to a different type
     * 
     * @param mapper Mapping function
     * @param <R> New content type
     * @return PageResponseDTO with mapped content
     */
    public <R> PageResponseDTO<R> map(Function<T, R> mapper) {
        List<R> mappedContent = content.stream()
                .map(mapper)
                .collect(Collectors.toList());
        
        return PageResponseDTO.<R>builder()
                .content(mappedContent)
                .pageNumber(this.pageNumber)
                .pageSize(this.pageSize)
                .totalElements(this.totalElements)
                .totalPages(this.totalPages)
                .first(this.first)
                .last(this.last)
                .empty(this.empty)
                .numberOfElements(this.numberOfElements)
                .sort(this.sort)
                .metadata(this.metadata)
                .build();
    }
    
    /**
     * Checks if there's a next page
     * 
     * @return true if next page exists
     */
    public boolean hasNext() {
        return !last;
    }
    
    /**
     * Checks if there's a previous page
     * 
     * @return true if previous page exists
     */
    public boolean hasPrevious() {
        return !first;
    }
    
    /**
     * Gets the next page number
     * 
     * @return next page number or current if last page
     */
    public int getNextPageNumber() {
        return hasNext() ? pageNumber + 1 : pageNumber;
    }
    
    /**
     * Gets the previous page number
     * 
     * @return previous page number or 0 if first page
     */
    public int getPreviousPageNumber() {
        return hasPrevious() ? pageNumber - 1 : 0;
    }
    
    /**
     * Calculates the offset for the current page
     * 
     * @return offset value
     */
    public long getOffset() {
        return (long) pageNumber * pageSize;
    }
    
    /**
     * Creates a summary string for logging
     * 
     * @return summary string
     */
    public String toSummary() {
        return String.format(
            "Page %d/%d (size=%d, elements=%d/%d, sorted=%s)",
            pageNumber + 1,
            totalPages,
            pageSize,
            numberOfElements,
            totalElements,
            sort != null && sort.isSorted()
        );
    }
}