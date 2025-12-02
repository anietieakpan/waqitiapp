package com.waqiti.common.bulkhead;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Configuration for bulkhead pattern implementation
 */
@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(BulkheadConfiguration.BulkheadProperties.class)
@RequiredArgsConstructor
public class BulkheadConfiguration {
    
    @Bean
    public BulkheadService bulkheadService(BulkheadProperties properties) {
        return new BulkheadService(properties);
    }
    
    @ConfigurationProperties(prefix = "waqiti.bulkhead")
    public static class BulkheadProperties {
        
        private PaymentProcessing paymentProcessing = new PaymentProcessing();
        private KycVerification kycVerification = new KycVerification();
        private FraudDetection fraudDetection = new FraudDetection();
        private Notification notification = new Notification();
        private Analytics analytics = new Analytics();
        private CoreBanking coreBanking = new CoreBanking();
        
        public static class PaymentProcessing {
            private int poolSize = 50;
            private int permits = 100;
            private int timeoutSeconds = 30;
            
            public int getPoolSize() { return poolSize; }
            public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
            public int getPermits() { return permits; }
            public void setPermits(int permits) { this.permits = permits; }
            public int getTimeoutSeconds() { return timeoutSeconds; }
            public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        }
        
        public static class KycVerification {
            private int poolSize = 20;
            private int permits = 40;
            private int timeoutSeconds = 60;
            
            public int getPoolSize() { return poolSize; }
            public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
            public int getPermits() { return permits; }
            public void setPermits(int permits) { this.permits = permits; }
            public int getTimeoutSeconds() { return timeoutSeconds; }
            public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        }
        
        public static class FraudDetection {
            private int poolSize = 30;
            private int permits = 60;
            private int timeoutSeconds = 15;
            
            public int getPoolSize() { return poolSize; }
            public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
            public int getPermits() { return permits; }
            public void setPermits(int permits) { this.permits = permits; }
            public int getTimeoutSeconds() { return timeoutSeconds; }
            public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        }
        
        public static class Notification {
            private int poolSize = 25;
            private int permits = 100;
            private int timeoutSeconds = 10;
            
            public int getPoolSize() { return poolSize; }
            public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
            public int getPermits() { return permits; }
            public void setPermits(int permits) { this.permits = permits; }
            public int getTimeoutSeconds() { return timeoutSeconds; }
            public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        }
        
        public static class Analytics {
            private int poolSize = 15;
            private int permits = 30;
            private int timeoutSeconds = 45;
            
            public int getPoolSize() { return poolSize; }
            public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
            public int getPermits() { return permits; }
            public void setPermits(int permits) { this.permits = permits; }
            public int getTimeoutSeconds() { return timeoutSeconds; }
            public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        }
        
        public static class CoreBanking {
            private int poolSize = 40;
            private int permits = 80;
            private int timeoutSeconds = 60;
            
            public int getPoolSize() { return poolSize; }
            public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
            public int getPermits() { return permits; }
            public void setPermits(int permits) { this.permits = permits; }
            public int getTimeoutSeconds() { return timeoutSeconds; }
            public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        }
        
        public PaymentProcessing getPaymentProcessing() { return paymentProcessing; }
        public void setPaymentProcessing(PaymentProcessing paymentProcessing) { this.paymentProcessing = paymentProcessing; }
        public KycVerification getKycVerification() { return kycVerification; }
        public void setKycVerification(KycVerification kycVerification) { this.kycVerification = kycVerification; }
        public FraudDetection getFraudDetection() { return fraudDetection; }
        public void setFraudDetection(FraudDetection fraudDetection) { this.fraudDetection = fraudDetection; }
        public Notification getNotification() { return notification; }
        public void setNotification(Notification notification) { this.notification = notification; }
        public Analytics getAnalytics() { return analytics; }
        public void setAnalytics(Analytics analytics) { this.analytics = analytics; }
        public CoreBanking getCoreBanking() { return coreBanking; }
        public void setCoreBanking(CoreBanking coreBanking) { this.coreBanking = coreBanking; }
    }
}