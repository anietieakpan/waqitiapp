package com.waqiti.compliance.config;

import org.drools.decisiontable.DecisionTableProviderImpl;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.builder.DecisionTableConfiguration;
import org.kie.internal.builder.DecisionTableInputType;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;

/**
 * Drools Rule Engine Configuration for AML/CTF Compliance
 * 
 * This configuration sets up the Drools rule engine with:
 * - Rule files (DRL) for complex business logic
 * - Decision tables (Excel) for business-user-maintainable rules
 * - Stateless sessions for thread-safe rule execution
 * - Dynamic rule reloading capability
 */
@Configuration
public class DroolsConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(DroolsConfig.class);
    
    private static final String RULES_PATH = "rules/";
    private static final String DRL_FILES_PATTERN = "classpath*:rules/**/*.drl";
    private static final String EXCEL_FILES_PATTERN = "classpath*:rules/**/*.xlsx";
    private static final String CSV_FILES_PATTERN = "classpath*:rules/**/*.csv";
    
    /**
     * Create KieContainer which holds all the rules
     */
    @Bean
    public KieContainer kieContainer() throws IOException {
        KieServices kieServices = KieServices.Factory.get();
        
        // Create KieFileSystem to programmatically add rules
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
        
        // Load DRL rule files
        loadRuleFiles(kieFileSystem, DRL_FILES_PATTERN, "drl");
        
        // Load Excel decision tables
        loadDecisionTables(kieFileSystem, EXCEL_FILES_PATTERN, DecisionTableInputType.XLS);
        
        // Load CSV decision tables
        loadDecisionTables(kieFileSystem, CSV_FILES_PATTERN, DecisionTableInputType.CSV);
        
        // Build the KieModule
        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();
        
        // Check for errors
        Results results = kieBuilder.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException("Error building rules: " + results.getMessages());
        }
        
        // Get KieModule and create KieContainer
        KieModule kieModule = kieBuilder.getKieModule();
        KieContainer kieContainer = kieServices.newKieContainer(kieModule.getReleaseId());
        
        logger.info("Drools KieContainer initialized successfully with {} rule packages", 
                   kieContainer.getKieBaseNames().size());
        
        return kieContainer;
    }
    
    /**
     * Create a prototype-scoped KieSession for stateful rule execution
     */
    @Bean
    @org.springframework.context.annotation.Scope("prototype")
    public KieSession kieSession(KieContainer kieContainer) {
        KieSession kieSession = kieContainer.newKieSession();
        
        // Add global services that rules can use
        kieSession.setGlobal("logger", LoggerFactory.getLogger("AMLRules"));
        
        logger.debug("Created new KieSession for rule execution");
        return kieSession;
    }
    
    /**
     * Create a singleton StatelessKieSession for thread-safe rule execution
     */
    @Bean
    public StatelessKieSession statelessKieSession(KieContainer kieContainer) {
        StatelessKieSession statelessSession = kieContainer.newStatelessKieSession();
        
        // Add global services
        statelessSession.setGlobal("logger", LoggerFactory.getLogger("AMLRules"));
        
        logger.info("Created StatelessKieSession for thread-safe rule execution");
        return statelessSession;
    }
    
    /**
     * Load DRL rule files into KieFileSystem
     */
    private void loadRuleFiles(KieFileSystem kieFileSystem, String pattern, String type) throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(pattern);
        
        for (Resource resource : resources) {
            if (resource.exists() && resource.isReadable()) {
                String targetPath = RULES_PATH + resource.getFilename();
                kieFileSystem.write(targetPath, 
                    ResourceFactory.newInputStreamResource(resource.getInputStream()));
                logger.info("Loaded {} rule file: {}", type, resource.getFilename());
            }
        }
    }
    
    /**
     * Load and compile decision tables into KieFileSystem
     */
    private void loadDecisionTables(KieFileSystem kieFileSystem, String pattern, 
                                   DecisionTableInputType inputType) throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(pattern);
        
        DecisionTableConfiguration dtConfig = KnowledgeBuilderFactory.newDecisionTableConfiguration();
        dtConfig.setInputType(inputType);
        
        for (Resource resource : resources) {
            if (resource.exists() && resource.isReadable()) {
                // Convert decision table to DRL
                String drl = new DecisionTableProviderImpl().loadFromInputStream(
                    resource.getInputStream(), dtConfig);
                
                // Write compiled DRL to KieFileSystem
                String targetPath = RULES_PATH + resource.getFilename().replace(".xlsx", ".drl")
                                                                      .replace(".csv", ".drl");
                kieFileSystem.write(targetPath, drl);
                logger.info("Compiled decision table: {} to DRL", resource.getFilename());
            }
        }
    }
    
    /**
     * Bean for runtime rule management (adding/removing rules dynamically)
     */
    @Bean
    public RuleManagementService ruleManagementService(KieContainer kieContainer) {
        return new RuleManagementService(kieContainer);
    }
    
    /**
     * Service for managing rules at runtime
     */
    public static class RuleManagementService {
        private final KieContainer kieContainer;
        private final KieServices kieServices;
        
        public RuleManagementService(KieContainer kieContainer) {
            this.kieContainer = kieContainer;
            this.kieServices = KieServices.Factory.get();
        }
        
        /**
         * Reload rules from filesystem (for dynamic rule updates)
         */
        public void reloadRules() {
            ReleaseId releaseId = kieContainer.getReleaseId();
            Results results = kieContainer.updateToVersion(releaseId);
            
            if (results.hasMessages(Message.Level.ERROR)) {
                logger.error("Error reloading rules: {}", results.getMessages());
                throw new RuntimeException("Failed to reload rules: " + results.getMessages());
            }
            
            logger.info("Successfully reloaded rules");
        }
        
        /**
         * Add a new rule dynamically
         */
        public void addRule(String ruleName, String ruleContent) {
            KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
            String rulePath = "src/main/resources/rules/dynamic/" + ruleName + ".drl";
            
            kieFileSystem.write(rulePath, ruleContent);
            
            KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
            kieBuilder.buildAll();
            
            Results results = kieBuilder.getResults();
            if (results.hasMessages(Message.Level.ERROR)) {
                logger.error("Error adding rule {}: {}", ruleName, results.getMessages());
                throw new RuntimeException("Failed to add rule: " + results.getMessages());
            }
            
            logger.info("Successfully added new rule: {}", ruleName);
        }
        
        /**
         * Get rule execution statistics
         */
        public RuleStatistics getRuleStatistics() {
            RuleStatistics stats = new RuleStatistics();
            stats.setTotalSessions(kieContainer.getKieSessionNamesInKieBase("defaultKieBase").size());
            stats.setKieBaseNames(kieContainer.getKieBaseNames());
            return stats;
        }
    }
    
    /**
     * DTO for rule statistics
     */
    public static class RuleStatistics {
        private int totalSessions;
        private java.util.Collection<String> kieBaseNames;
        
        // Getters and setters
        public int getTotalSessions() { return totalSessions; }
        public void setTotalSessions(int totalSessions) { this.totalSessions = totalSessions; }
        public java.util.Collection<String> getKieBaseNames() { return kieBaseNames; }
        public void setKieBaseNames(java.util.Collection<String> kieBaseNames) { this.kieBaseNames = kieBaseNames; }
    }
}