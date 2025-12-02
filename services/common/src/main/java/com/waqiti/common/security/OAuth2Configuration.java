// File: common/src/main/java/com/waqiti/common/security/OAuth2Configuration.java
package com.waqiti.common.security;

// Comment out for now until dependencies are fixed
/*
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
*/
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OAuth2Configuration {

    /*
    @Bean
    public WebClient webClient(ClientRegistrationRepository clientRegistrationRepository,
                              OAuth2AuthorizedClientRepository authorizedClientRepository) {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(
                        clientRegistrationRepository, authorizedClientRepository);
        oauth2.setDefaultOAuth2AuthorizedClient(true);
        return WebClient.builder()
                .apply(oauth2.oauth2Configuration())
                .build();
    }
    */
}