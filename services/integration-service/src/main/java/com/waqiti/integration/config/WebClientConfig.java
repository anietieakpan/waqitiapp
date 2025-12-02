package com.waqiti.integration.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient configuration for integration service
 */
@Configuration
@Slf4j
public class WebClientConfig {

    @Value("${core-banking-service.url:http://localhost:8088}")
    private String coreBankingServiceUrl;

    @Value("${ledger-service.url:http://localhost:8087}")
    private String ledgerServiceUrl;

    @Value("${notification-service.url:http://localhost:8086}")
    private String notificationServiceUrl;

    @Value("${compliance-service.url:http://localhost:8085}")
    private String complianceServiceUrl;

    @Value("${webclient.timeout.connection:5000}")
    private int connectionTimeout;

    @Value("${webclient.timeout.read:10000}")
    private int readTimeout;

    @Value("${webclient.timeout.write:10000}")
    private int writeTimeout;

    @Bean("coreBankingWebClient")
    public WebClient coreBankingWebClient() {
        return createWebClient(coreBankingServiceUrl, "CoreBanking");
    }

    @Bean("ledgerWebClient")
    public WebClient ledgerWebClient() {
        return createWebClient(ledgerServiceUrl, "Ledger");
    }

    @Bean("notificationWebClient")
    public WebClient notificationWebClient() {
        return createWebClient(notificationServiceUrl, "Notification");
    }

    @Bean("complianceWebClient")
    public WebClient complianceWebClient() {
        return createWebClient(complianceServiceUrl, "Compliance");
    }

    private WebClient createWebClient(String baseUrl, String serviceName) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
                .responseTimeout(Duration.ofMillis(readTimeout))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeout, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(logRequest(serviceName))
                .filter(logResponse(serviceName))
                .filter(errorHandler(serviceName))
                .build();
    }

    private ExchangeFilterFunction logRequest(String serviceName) {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            if (log.isDebugEnabled()) {
                log.debug("{} Request: {} {}", serviceName, clientRequest.method(), clientRequest.url());
                clientRequest.headers().forEach((name, values) ->
                        values.forEach(value -> log.debug("{} Request Header: {}={}", serviceName, name, value)));
            }
            return Mono.just(clientRequest);
        });
    }

    private ExchangeFilterFunction logResponse(String serviceName) {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (log.isDebugEnabled()) {
                log.debug("{} Response Status: {}", serviceName, clientResponse.statusCode());
            }
            return Mono.just(clientResponse);
        });
    }

    private ExchangeFilterFunction errorHandler(String serviceName) {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (clientResponse.statusCode().isError()) {
                log.error("{} Error Response: {} {}", serviceName, 
                         clientResponse.statusCode(), clientResponse.statusCode().getReasonPhrase());
            }
            return Mono.just(clientResponse);
        });
    }
}