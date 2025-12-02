package com.waqiti.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "payment")
public class PaymentConfigurationProperties {
    
    private Commons commons = new Commons();
    private Fraud fraud = new Fraud();
    private Events events = new Events();
    private Fees fees = new Fees();
    private Validation validation = new Validation();
    private RateLimiting rateLimiting = new RateLimiting();
    private Audit audit = new Audit();
    private WebClient webClient = new WebClient();
    private RateLimit rateLimit = new RateLimit();
    private Executor executor = new Executor();
    
    // High value transaction thresholds
    private BigDecimal highValueThreshold = new BigDecimal("5000");
    private BigDecimal criticalValueThreshold = new BigDecimal("25000");
    private BigDecimal extremeValueThreshold = new BigDecimal("100000");
    
    // MFA configuration
    private int mfaSessionDurationMinutes = 10;
    private int mfaChallengeExpiryMinutes = 5;
    private int mfaMaxAttempts = 3;
    
    @Data
    public static class Commons {
        private Providers providers = new Providers();
        
        @Data
        public static class Providers {
            private Wise wise = new Wise();
            private Plaid plaid = new Plaid();
            private Bitcoin bitcoin = new Bitcoin();
            private Square square = new Square();
            private Stripe stripe = new Stripe();
            private Ethereum ethereum = new Ethereum();
            private Braintree braintree = new Braintree();
            private PayPal paypal = new PayPal();
            private Adyen adyen = new Adyen();
            private Dwolla dwolla = new Dwolla();
            
            private String wiseSupportedPaymentTypes = "BANK_TRANSFER,SWIFT";
            private String plaidSupportedPaymentTypes = "ACH,WIRE";
            private boolean plaidCapabilitiesAchTransfer = true;
            private String bitcoinSupportedCurrencies = "BTC,ETH,LTC,XRP";
            private String squareSupportedPaymentTypes = "CARD,ACH,WALLET";
            private String stripeSupportedPaymentTypes = "CARD,BANK,WALLET,CRYPTO";
            
            // Additional provider properties
            private int braintreePriority = 5;
            private boolean braintreeCapabilitiesVault = true;
            private String braintreeSupportedCurrencies = "USD,EUR,GBP";
            private int bitcoinPriority = 10;
            private String bitcoinNetwork = "mainnet";
            private String paypalSupportedPaymentTypes = "PAYPAL,VENMO,CREDIT";
            private int paypalPriority = 3;
            private String paypalApiVersion = "v2";
            private boolean paypalCapabilitiesSubscription = true;
            private boolean ethereumEnabled = true;
            private int wisePriority = 4;
            private boolean wiseCapabilitiesMultiCurrency = true;
            private boolean wiseCapabilitiesRateLock = true;
            private int plaidPriority = 2;
            private String plaidSupportedCurrencies = "USD,CAD";
            private boolean squareCapabilitiesPartialRefund = true;
            private boolean squareCapabilitiesPaymentLinks = true;
            private String squareSupportedCurrencies = "USD,CAD,EUR,GBP";
            private int stripeRetryBackoffMultiplier = 2;
            private boolean adyenCapabilitiesSubscription = true;
            private boolean wiseCapabilitiesInternationalTransfer = true;
            private boolean ethereumCapabilitiesErc20Tokens = true;
            private int squarePriority = 6;
            private int stripeRetryMaxAttempts = 3;
            private String stripeSupportedCurrencies = "USD,EUR,GBP,CAD,AUD,JPY";
            
            @Data
            public static class Wise {
                private Capabilities capabilities = new Capabilities();
                
                @Data
                public static class Capabilities {
                    private boolean batchPayment = true;
                }
            }
            
            @Data
            public static class Plaid {
                private String products = "transactions,accounts,auth,identity";
                private Capabilities capabilities = new Capabilities();
                
                @Data
                public static class Capabilities {
                    private boolean bankTransfer = true;
                }
            }
            
            @Data
            public static class Bitcoin {
                private Capabilities capabilities = new Capabilities();
                
                @Data
                public static class Capabilities {
                    private boolean lightningNetwork = true;
                }
            }
            
            @Data
            public static class Square {
                private boolean enabled = true;
            }
            
            @Data
            public static class Stripe {
                private Capabilities capabilities = new Capabilities();
                
                @Data
                public static class Capabilities {
                    private boolean subscription = true;
                }
            }
            
            @Data
            public static class Ethereum {
                private String supportedPaymentTypes = "DIRECT,SMART_CONTRACT";
                private Capabilities capabilities = new Capabilities();
                
