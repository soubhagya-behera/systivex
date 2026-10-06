package com.soubhagya.systivex.gateway;

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
 * HTTP client toward order-service. URL from configuration, never source. The
 * builder carries the observation registry explicitly so the incoming trace
 * context is forwarded (metrics + trace-context propagation) — without it
 * the distributed trace would silently stop at the gateway hop.
 */
@Configuration
public class OrderClientConfig {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    RestClient.Builder orderServiceBuilder(ObservationRegistry observations) {
        return RestClient.builder().observationRegistry(observations);
    }

    @Bean
    RestClient orderClient(
            RestClient.Builder orderServiceBuilder,
            @Value("${app.order-service.url:http://localhost:8082}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        return orderServiceBuilder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
