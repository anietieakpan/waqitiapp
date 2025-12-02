package com.waqiti.payment.core.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class PaymentWebClientConfig {

    @Value("${stripe.base-url:https://api.stripe.com}")
    private String stripeBaseUrl;
    
    @Value("${paypal.base-url:https://api-m.paypal.com}")
    private String paypalBaseUrl;
    
    @Value("${square.base-url:https://connect.squareup.com}")
    private String squareBaseUrl;
    
    @Value("${braintree.base-url:https://api.braintreegateway.com}")
    private String braintreeBaseUrl;
    
    @Value("${adyen.base-url:https://checkout-test.adyen.com}")
    private String adyenBaseUrl;
    
    @Value("${dwolla.base-url:https://api.dwolla.com}")
    private String dwollaBaseUrl;

    @Bean("stripeWebClient")
    public WebClient stripeWebClient() {
        return createSecureWebClient(stripeBaseUrl, "Stripe");
    }

    @Bean("paypalWebClient")
    public WebClient paypalWebClient() {
        return createSecureWebClient(paypalBaseUrl, "PayPal");
    }

    @Bean("squareWebClient")
    public WebClient squareWebClient() {
        return createSecureWebClient(squareBaseUrl, "Square");
    }

    @Bean("braintreeWebClient")
    public WebClient braintreeWebClient() {
        return createSecureWebClient(braintreeBaseUrl, "Braintree");
    }

    @Bean("adyenWebClient")
    public WebClient adyenWebClient() {
        return createSecureWebClient(adyenBaseUrl, "Adyen");
    }

    @Bean("dwollaWebClient")
    public WebClient dwollaWebClient() {
        return createSecureWebClient(dwollaBaseUrl, "Dwolla");
    }

    private WebClient createSecureWebClient(String baseUrl, String providerName) {
        log.info("Configuring secure WebClient for {} at {}", providerName, baseUrl);
        
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
            .responseTimeout(Duration.ofSeconds(60))
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS))
            )
            .secure(sslSpec -> sslSpec
                .handshakeTimeout(Duration.ofSeconds(30))
                .closeNotifyFlushTimeout(Duration.ofSeconds(10))
                .closeNotifyReadTimeout(Duration.ofSeconds(10))
            );

        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .bufferDefaults(bufferDefaults -> 
                bufferDefaults.maxInMemorySize(10 * 1024 * 1024)) // 10MB buffer
            .build();

        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(strategies)
            .defaultHeader("User-Agent", "Waqiti/1.0 " + providerName + "Integration")
            .defaultHeader("Accept", "application/json")
            .defaultHeader("Content-Type", "application/json")
            .filter((request, next) -> {
                log.debug("Making {} API call: {} {}", 
                    providerName, request.method(), request.url());
                return next.exchange(request);
            })
            .build();
    }
}