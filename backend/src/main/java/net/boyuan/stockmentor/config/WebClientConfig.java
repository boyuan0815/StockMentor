package net.boyuan.stockmentor.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @Qualifier("twelveDataWebClient")
    public WebClient twelveDataWebClient(WebClient.Builder webClientBuilder) {

//        CANNOT return new WebClient() directly because:
//              1. hard to test
//              2. every class got own instance
//              3. harder to manage timeout/baseUtl/auth

        return webClientBuilder.clone()
                .baseUrl("https://api.twelvedata.com")
                .build();
    }

    @Bean
    @Qualifier("openAiWebClient")
    public WebClient openAiWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.clone()
                .baseUrl("https://api.openai.com")
                .build();
    }
}
