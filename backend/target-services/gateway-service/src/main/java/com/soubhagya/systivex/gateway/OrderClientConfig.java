package com.soubhagya.systivex.gateway;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client toward order-service. URL from configuration, never source. */
@Configuration
public class OrderClientConfig {

    @Bean
    RestClient orderClient(
            @Value("${app.order-service.url:http://localhost:8082}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(requestFactory).baseUrl(baseUrl).build();
    }
}
