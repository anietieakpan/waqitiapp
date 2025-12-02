package com.waqiti.customer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Web MVC Configuration for Customer Service.
 * Configures message converters, CORS, path matching, and date/time formatting.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Configures CORS mappings for cross-origin requests.
     *
     * @param registry CORS registry
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders(
                "Authorization",
                "X-Correlation-Id",
                "X-Request-Id",
                "X-Total-Count",
                "Content-Disposition"
            )
            .allowCredentials(true)
            .maxAge(3600);

        log.info("CORS mappings configured for /api/**");
    }

    /**
     * Configures path matching strategy.
     * Disables trailing slash matching for more strict endpoint definitions.
     *
     * @param configurer Path match configurer
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer
            .setUseTrailingSlashMatch(false)
            .setUseCaseSensitiveMatch(true);

        log.info("Path matching configured: trailing slash match disabled");
    }

    /**
     * Configures message converters with custom Jackson ObjectMapper.
     *
     * @param converters List of HTTP message converters
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(mappingJackson2HttpMessageConverter());
        log.info("Custom Jackson message converter configured");
    }

    /**
     * Configures formatters for date/time binding from request parameters.
     *
     * @param registry Formatter registry
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setUseIsoFormat(true);
        registrar.registerFormatters(registry);

        log.info("ISO 8601 date/time formatters registered");
    }

    /**
     * Creates a custom Jackson HTTP message converter.
     *
     * @return MappingJackson2HttpMessageConverter
     */
    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        return new MappingJackson2HttpMessageConverter(objectMapper());
    }

    /**
     * Configures ObjectMapper for JSON serialization/deserialization.
     * Includes Java 8 date/time support and standard formatting options.
     *
     * @return Configured ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder
            .json()
            .modules(new JavaTimeModule())
            .featuresToDisable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                SerializationFeature.FAIL_ON_EMPTY_BEANS
            )
            .featuresToEnable(
                SerializationFeature.WRITE_DATES_WITH_ZONE_ID
            )
            .dateFormat(new com.fasterxml.jackson.databind.util.StdDateFormat())
            .build();
    }

    /**
     * Configures date/time formatter for ISO 8601 format.
     *
     * @return DateTimeFormatter for ISO 8601
     */
    @Bean
    public DateTimeFormatter dateTimeFormatter() {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    }
}
