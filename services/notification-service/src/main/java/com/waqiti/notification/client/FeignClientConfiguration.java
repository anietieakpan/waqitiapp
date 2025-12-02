package com.waqiti.notification.client;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignClientConfiguration {
    
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
    
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
            10, TimeUnit.SECONDS,  // connectTimeout
            30, TimeUnit.SECONDS,  // readTimeout
            true                   // followRedirects
        );
    }
    
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(
            100,      // period
            1000,     // maxPeriod
            3         // maxAttempts
        );
    }
    
    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomErrorDecoder();
    }
    
    public static class CustomErrorDecoder implements ErrorDecoder {
        private final ErrorDecoder defaultErrorDecoder = new Default();
        
        @Override
        public Exception decode(String methodKey, feign.Response response) {
            if (response.status() >= 400 && response.status() < 500) {
                return new ClientException(
                    String.format("Client error calling %s: status=%d", 
                        methodKey, response.status())
                );
            } else if (response.status() >= 500) {
                return new ServerException(
                    String.format("Server error calling %s: status=%d", 
                        methodKey, response.status())
                );
            }
            return defaultErrorDecoder.decode(methodKey, response);
        }
    }
    
    public static class ClientException extends RuntimeException {
        public ClientException(String message) {
            super(message);
        }
    }
    
    public static class ServerException extends RuntimeException {
        public ServerException(String message) {
            super(message);
        }
    }
}