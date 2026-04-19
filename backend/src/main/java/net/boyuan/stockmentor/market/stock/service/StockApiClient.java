package net.boyuan.stockmentor.market.stock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class StockApiClient {
    @Value("${twelvedata.api.key}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public JsonNode fetchTimeSeries(String symbols, int outputSize) throws JsonProcessingException {
        String response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/time_series")
                        .queryParam("symbol", symbols)
                        .queryParam("interval", "1min")
                        .queryParam("outputsize", outputSize)
                        .queryParam("order", "asc")
                        .queryParam("apikey", apiKey)
                        .build()
                )
                .header("X-API-Version", "last")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return objectMapper.readTree(response);
    }
}
