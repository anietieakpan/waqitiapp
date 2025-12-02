package com.waqiti.common.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.jdbc.datasource.OpenTelemetryDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Bean post processor to automatically wrap DataSources with OpenTelemetry instrumentation
 * 
 * @author Waqiti Platform Team
 * @since Phase 3 - OpenTelemetry Implementation
 */
@Slf4j
@Component
public class DataSourceInstrumentationBean implements BeanPostProcessor {
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource && !(bean instanceof OpenTelemetryDataSource)) {
            log.info("Instrumenting DataSource bean: {}", beanName);
            return new OpenTelemetryDataSource((DataSource) bean, GlobalOpenTelemetry.get());
        }
        return bean;
    }
}