package com.waqiti.analytics.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import lombok.extern.slf4j.Slf4j;

/**
 * Keycloak security configuration for Analytics Service
 * Manages authentication and authorization for analytics, reporting, and ML features
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Order(1)
public class AnalyticsKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain analyticsKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "analytics-service", httpSecurity -> {
            httpSecurity
                .authorizeHttpRequests(authz -> authz
                    // Public endpoints
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/v1/analytics/public/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    
                    // Dashboard Access
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/dashboard").hasAuthority("SCOPE_analytics:dashboard-view")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/dashboard/widgets").hasAuthority("SCOPE_analytics:dashboard-view")
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/dashboard/customize").hasAuthority("SCOPE_analytics:dashboard-customize")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/dashboard/export").hasAuthority("SCOPE_analytics:dashboard-export")
                    
                    // Transaction Analytics
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/transactions").hasAuthority("SCOPE_analytics:transaction-view")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/transactions/summary").hasAuthority("SCOPE_analytics:transaction-view")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/transactions/trends").hasAuthority("SCOPE_analytics:transaction-trends")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/transactions/patterns").hasAuthority("SCOPE_analytics:transaction-patterns")
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/transactions/analyze").hasAuthority("SCOPE_analytics:transaction-analyze")
                    
                    // User Analytics
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/users/metrics").hasAuthority("SCOPE_analytics:user-metrics")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/users/behavior").hasAuthority("SCOPE_analytics:user-behavior")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/users/segments").hasAuthority("SCOPE_analytics:user-segments")
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/users/segment").hasAuthority("SCOPE_analytics:user-segment-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/users/retention").hasAuthority("SCOPE_analytics:user-retention")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/users/lifetime-value").hasAuthority("SCOPE_analytics:user-ltv")
                    
                    // Revenue Analytics
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/revenue").hasAuthority("SCOPE_analytics:revenue-view")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/revenue/forecast").hasAuthority("SCOPE_analytics:revenue-forecast")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/revenue/breakdown").hasAuthority("SCOPE_analytics:revenue-breakdown")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/revenue/growth").hasAuthority("SCOPE_analytics:revenue-growth")
                    
                    // Machine Learning Models
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/ml/predict").hasAuthority("SCOPE_analytics:ml-predict")
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/ml/train").hasAuthority("SCOPE_analytics:ml-train")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/ml/models").hasAuthority("SCOPE_analytics:ml-models-view")
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/ml/evaluate").hasAuthority("SCOPE_analytics:ml-evaluate")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/ml/recommendations").hasAuthority("SCOPE_analytics:ml-recommendations")
                    
                    // Fraud Detection Analytics
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/fraud/alerts").hasAuthority("SCOPE_analytics:fraud-view")
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/fraud/analyze").hasAuthority("SCOPE_analytics:fraud-analyze")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/fraud/patterns").hasAuthority("SCOPE_analytics:fraud-patterns")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/fraud/risk-scores").hasAuthority("SCOPE_analytics:fraud-risk")
                    
                    // Real-time Analytics
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/realtime/stream").hasAuthority("SCOPE_analytics:realtime-stream")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/realtime/metrics").hasAuthority("SCOPE_analytics:realtime-metrics")
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/realtime/subscribe").hasAuthority("SCOPE_analytics:realtime-subscribe")
                    
                    // Reports Generation
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/reports/generate").hasAuthority("SCOPE_analytics:report-generate")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/reports").hasAuthority("SCOPE_analytics:report-view")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/reports/*/download").hasAuthority("SCOPE_analytics:report-download")
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/reports/schedule").hasAuthority("SCOPE_analytics:report-schedule")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/reports/templates").hasAuthority("SCOPE_analytics:report-templates")
                    
                    // Custom Queries
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/query").hasAuthority("SCOPE_analytics:query-execute")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/query/saved").hasAuthority("SCOPE_analytics:query-view")
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/query/save").hasAuthority("SCOPE_analytics:query-save")
                    
                    // A/B Testing Analytics
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/experiments").hasAuthority("SCOPE_analytics:experiment-view")
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/experiments/create").hasAuthority("SCOPE_analytics:experiment-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/experiments/*/results").hasAuthority("SCOPE_analytics:experiment-results")
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/experiments/*/conclude").hasAuthority("SCOPE_analytics:experiment-conclude")
                    
                    // Compliance Analytics
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/compliance/aml").hasAuthority("SCOPE_analytics:compliance-aml")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/compliance/kyc").hasAuthority("SCOPE_analytics:compliance-kyc")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/compliance/audit").hasAuthority("SCOPE_analytics:compliance-audit")
                    
                    // Data Export
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/export").hasAuthority("SCOPE_analytics:data-export")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/export/formats").hasAuthority("SCOPE_analytics:export-formats")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/export/history").hasAuthority("SCOPE_analytics:export-history")
                    
                    // ETL Operations (Admin only)
                    .requestMatchers(HttpMethod.POST, "/api/v1/analytics/etl/run").hasRole("ANALYTICS_ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/analytics/etl/jobs").hasRole("ANALYTICS_ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/analytics/etl/schedule").hasRole("ANALYTICS_ADMIN")
                    
                    // Admin Operations
                    .requestMatchers("/api/v1/analytics/admin/**").hasRole("ANALYTICS_ADMIN")
                    .requestMatchers("/api/v1/analytics/config/**").hasRole("ANALYTICS_ADMIN")
                    .requestMatchers("/api/v1/analytics/data-pipeline/**").hasRole("ANALYTICS_ADMIN")
                    
                    // Internal service-to-service endpoints
                    .requestMatchers("/internal/analytics/**").hasRole("SERVICE")
                    .requestMatchers("/internal/metrics/**").hasRole("SERVICE")
                    .requestMatchers("/internal/ml/**").hasRole("SERVICE")
                    
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
                );
        });
    }
}