package com.waqiti.support.config;

import com.waqiti.support.service.TicketCategorizationService;
import com.waqiti.support.service.impl.TicketCategorizationServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class TicketCategorizationConfig {

    @Bean
    @ConfigurationProperties(prefix = "waqiti.support.categorization")
    public CategorizationSettings categorizationSettings() {
        return new CategorizationSettings();
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    public static class CategorizationSettings {
        
        // Model settings
        private boolean enabled = true;
        private boolean autoApply = true;
        private double confidenceThreshold = 0.7;
        private boolean requireHumanReview = false;
        
        // Training settings
        private int trainingBatchSize = 1000;
        private boolean autoRetrain = false;
        private String retrainSchedule = "0 0 2 * * SUN"; // Weekly on Sunday at 2 AM
        private int minTrainingData = 100;
        
        // Feature flags
        private boolean useMLClassification = true;
        private boolean useRuleBasedClassification = true;
        private boolean useSentimentAnalysis = true;
        private boolean useEscalationPrediction = true;
        private boolean useSpamDetection = true;
        
        // Performance settings
        private int maxSimilarTickets = 10;
        private int cacheSize = 1000;
        private long cacheExpirationMinutes = 60;
        
        // Notification settings
        private boolean notifyOnRecategorization = true;
        private boolean notifyOnLowConfidence = true;
        private double lowConfidenceThreshold = 0.5;
        
        // Security settings
        private boolean enableSecurityCategoryDetection = true;
        private boolean autoEscalateSecurityIssues = true;
        private boolean autoAssignCriticalTickets = true;
        
        // Quality settings
        private boolean trackAccuracy = true;
        private boolean enableFeedbackLoop = true;
        private int accuracyReportingInterval = 24; // hours
    }
}