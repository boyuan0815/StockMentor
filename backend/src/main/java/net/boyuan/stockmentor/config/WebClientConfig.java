package net.boyuan.stockmentor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {

//        CANNOT return new WebClient() directly because:
//              1. hard to test
//              2. every class got own instance
//              3. harder to manage timeout/baseUtl/auth

        return WebClient.builder()
                .baseUrl("https://api.twelvedata.com")
                .build();
    }
}
