package com.waqiti.rewards.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.security.SecurityConfig;
import com.waqiti.rewards.client.PaymentServiceClient;
import com.waqiti.rewards.client.UserServiceClient;
import com.waqiti.rewards.client.WalletServiceClient;
import feign.Logger;
import feign.codec.ErrorDecoder;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Duration;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableFeignClients(basePackages = "com.waqiti.rewards.client")
@EnableJpaRepositories(basePackages = "com.waqiti.rewards.repository")
@EnableJpaAuditing
@EnableTransactionManagement
@EnableCaching
@EnableScheduling
@EnableAsync
@Import(SecurityConfig.class)
public class RewardsServiceConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    // RabbitMQ Configuration
    @Bean
    public Queue rewardsQueue() {
        return QueueBuilder.durable("rewards.queue")
                .withArgument("x-message-ttl", 3600000) // 1 hour TTL
                .build();
    }

    @Bean
    public Queue rewardsProcessingQueue() {
        return QueueBuilder.durable("rewards.processing.queue")
                .withArgument("x-max-priority", 10)
                .build();
    }

    @Bean
    public TopicExchange rewardsExchange() {
        return new TopicExchange("rewards.exchange");
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("rewards.dlx");
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("rewards.dlq").build();
    }

    @Bean
    public Binding rewardsBinding() {
        return BindingBuilder.bind(rewardsQueue())
                .to(rewardsExchange())
                .with("rewards.*");
    }

    @Bean
    public Binding rewardsProcessingBinding() {
        return BindingBuilder.bind(rewardsProcessingQueue())
                .to(rewardsExchange())
                .with("rewards.process.*");
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("rewards.dlq");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, 
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                // Handle message sending failure
                // Log or implement retry logic
            }
        });
        return template;
    }

    @Bean
    public EventPublisher eventPublisher(RabbitTemplate rabbitTemplate) {
        return new EventPublisher(rabbitTemplate, "rewards.exchange");
    }

    // Redis Cache Configuration
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values
        GenericJackson2JsonRedisSerializer jsonSerializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     ObjectMapper objectMapper) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)))
                .disableCachingNullValues();

        RedisCacheConfiguration accountCacheConfig = config
                .entryTtl(Duration.ofMinutes(30))
                .prefixCacheNameWith("rewards:account:");

        RedisCacheConfiguration tierCacheConfig = config
                .entryTtl(Duration.ofHours(1))
                .prefixCacheNameWith("rewards:tier:");

        RedisCacheConfiguration campaignCacheConfig = config
                .entryTtl(Duration.ofMinutes(15))
                .prefixCacheNameWith("rewards:campaign:");

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration("rewardsAccount", accountCacheConfig)
                .withCacheConfiguration("loyaltyTiers", tierCacheConfig)
                .withCacheConfiguration("activeCampaigns", campaignCacheConfig)
                .build();
    }

    // Feign Configuration
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new RewardsServiceErrorDecoder();
    }

    // Security Configuration
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/rewards/tiers").permitAll() // Public endpoint
                .requestMatchers("/api/v1/rewards/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/rewards/**").authenticated()
                .anyRequest().authenticated()
            .and()
            .oauth2ResourceServer()
                .jwt();
        
        return http.build();
    }

    // Error Decoder for Feign Clients
    public static class RewardsServiceErrorDecoder implements ErrorDecoder {
        private final ErrorDecoder defaultDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            switch (response.status()) {
                case 404:
                    if (methodKey.contains("PaymentServiceClient")) {
                        return new PaymentNotFoundException("Payment not found");
                    } else if (methodKey.contains("UserServiceClient")) {
                        return new UserNotFoundException("User not found");
                    } else if (methodKey.contains("WalletServiceClient")) {
                        return new WalletNotFoundException("Wallet not found");
                    }
                    break;
                case 409:
                    return new RewardsConflictException("Rewards operation conflict");
                case 400:
                    return new InvalidRewardsRequestException("Invalid rewards request");
                case 503:
                    return new ServiceUnavailableException("Dependent service unavailable");
            }
            return defaultDecoder.decode(methodKey, response);
        }
    }

    // Custom Exceptions
    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(String message) {
            super(message);
        }
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }

    public static class WalletNotFoundException extends RuntimeException {
        public WalletNotFoundException(String message) {
            super(message);
        }
    }

    public static class RewardsConflictException extends RuntimeException {
        public RewardsConflictException(String message) {
            super(message);
        }
    }

    public static class InvalidRewardsRequestException extends RuntimeException {
        public InvalidRewardsRequestException(String message) {
            super(message);
        }
    }

    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }
    }
}