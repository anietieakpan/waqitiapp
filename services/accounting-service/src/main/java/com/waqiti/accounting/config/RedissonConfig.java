package com.waqiti.accounting.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson configuration for distributed locking
 */
@Configuration
@Slf4j
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        log.info("Configuring Redisson client for distributed locking: {}:{}", redisHost, redisPort);

        Config config = new Config();
        String address = String.format("redis://%s:%d", redisHost, redisPort);

        config.useSingleServer()
            .setAddress(address)
            .setPassword(redisPassword.isEmpty() ? null : redisPassword)
            .setConnectionPoolSize(10)
            .setConnectionMinimumIdleSize(2)
            .setRetryAttempts(3)
            .setRetryInterval(1500)
            .setTimeout(3000)
            .setConnectTimeout(10000);

        return Redisson.create(config);
    }
}
