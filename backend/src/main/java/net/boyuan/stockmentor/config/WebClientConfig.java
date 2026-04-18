package net.boyuan.stockmentor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

// let Spring will scan this class at startup, then will look inside @Bean methods...
@Configuration
public class WebClientConfig {

//  run this task once, then get the returned object, register this returned object into Spring container
//  then later in other class with @Repository,
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
