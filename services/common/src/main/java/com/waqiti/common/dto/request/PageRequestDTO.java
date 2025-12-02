package com.waqiti.common.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Enterprise-grade pagination request DTO with comprehensive validation and sorting
 * 
 * This class provides a robust implementation of pagination parameters with:
 * - Input validation and sanitization
 * - Multiple sort field support
 * - Spring Data Pageable conversion
 * - SQL injection prevention
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "Pagination and sorting parameters for list endpoints")
public class PageRequestDTO implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final String DEFAULT_SORT_FIELD = "id";
    public static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.ASC;
    
    @Schema(description = "Page number (0-indexed)", example = "0", minimum = "0")
    @Min(value = 0, message = "Page number must be non-negative")
    private final Integer page;
    
    @Schema(description = "Page size", example = "20", minimum = "1", maximum = "100")
    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = MAX_SIZE, message = "Page size cannot exceed " + MAX_SIZE)
    private final Integer size;
    
    @Schema(description = "Sort fields with direction (e.g., 'name,asc')")
    private final List<SortField> sortFields;
    
    @Schema(description = "Search query for filtering", example = "john")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-_.@]*$", message = "Search query contains invalid characters")
    private final String searchQuery;
    
    /**
     * JSON Creator for proper deserialization
     */
    @JsonCreator
    public PageRequestDTO(
            @JsonProperty("page") Integer page,
            @JsonProperty("size") Integer size,
            @JsonProperty("sortFields") List<SortField> sortFields,
            @JsonProperty("searchQuery") String searchQuery) {
        
        this.page = page != null ? page : DEFAULT_PAGE;
        this.size = size != null ? Math.min(size, MAX_SIZE) : DEFAULT_SIZE;
        this.sortFields = sortFields != null ? new ArrayList<>(sortFields) : new ArrayList<>();
        this.searchQuery = sanitizeSearchQuery(searchQuery);
        
        // Add default sort if none provided
        if (this.sortFields.isEmpty()) {
            this.sortFields.add(new SortField(DEFAULT_SORT_FIELD, DEFAULT_SORT_DIRECTION));
        }
    }
    
    /**
     * Sort field representation with validation
     */
    @Data
    @Builder
    public static class SortField implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;
        
        @Schema(description = "Field name to sort by", example = "createdAt")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_.]*$", message = "Invalid sort field name")
        private final String field;
        
        @Schema(description = "Sort direction", example = "ASC", allowableValues = {"ASC", "DESC"})
        private final Sort.Direction direction;
        
        @JsonCreator
        public SortField(
                @JsonProperty("field") String field,
                @JsonProperty("direction") Sort.Direction direction) {
            
            this.field = validateFieldName(field);
            this.direction = direction != null ? direction : Sort.Direction.ASC;
        }
        
        /**
         * Creates a Sort.Order from this sort field
         */
        public Sort.Order toOrder() {
            return new Sort.Order(direction, field);
        }
        
        /**
         * Validates and sanitizes field name to prevent SQL injection
         */
        private static String validateFieldName(String field) {
            if (field == null || field.trim().isEmpty()) {
                return DEFAULT_SORT_FIELD;
            }
            
            // Remove any SQL injection attempts
            String sanitized = field.trim()
                    .replaceAll("[^a-zA-Z0-9_.]", "")
                    .replaceAll("\\.{2,}", ".");
            
            // Limit field name length
            if (sanitized.length() > 50) {
                sanitized = sanitized.substring(0, 50);
            }
            
            return sanitized.isEmpty() ? DEFAULT_SORT_FIELD : sanitized;
        }
    }
    
    /**
     * Converts to Spring Data Pageable
     * 
     * @return Pageable instance
     */
    public Pageable toPageable() {
        if (sortFields.isEmpty()) {
            return PageRequest.of(page, size);
        }
        
        List<Sort.Order> orders = sortFields.stream()
                .filter(Objects::nonNull)
                .map(SortField::toOrder)
                .collect(Collectors.toList());
        
        return PageRequest.of(page, size, Sort.by(orders));
    }
    
    /**
     * Creates a Pageable with specific allowed sort fields
     * 
     * @param allowedFields List of allowed field names for sorting
     * @return Pageable instance with validated sort fields
     */
    public Pageable toPageable(List<String> allowedFields) {
        if (allowedFields == null || allowedFields.isEmpty()) {
            return PageRequest.of(page, size);
        }
        
        List<Sort.Order> orders = sortFields.stream()
                .filter(sf -> sf != null && allowedFields.contains(sf.getField()))
                .map(SortField::toOrder)
                .collect(Collectors.toList());
        
        if (orders.isEmpty()) {
            return PageRequest.of(page, size);
        }
        
        return PageRequest.of(page, size, Sort.by(orders));
    }
    
    /**
     * Sanitizes search query to prevent injection attacks
     */
    private static String sanitizeSearchQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        
        // Remove SQL injection patterns
        return query.trim()
                .replaceAll("['\"\\\\;]", "")
                .replaceAll("--", "")
                .replaceAll("/\\*.*?\\*/", "")
                .replaceAll("(?i)\\b(DROP|DELETE|INSERT|UPDATE|ALTER|CREATE)\\b", "");
    }
    
    /**
     * Creates a default page request
     */
    public static PageRequestDTO defaultRequest() {
        return PageRequestDTO.builder()
                .page(DEFAULT_PAGE)
                .size(DEFAULT_SIZE)
                .sortFields(List.of(new SortField(DEFAULT_SORT_FIELD, DEFAULT_SORT_DIRECTION)))
                .build();
    }
    
    /**
     * Creates a page request with specific size
     */
    public static PageRequestDTO withSize(int size) {
        return PageRequestDTO.builder()
                .page(DEFAULT_PAGE)
                .size(Math.min(size, MAX_SIZE))
                .sortFields(List.of(new SortField(DEFAULT_SORT_FIELD, DEFAULT_SORT_DIRECTION)))
                .build();
    }
    
    /**
     * Validates the page request
     */
    public boolean isValid() {
        return page >= 0 && 
               size > 0 && 
               size <= MAX_SIZE &&
               (sortFields == null || sortFields.stream().allMatch(sf -> sf != null && sf.getField() != null));
    }
    
    /**
     * Gets the offset for database queries
     */
    public int getOffset() {
        return page * size;
    }
    
    /**
     * Checks if this is the first page
     */
    public boolean isFirstPage() {
        return page == 0;
    }
    
    /**
     * Gets the next page request
     */
    public PageRequestDTO nextPage() {
        return PageRequestDTO.builder()
                .page(page + 1)
                .size(size)
                .sortFields(new ArrayList<>(sortFields))
                .searchQuery(searchQuery)
                .build();
    }
    
    /**
     * Gets the previous page request
     */
    public PageRequestDTO previousPage() {
        return PageRequestDTO.builder()
                .page(Math.max(0, page - 1))
                .size(size)
                .sortFields(new ArrayList<>(sortFields))
                .searchQuery(searchQuery)
                .build();
    }
}