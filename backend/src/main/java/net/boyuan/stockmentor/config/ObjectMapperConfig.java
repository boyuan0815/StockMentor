package net.boyuan.stockmentor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

@Configuration
public class ObjectMapperConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }

    @Bean
    public JsonMapperBuilderCustomizer strictJsonIntegerDeserialization() {
        return builder -> builder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
