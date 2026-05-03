package net.boyuan.stockmentor.market.stock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@Service
@RequiredArgsConstructor
public class StockApiClient {
    @Value("${twelvedata.api.key}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(StockApiClient.class);

    public JsonNode fetchTimeSeries(String symbols, int outputSize) throws JsonProcessingException {
        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info("Calling TwelveData: symbols={}, outputSize={}, attempt={}", symbols, outputSize, attempt);

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
            } catch (WebClientRequestException e) {
                log.warn("TwelveData connection attempt {} failed for symbols={}: {}", attempt, symbols, e.getMessage());

                if (attempt == maxAttempts) {
                    throw e;
                }

                sleepBeforeRetry(attempt);
            }
        }

        throw new IllegalStateException("Unexpected retry flow termination");
    }

    private void sleepBeforeRetry(int attempt) {
        long delayMs = switch (attempt) {
            case 1 -> 2000L;
            case 2 -> 5000L;
            default -> 8000L;
        };

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry sleep interrupted", e);
        }
    }
}