                @Data
                public static class Capabilities {
                    private boolean smartContracts = true;
                }
            }
            
            @Data
            public static class Braintree {
                private Capabilities capabilities = new Capabilities();
                private String supportedPaymentTypes = "CARD,PAYPAL,VENMO";
                
                @Data
                public static class Capabilities {
                    private boolean instantTransfer = true;
                    private boolean subscription = true;
                }
            }
            
            @Data
            public static class PayPal {
                private String mode = "sandbox";
            }
            
            @Data
            public static class Adyen {
                private Capabilities capabilities = new Capabilities();
                private String supportedPaymentTypes = "CARD,BANK,WALLET";
                private String apiVersion = "v70";
                
                @Data
                public static class Capabilities {
                    private boolean instantTransfer = true;
                    private boolean subscription = true;
                    private boolean paymentLinks = true;
                }
            }
            
            @Data
            public static class Dwolla {
                private String supportedPaymentTypes = "ACH,WIRE";
                private Capabilities capabilities = new Capabilities();
                
                @Data
                public static class Capabilities {
                    private boolean instantTransfer = true;
                }
            }
        }
    }
    
    @Data
    public static class Fraud {
        private RiskThresholds riskThresholds = new RiskThresholds();
        private Rules rules = new Rules();
        private int velocityCheckMaxTransactions = 10;
        
        @Data
        public static class RiskThresholds {
            private int low = 30;
            private int high = 70;
        }
        
        @Data
        public static class Rules {
            private VelocityCheck velocityCheck = new VelocityCheck();
            private int maxTransactions = 5;
            private BigDecimal amountLimitDaily = new BigDecimal("10000");
            private BigDecimal amountLimitSingle = new BigDecimal("5000");
            private String geoLocationBlockedCountries = "KP,IR,SY";
            
            @Data
            public static class VelocityCheck {
                private int maxTransactions = 10;
                private int windowMinutes = 60;
            }
        }
    }
    
    @Data
    public static class Events {
        private Topics topics = new Topics();
        private boolean enabled = true;
        private boolean async = true;
        private int retentionDays = 30;
        
        @Data
        public static class Topics {
            private String paymentInitiated = "payment.initiated";
            private String paymentFailed = "payment.failed";
            private String async = "payment.async";
            private String paymentCompleted = "payment.completed";
            private String paymentRefunded = "payment.refunded";
            private String paymentCancelled = "payment.cancelled";
        }
    }
    
    @Data
    public static class Fees {
        private Platform platform = new Platform();
        private Calculation calculation = new Calculation();
        
        @Data
        public static class Platform {
            private BigDecimal fixed = new BigDecimal("0.30");
            private BigDecimal percentage = new BigDecimal("2.9");
        }
        
        @Data
        public static class Calculation {
            private boolean roundUp = false;
        }
    }
    
    @Data
    public static class Validation {
        private Currency currency = new Currency();
        private Amount amount = new Amount();
        private boolean enabled = true;
        private boolean strictMode = true;
        
        @Data
        public static class Currency {
            private String defaultValue = "USD";
            private String supported = "USD,EUR,GBP,JPY,CAD,AUD";
        }
        
        @Data
        public static class Amount {
            private int decimalPlaces = 2;
        }
    }
    
    @Data
    public static class RateLimiting {
        private Map<String, PaymentTypeLimit> byPaymentType;
        private DefaultLimit defaultLimit = new DefaultLimit();
        
        @Data
        public static class PaymentTypeLimit {
            private int requestsPerMinute;
        }
        
        @Data
        public static class DefaultLimit {
            private int requestsPerHour = 1000;
        }
    }
    
    @Data
    public static class Audit {
        private boolean enabled = true;
        private boolean includeResponseData = true;
    }
    
    @Data
    public static class WebClient {
        private Pool pool = new Pool();
        private Retry retry = new Retry();
        private Timeout timeout = new Timeout();
        
        @Data
        public static class Pool {
            private int maxConnections = 50;
        }
        
        @Data
        public static class Retry {
            private int maxAttempts = 3;
            private int backoffMaxInterval = 5000;
        }
        
        @Data
        public static class Timeout {
            private int write = 10000;
            private int connection = 5000;
        }
    }
    
    @Data
    public static class RateLimit {
        private int defaultLimit = 100;
        private int windowSeconds = 60;
    }
    
    @Data
    public static class Executor {
        private int corePoolSize = 10;
        private int maxPoolSize = 50;
        private int queueCapacity = 100;
        private int keepAliveSeconds = 60;
    }
}