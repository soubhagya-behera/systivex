package com.soubhagya.systivex.order;

import java.time.Duration;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP clients for the two downstream services. URLs come from configuration
 * (environment-overridable), never from source. The shared builder is
 * prototype-scoped (builders are mutable) with the observation registry set
 * explicitly so client observations (metrics, trace-context propagation)
 * always apply — without it the distributed trace would silently stop at
 * these hops. Plain timeouts only — no retry or circuit-breaker machinery in
 * this phase.
 */
@Configuration
public class DownstreamClients {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    RestClient.Builder downstreamBuilder(ObservationRegistry observations) {
        return RestClient.builder().observationRegistry(observations);
    }

    @Bean
    RestClient inventoryClient(
            RestClient.Builder downstreamBuilder,
            @Value("${app.inventory-service.url:http://localhost:8084}") String baseUrl) {
        return downstreamBuilder.baseUrl(baseUrl).requestFactory(requestFactory()).build();
    }

    @Bean
    RestClient paymentClient(
            RestClient.Builder downstreamBuilder,
            @Value("${app.payment-service.url:http://localhost:8083}") String baseUrl) {
        return downstreamBuilder.baseUrl(baseUrl).requestFactory(requestFactory()).build();
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return requestFactory;
    }
}
