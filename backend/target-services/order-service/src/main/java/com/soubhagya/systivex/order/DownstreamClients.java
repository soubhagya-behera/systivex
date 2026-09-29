package com.soubhagya.systivex.order;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP clients for the two downstream services. URLs come from configuration
 * (environment-overridable), never from source. Plain timeouts only — no
 * retry or circuit-breaker machinery in this phase.
 */
@Configuration
public class DownstreamClients {

    @Bean
    RestClient.Builder downstreamRestClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Bean
    RestClient inventoryClient(
            RestClient.Builder downstreamRestClientBuilder,
            @Value("${app.inventory-service.url:http://localhost:8084}") String baseUrl) {
        return downstreamRestClientBuilder.baseUrl(baseUrl).build();
    }

    @Bean
    RestClient paymentClient(
            RestClient.Builder downstreamRestClientBuilder,
            @Value("${app.payment-service.url:http://localhost:8083}") String baseUrl) {
        return downstreamRestClientBuilder.baseUrl(baseUrl).build();
    }
}
