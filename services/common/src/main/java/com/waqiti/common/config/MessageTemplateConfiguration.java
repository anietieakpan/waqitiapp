package com.waqiti.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for message template beans
 */
@Configuration
public class MessageTemplateConfiguration {
    
    /**
     * Provides a MessageTemplate bean for template processing
     */
    @Bean
    @ConditionalOnMissingBean(name = "messageTemplate")
    public MessageTemplate messageTemplate() {
        return new DefaultMessageTemplate();
    }
    
    /**
     * Default implementation of MessageTemplate
     */
    public static class DefaultMessageTemplate implements MessageTemplate {
        @Override
        public String processTemplate(String template, Object... args) {
            return String.format(template, args);
        }
    }
    
    /**
     * Interface for message template processing
     */
    public interface MessageTemplate {
        String processTemplate(String template, Object... args);
    }
}