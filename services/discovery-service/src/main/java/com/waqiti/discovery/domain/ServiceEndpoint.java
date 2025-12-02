package com.waqiti.discovery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.Map;

/**
 * Service Endpoint Entity
 * Represents API endpoints exposed by services
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Entity
@Table(name = "service_endpoint",
    indexes = {
        @Index(name = "idx_service_endpoint_service", columnList = "service_id"),
        @Index(name = "idx_service_endpoint_path", columnList = "endpoint_path"),
        @Index(name = "idx_service_endpoint_method", columnList = "http_method"),
        @Index(name = "idx_service_endpoint_public", columnList = "is_public"),
        @Index(name = "idx_service_endpoint_deprecated", columnList = "deprecated")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_endpoint_id", columnNames = "endpoint_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(callSuper = true)
public class ServiceEndpoint extends BaseEntity {

    @NotBlank(message = "Endpoint ID is required")
    @Size(max = 100, message = "Endpoint ID must not exceed 100 characters")
    @Column(name = "endpoint_id", nullable = false, unique = true, length = 100)
    private String endpointId;

    @NotBlank(message = "Service ID is required")
    @Size(max = 100, message = "Service ID must not exceed 100 characters")
    @Column(name = "service_id", nullable = false, length = 100)
    private String serviceId;

    @NotBlank(message = "Endpoint path is required")
    @Size(max = 500, message = "Endpoint path must not exceed 500 characters")
    @Column(name = "endpoint_path", nullable = false, length = 500)
    private String endpointPath;

    @NotBlank(message = "HTTP method is required")
    @Size(max = 10, message = "HTTP method must not exceed 10 characters")
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Size(max = 255, message = "Endpoint name must not exceed 255 characters")
    @Column(name = "endpoint_name", length = 255)
    private String endpointName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_public")
    @Builder.Default
    private Boolean isPublic = false;

    @Column(name = "requires_auth")
    @Builder.Default
    private Boolean requiresAuth = true;

    @Column(name = "required_roles", columnDefinition = "text[]")
    private String[] requiredRoles;

    @Min(value = 1, message = "Rate limit must be at least 1")
    @Column(name = "rate_limit_rpm")
    @Builder.Default
    private Integer rateLimitRpm = 1000;

    @Column(name = "timeout_seconds")
    @Builder.Default
    private Integer timeoutSeconds = 30;

    @Column(name = "cache_ttl_seconds")
    @Builder.Default
    private Integer cacheTtlSeconds = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_schema", columnDefinition = "jsonb")
    private Map<String, Object> requestSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_schema", columnDefinition = "jsonb")
    private Map<String, Object> responseSchema;

    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    @Column(name = "deprecated")
    @Builder.Default
    private Boolean deprecated = false;

    @Column(name = "deprecated_date")
    private LocalDate deprecatedDate;

    @Size(max = 100, message = "Replacement endpoint must not exceed 100 characters")
    @Column(name = "replacement_endpoint", length = 100)
    private String replacementEndpoint;

    @Size(max = 1000, message = "Documentation URL must not exceed 1000 characters")
    @Column(name = "documentation_url", length = 1000)
    private String documentationUrl;

    @NotBlank(message = "Created by is required")
    @Size(max = 100, message = "Created by must not exceed 100 characters")
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    // Business Methods

    /**
     * Deprecate this endpoint
     *
     * @param replacementEndpointId replacement endpoint ID
     */
    public void deprecate(String replacementEndpointId) {
        this.deprecated = true;
        this.deprecatedDate = LocalDate.now();
        this.replacementEndpoint = replacementEndpointId;
    }

    /**
     * Check if endpoint requires authentication
     *
     * @return true if authentication is required
     */
    public boolean isSecured() {
        return requiresAuth != null && requiresAuth;
    }

    /**
     * Check if endpoint is cacheable
     *
     * @return true if cacheable
     */
    public boolean isCacheable() {
        return cacheTtlSeconds != null && cacheTtlSeconds > 0;
    }

    /**
     * Add required role
     *
     * @param role role to add
     */
    public void addRequiredRole(String role) {
        if (requiredRoles == null) {
            requiredRoles = new String[]{role};
        } else {
            String[] newRoles = new String[requiredRoles.length + 1];
            System.arraycopy(requiredRoles, 0, newRoles, 0, requiredRoles.length);
            newRoles[requiredRoles.length] = role;
            requiredRoles = newRoles;
        }
    }

    /**
     * Get full endpoint path pattern
     *
     * @return HTTP_METHOD /path
     */
    public String getFullPattern() {
        return httpMethod.toUpperCase() + " " + endpointPath;
    }
}
