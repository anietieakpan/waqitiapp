package com.waqiti.common.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Comprehensive API Versioning Service
 * 
 * Implements multiple versioning strategies:
 * - URI path versioning (v1, v2, etc.)
 * - Header-based versioning
 * - Accept header content negotiation
 * - Date-based versioning
 * - Deprecation management
 * - Backward compatibility
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiVersioningService {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^v\\d+$");
    private static final Pattern DATE_VERSION_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    @Value("${api.versioning.current-version:v1}")
    private String currentVersion;
    
    @Value("${api.versioning.supported-versions:v1}")
    private List<String> supportedVersions;
    
    @Value("${api.versioning.deprecated-versions:}")
    private List<String> deprecatedVersions;
    
    @Value("${api.versioning.default-strategy:PATH}")
    private VersioningStrategy defaultStrategy;
    
    @Value("${api.versioning.deprecation-notice-days:90}")
    private int deprecationNoticeDays;
    
    // Version compatibility matrix
    private final Map<String, VersionInfo> versionRegistry = new HashMap<>();
    
    public void init() {
        // Initialize version registry with metadata
        registerVersion("v1", VersionInfo.builder()
            .version("v1")
            .releaseDate(LocalDate.of(2024, 1, 15))
            .status(VersionStatus.STABLE)
            .deprecationDate(null)
            .sunsetDate(null)
            .backwardCompatible(new HashSet<>())
            .forwardCompatible(Set.of("v2"))
            .supportedFeatures(Set.of("payments", "wallets", "basic-kyc"))
            .build());
            
        registerVersion("v2", VersionInfo.builder()
            .version("v2")
            .releaseDate(LocalDate.of(2024, 6, 1))
            .status(VersionStatus.STABLE)
            .deprecationDate(null)
            .sunsetDate(null)
            .backwardCompatible(Set.of("v1"))
            .forwardCompatible(new HashSet<>())
            .supportedFeatures(Set.of("payments", "wallets", "enhanced-kyc", "compliance", "crypto"))
            .build());
            
        log.info("API versioning initialized with {} supported versions", supportedVersions.size());
    }

    /**
     * Extract API version from request context
     */
    public ApiVersionContext extractVersion(VersionExtractionContext context) {
        String version = null;
        VersioningStrategy usedStrategy = null;
        
        // Try different extraction strategies in order of preference
        
        // 1. URI Path versioning (/api/v1/...)
        if (context.getRequestPath() != null) {
            version = extractVersionFromPath(context.getRequestPath());
            if (version != null) {
                usedStrategy = VersioningStrategy.PATH;
            }
        }
        
        // 2. Custom version header
        if (version == null && context.getVersionHeader() != null) {
            version = validateAndNormalizeVersion(context.getVersionHeader());
            if (version != null) {
                usedStrategy = VersioningStrategy.HEADER;
            }
        }
        
        // 3. Accept header content negotiation
        if (version == null && context.getAcceptHeader() != null) {
            version = extractVersionFromAcceptHeader(context.getAcceptHeader());
            if (version != null) {
                usedStrategy = VersioningStrategy.CONTENT_NEGOTIATION;
            }
        }
        
        // 4. API-Version header (standard approach)
        if (version == null && context.getApiVersionHeader() != null) {
            version = validateAndNormalizeVersion(context.getApiVersionHeader());
            if (version != null) {
                usedStrategy = VersioningStrategy.API_VERSION_HEADER;
            }
        }
        
        // 5. Query parameter
        if (version == null && context.getVersionParameter() != null) {
            version = validateAndNormalizeVersion(context.getVersionParameter());
            if (version != null) {
                usedStrategy = VersioningStrategy.QUERY_PARAMETER;
            }
        }
        
        // Default to current version if no version specified
        if (version == null) {
            version = currentVersion;
            usedStrategy = VersioningStrategy.DEFAULT;
        }
        
        return createVersionContext(version, usedStrategy, context);
    }

    /**
     * Validate version compatibility
     */
    public VersionCompatibilityResult checkCompatibility(String requestedVersion, String targetVersion) {
        VersionInfo requestedInfo = versionRegistry.get(requestedVersion);
        VersionInfo targetInfo = versionRegistry.get(targetVersion);
        
        if (requestedInfo == null) {
            return VersionCompatibilityResult.unsupported(requestedVersion);
        }
        
        if (targetInfo == null) {
            return VersionCompatibilityResult.unsupported(targetVersion);
        }
        
        // Check if versions are compatible
        if (requestedVersion.equals(targetVersion)) {
            return VersionCompatibilityResult.exact();
        }
        
        if (requestedInfo.getBackwardCompatible().contains(targetVersion)) {
            return VersionCompatibilityResult.backwardCompatible();
        }
        
        if (requestedInfo.getForwardCompatible().contains(targetVersion)) {
            return VersionCompatibilityResult.forwardCompatible();
        }
        
        return VersionCompatibilityResult.incompatible(requestedVersion, targetVersion);
    }

    /**
     * Generate deprecation warnings
     */
    public Optional<DeprecationWarning> getDeprecationWarning(String version) {
        VersionInfo versionInfo = versionRegistry.get(version);
        
        if (versionInfo == null || versionInfo.getStatus() != VersionStatus.DEPRECATED) {
            return Optional.empty();
        }
        
        LocalDate deprecationDate = versionInfo.getDeprecationDate();
        LocalDate sunsetDate = versionInfo.getSunsetDate();
        long daysUntilSunset = sunsetDate != null ? 
            java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), sunsetDate) : -1;
        
        return Optional.of(DeprecationWarning.builder()
            .version(version)
            .deprecationDate(deprecationDate)
            .sunsetDate(sunsetDate)
            .daysUntilSunset(daysUntilSunset)
            .recommendedVersion(currentVersion)
            .migrationGuideUrl("/docs/migration/" + version + "-to-" + currentVersion)
            .build());
    }

    /**
     * Check if feature is supported in version
     */
    public boolean isFeatureSupported(String version, String feature) {
        VersionInfo versionInfo = versionRegistry.get(version);
        return versionInfo != null && versionInfo.getSupportedFeatures().contains(feature);
    }

    /**
     * Get version transformation rules
     */
    public VersionTransformation getTransformation(String fromVersion, String toVersion) {
        // Implementation for request/response transformation between versions
        return VersionTransformation.builder()
            .fromVersion(fromVersion)
            .toVersion(toVersion)
            .requestTransformers(getRequestTransformers(fromVersion, toVersion))
            .responseTransformers(getResponseTransformers(fromVersion, toVersion))
            .build();
    }

    // Private helper methods

    private String extractVersionFromPath(String path) {
        String[] pathParts = path.split("/");
        for (String part : pathParts) {
            if (VERSION_PATTERN.matcher(part).matches() && supportedVersions.contains(part)) {
                return part;
            }
        }
        return null;
    }

    private String extractVersionFromAcceptHeader(String acceptHeader) {
        // Parse Accept header for version-specific media types
        // e.g., application/vnd.waqiti.v2+json
        Pattern versionMediaType = Pattern.compile("application/vnd\\.waqiti\\.(v\\d+)\\+json");
        var matcher = versionMediaType.matcher(acceptHeader);
        
        if (matcher.find()) {
            String version = matcher.group(1);
            return supportedVersions.contains(version) ? version : null;
        }
        
        return null;
    }

    private String validateAndNormalizeVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return null;
        }
        
        String normalized = version.trim().toLowerCase();
        
        // Handle different version formats
        if (DATE_VERSION_PATTERN.matcher(normalized).matches()) {
            // Convert date-based version to semantic version
            return convertDateVersionToSemantic(normalized);
        }
        
        if (VERSION_PATTERN.matcher(normalized).matches()) {
            return supportedVersions.contains(normalized) ? normalized : null;
        }
        
        // Try adding 'v' prefix
        if (normalized.matches("\\d+")) {
            String withPrefix = "v" + normalized;
            return supportedVersions.contains(withPrefix) ? withPrefix : null;
        }
        
        return null;
    }

    private String convertDateVersionToSemantic(String dateVersion) {
        // Convert date-based versions to semantic versions
        // This is a simplified example - implement based on your versioning strategy
        LocalDate date = LocalDate.parse(dateVersion, DATE_FORMATTER);
        
        if (date.isBefore(LocalDate.of(2024, 6, 1))) {
            return "v1";
        } else {
            return "v2";
        }
    }

    private ApiVersionContext createVersionContext(String version, VersioningStrategy strategy, 
                                                  VersionExtractionContext extractionContext) {
        VersionInfo versionInfo = versionRegistry.get(version);
        
        return ApiVersionContext.builder()
            .version(version)
            .strategy(strategy)
            .versionInfo(versionInfo)
            .isSupported(supportedVersions.contains(version))
            .isDeprecated(deprecatedVersions.contains(version))
            .isCurrent(currentVersion.equals(version))
            .extractionContext(extractionContext)
            .build();
    }

    private void registerVersion(String version, VersionInfo info) {
        versionRegistry.put(version, info);
    }

    private List<String> getRequestTransformers(String fromVersion, String toVersion) {
        // Return list of request transformer class names or strategies
        List<String> transformers = new ArrayList<>();
        
        if ("v1".equals(fromVersion) && "v2".equals(toVersion)) {
            transformers.add("com.waqiti.api.transform.V1ToV2RequestTransformer");
        }
        
        return transformers;
    }

    private List<String> getResponseTransformers(String fromVersion, String toVersion) {
        // Return list of response transformer class names or strategies
        List<String> transformers = new ArrayList<>();
        
        if ("v2".equals(fromVersion) && "v1".equals(toVersion)) {
            transformers.add("com.waqiti.api.transform.V2ToV1ResponseTransformer");
        }
        
        return transformers;
    }

    // Enums and DTOs

    public enum VersioningStrategy {
        PATH,
        HEADER,
        CONTENT_NEGOTIATION,
        API_VERSION_HEADER,
        QUERY_PARAMETER,
        DEFAULT
    }

    public enum VersionStatus {
        BETA,
        STABLE,
        DEPRECATED,
        SUNSET
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VersionInfo {
        private String version;
        private LocalDate releaseDate;
        private VersionStatus status;
        private LocalDate deprecationDate;
        private LocalDate sunsetDate;
        private Set<String> backwardCompatible;
        private Set<String> forwardCompatible;
        private Set<String> supportedFeatures;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ApiVersionContext {
        private String version;
        private VersioningStrategy strategy;
        private VersionInfo versionInfo;
        private boolean isSupported;
        private boolean isDeprecated;
        private boolean isCurrent;
        private VersionExtractionContext extractionContext;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VersionExtractionContext {
        private String requestPath;
        private String versionHeader;
        private String acceptHeader;
        private String apiVersionHeader;
        private String versionParameter;
        private String userAgent;
        private String clientId;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VersionCompatibilityResult {
        private boolean compatible;
        private CompatibilityType type;
        private String message;
        private String requestedVersion;
        private String targetVersion;

        public static VersionCompatibilityResult exact() {
            return VersionCompatibilityResult.builder()
                .compatible(true)
                .type(CompatibilityType.EXACT)
                .message("Exact version match")
                .build();
        }

        public static VersionCompatibilityResult backwardCompatible() {
            return VersionCompatibilityResult.builder()
                .compatible(true)
                .type(CompatibilityType.BACKWARD_COMPATIBLE)
                .message("Backward compatible")
                .build();
        }

        public static VersionCompatibilityResult forwardCompatible() {
            return VersionCompatibilityResult.builder()
                .compatible(true)
                .type(CompatibilityType.FORWARD_COMPATIBLE)
                .message("Forward compatible")
                .build();
        }

        public static VersionCompatibilityResult incompatible(String requested, String target) {
            return VersionCompatibilityResult.builder()
                .compatible(false)
                .type(CompatibilityType.INCOMPATIBLE)
                .message("Versions are incompatible")
                .requestedVersion(requested)
                .targetVersion(target)
                .build();
        }

        public static VersionCompatibilityResult unsupported(String version) {
            return VersionCompatibilityResult.builder()
                .compatible(false)
                .type(CompatibilityType.UNSUPPORTED)
                .message("Version not supported: " + version)
                .requestedVersion(version)
                .build();
        }
    }

    public enum CompatibilityType {
        EXACT,
        BACKWARD_COMPATIBLE,
        FORWARD_COMPATIBLE,
        INCOMPATIBLE,
        UNSUPPORTED
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DeprecationWarning {
        private String version;
        private LocalDate deprecationDate;
        private LocalDate sunsetDate;
        private long daysUntilSunset;
        private String recommendedVersion;
        private String migrationGuideUrl;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VersionTransformation {
        private String fromVersion;
        private String toVersion;
        private List<String> requestTransformers;
        private List<String> responseTransformers;
    }
}