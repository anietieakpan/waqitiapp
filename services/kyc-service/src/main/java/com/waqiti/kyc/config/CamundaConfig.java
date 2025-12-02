package com.waqiti.kyc.config;

import org.camunda.bpm.spring.boot.starter.annotation.EnableProcessApplication;
import org.camunda.bpm.spring.boot.starter.configuration.Ordering;
import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Camunda BPM Configuration for KYC Workflow Automation
 */
@Configuration
@EnableProcessApplication
@EnableConfigurationProperties(CamundaBpmProperties.class)
@RequiredArgsConstructor
public class CamundaConfig {

    private final ProcessEngine processEngine;

    /**
     * Configure Camunda for production use
     */
    @Bean
    @Order(Ordering.DEFAULT_ORDER + 1)
    public static ProcessEngineConfigurationImpl processEngineConfigurationCustomizer() {
        return new ProcessEngineConfigurationImpl() {
            @Override
            public void initHistoryLevel() {
                // Set full history for audit compliance
                setHistoryLevel(HistoryLevel.HISTORY_LEVEL_FULL);
            }
        };
    }

    @Bean
    public RuntimeService runtimeService() {
        return processEngine.getRuntimeService();
    }

    @Bean
    public TaskService taskService() {
        return processEngine.getTaskService();
    }

    @Bean
    public HistoryService historyService() {
        return processEngine.getHistoryService();
    }

    @Bean
    public RepositoryService repositoryService() {
        return processEngine.getRepositoryService();
    }

    @Bean
    public ManagementService managementService() {
        return processEngine.getManagementService();
    }
}