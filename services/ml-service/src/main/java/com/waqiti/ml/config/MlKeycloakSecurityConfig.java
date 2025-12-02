package com.waqiti.ml.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import lombok.extern.slf4j.Slf4j;

/**
 * Keycloak security configuration for ML Service
 * Manages authentication and authorization for machine learning operations and model management
 * Critical for fraud detection, risk scoring, and behavioral analysis
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class MlKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain mlKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for ML Service");
        
        return createKeycloakSecurityFilterChain(http, "ml-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints - Very limited
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/ml/public/model-status").permitAll()
                
                // Model Management & Deployment
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/models").hasAuthority("SCOPE_ml:models-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/models/deploy").hasAuthority("SCOPE_ml:model-deploy")
                .requestMatchers(HttpMethod.PUT, "/api/v1/ml/models/*/update").hasAuthority("SCOPE_ml:model-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/ml/models/*/retire").hasAuthority("SCOPE_ml:model-retire")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/models/*/status").hasAuthority("SCOPE_ml:model-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/models/*/validate").hasAuthority("SCOPE_ml:model-validate")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/models/*/metrics").hasAuthority("SCOPE_ml:model-metrics")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/models/*/rollback").hasAuthority("SCOPE_ml:model-rollback")
                
                // Fraud Detection & Scoring
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/fraud/score").hasAuthority("SCOPE_ml:fraud-score")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/fraud/batch-score").hasAuthority("SCOPE_ml:fraud-batch-score")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/fraud/rules").hasAuthority("SCOPE_ml:fraud-rules-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/ml/fraud/rules").hasAuthority("SCOPE_ml:fraud-rules-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/fraud/patterns").hasAuthority("SCOPE_ml:fraud-patterns")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/fraud/feedback").hasAuthority("SCOPE_ml:fraud-feedback")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/fraud/alerts").hasAuthority("SCOPE_ml:fraud-alerts")
                
                // Risk Assessment & Scoring
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/risk/assess").hasAuthority("SCOPE_ml:risk-assess")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/risk/batch-assess").hasAuthority("SCOPE_ml:risk-batch-assess")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/risk/profile").hasAuthority("SCOPE_ml:risk-profile")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/risk/profile/update").hasAuthority("SCOPE_ml:risk-profile-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/risk/factors").hasAuthority("SCOPE_ml:risk-factors")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/risk/thresholds").hasAuthority("SCOPE_ml:risk-thresholds")
                .requestMatchers(HttpMethod.PUT, "/api/v1/ml/risk/thresholds").hasAuthority("SCOPE_ml:risk-thresholds-set")
                
                // Behavioral Analysis
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/behavior/analyze").hasAuthority("SCOPE_ml:behavior-analyze")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/behavior/patterns").hasAuthority("SCOPE_ml:behavior-patterns")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/behavior/anomaly-detection").hasAuthority("SCOPE_ml:behavior-anomaly")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/behavior/user-segments").hasAuthority("SCOPE_ml:behavior-segments")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/behavior/clustering").hasAuthority("SCOPE_ml:behavior-clustering")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/behavior/insights").hasAuthority("SCOPE_ml:behavior-insights")
                
                // Pattern Recognition & Analysis
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/patterns/detect").hasAuthority("SCOPE_ml:pattern-detect")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/patterns/trends").hasAuthority("SCOPE_ml:pattern-trends")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/patterns/similarity").hasAuthority("SCOPE_ml:pattern-similarity")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/patterns/correlations").hasAuthority("SCOPE_ml:pattern-correlations")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/patterns/forecast").hasAuthority("SCOPE_ml:pattern-forecast")
                
                // Feature Engineering & Management
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/features").hasAuthority("SCOPE_ml:features-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/features/generate").hasAuthority("SCOPE_ml:features-generate")
                .requestMatchers(HttpMethod.PUT, "/api/v1/ml/features/*/update").hasAuthority("SCOPE_ml:features-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/ml/features/*").hasAuthority("SCOPE_ml:features-delete")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/features/importance").hasAuthority("SCOPE_ml:features-importance")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/features/selection").hasAuthority("SCOPE_ml:features-selection")
                
                // Data Preprocessing & Pipeline Management
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/pipelines").hasAuthority("SCOPE_ml:pipelines-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/pipelines/create").hasAuthority("SCOPE_ml:pipeline-create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/ml/pipelines/*").hasAuthority("SCOPE_ml:pipeline-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/ml/pipelines/*").hasAuthority("SCOPE_ml:pipeline-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/pipelines/*/execute").hasAuthority("SCOPE_ml:pipeline-execute")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/pipelines/*/status").hasAuthority("SCOPE_ml:pipeline-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/preprocessing/clean").hasAuthority("SCOPE_ml:data-clean")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/preprocessing/transform").hasAuthority("SCOPE_ml:data-transform")
                
                // Model Training & Retraining
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/training/start").hasAuthority("SCOPE_ml:training-start")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/training/status").hasAuthority("SCOPE_ml:training-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/training/stop").hasAuthority("SCOPE_ml:training-stop")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/training/history").hasAuthority("SCOPE_ml:training-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/training/hyperparameter-tuning").hasAuthority("SCOPE_ml:hyperparameter-tuning")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/training/experiments").hasAuthority("SCOPE_ml:experiments-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/training/auto-ml").hasAuthority("SCOPE_ml:auto-ml")
                
                // Model Evaluation & Validation
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/evaluation/validate").hasAuthority("SCOPE_ml:model-validate")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/evaluation/metrics").hasAuthority("SCOPE_ml:evaluation-metrics")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/evaluation/compare").hasAuthority("SCOPE_ml:model-compare")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/evaluation/a-b-test").hasAuthority("SCOPE_ml:ab-test")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/evaluation/performance").hasAuthority("SCOPE_ml:performance-metrics")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/evaluation/cross-validation").hasAuthority("SCOPE_ml:cross-validation")
                
                // Inference & Prediction Services
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/inference/predict").hasAuthority("SCOPE_ml:inference-predict")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/inference/batch-predict").hasAuthority("SCOPE_ml:inference-batch")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/inference/models/*/info").hasAuthority("SCOPE_ml:inference-model-info")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/inference/explain").hasAuthority("SCOPE_ml:inference-explain")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/inference/queue/status").hasAuthority("SCOPE_ml:inference-queue")
                
                // Data & Dataset Management
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/datasets").hasAuthority("SCOPE_ml:datasets-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/datasets/upload").hasAuthority("SCOPE_ml:dataset-upload")
                .requestMatchers(HttpMethod.PUT, "/api/v1/ml/datasets/*").hasAuthority("SCOPE_ml:dataset-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/ml/datasets/*").hasAuthority("SCOPE_ml:dataset-delete")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/datasets/*/profile").hasAuthority("SCOPE_ml:dataset-profile")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/datasets/*/validate").hasAuthority("SCOPE_ml:dataset-validate")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/datasets/*/split").hasAuthority("SCOPE_ml:dataset-split")
                
                // Model Monitoring & Drift Detection
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/monitoring/dashboard").hasAuthority("SCOPE_ml:monitoring-dashboard")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/monitoring/drift").hasAuthority("SCOPE_ml:drift-detection")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/monitoring/alerts").hasAuthority("SCOPE_ml:monitoring-alerts")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/monitoring/performance").hasAuthority("SCOPE_ml:monitoring-performance")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/monitoring/data-quality").hasAuthority("SCOPE_ml:data-quality")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/monitoring/threshold/set").hasAuthority("SCOPE_ml:threshold-set")
                
                // Explainable AI & Model Interpretability
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/explainability/explain").hasAuthority("SCOPE_ml:explainability")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/explainability/feature-importance").hasAuthority("SCOPE_ml:feature-importance")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/explainability/lime").hasAuthority("SCOPE_ml:lime-explain")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/explainability/shap").hasAuthority("SCOPE_ml:shap-explain")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/explainability/bias-detection").hasAuthority("SCOPE_ml:bias-detection")
                
                // A/B Testing & Experimentation
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/experiments/create").hasAuthority("SCOPE_ml:experiment-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/experiments").hasAuthority("SCOPE_ml:experiments-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/ml/experiments/*/update").hasAuthority("SCOPE_ml:experiment-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/experiments/*/start").hasAuthority("SCOPE_ml:experiment-start")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/experiments/*/stop").hasAuthority("SCOPE_ml:experiment-stop")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/experiments/*/results").hasAuthority("SCOPE_ml:experiment-results")
                
                // MLOps & Version Control
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/versions").hasAuthority("SCOPE_ml:versions-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/versions/create").hasAuthority("SCOPE_ml:version-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/versions/*/artifacts").hasAuthority("SCOPE_ml:version-artifacts")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/versions/*/promote").hasAuthority("SCOPE_ml:version-promote")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/registry").hasAuthority("SCOPE_ml:registry-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/registry/register").hasAuthority("SCOPE_ml:registry-register")
                
                // Analytics & Reporting
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/analytics/usage").hasAuthority("SCOPE_ml:analytics-usage")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/analytics/performance").hasAuthority("SCOPE_ml:analytics-performance")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/reports/model-performance").hasAuthority("SCOPE_ml:reports-performance")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/reports/business-impact").hasAuthority("SCOPE_ml:reports-impact")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/reports/generate").hasAuthority("SCOPE_ml:reports-generate")
                
                // Admin Operations - Model Management
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/admin/models/all").hasRole("ML_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/admin/models/*/force-retrain").hasRole("ML_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/admin/models/*/emergency-rollback").hasRole("ML_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/admin/system/resources").hasRole("ML_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/admin/system/cleanup").hasRole("ML_ADMIN")
                
                // Admin Operations - Data Science Team
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/admin/experiments/all").hasRole("DATA_SCIENTIST")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/admin/models/research/deploy").hasRole("DATA_SCIENTIST")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/admin/research/datasets").hasRole("DATA_SCIENTIST")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/admin/research/notebooks/run").hasRole("DATA_SCIENTIST")
                
                // Admin Operations - ML Engineering
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/admin/infrastructure/status").hasRole("ML_ENGINEER")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/admin/infrastructure/scale").hasRole("ML_ENGINEER")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/admin/pipelines/monitoring").hasRole("ML_ENGINEER")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/admin/pipelines/optimize").hasRole("ML_ENGINEER")
                
                // Admin Operations - System Management
                .requestMatchers("/api/v1/ml/admin/**").hasRole("ML_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/ml/admin/audit/logs").hasRole("AUDITOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/admin/bulk-operations").hasRole("ML_ADMIN")
                
                // Webhook endpoints - ML platforms and external services
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/webhooks/training-complete").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/webhooks/model-drift-alert").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/ml/webhooks/experiment-result").hasRole("SERVICE")
                
                // High-Security Internal service-to-service endpoints
                .requestMatchers("/internal/ml/**").hasRole("SERVICE")
                .requestMatchers("/internal/fraud-detection/**").hasRole("SERVICE")
                .requestMatchers("/internal/risk-scoring/**").hasRole("SERVICE")
                .requestMatchers("/internal/model-inference/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}