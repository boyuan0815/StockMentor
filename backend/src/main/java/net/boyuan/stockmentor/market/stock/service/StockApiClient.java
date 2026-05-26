package net.boyuan.stockmentor.market.stock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.time.LocalDate;

@Service
public class StockApiClient {
    @Value("${twelvedata.api.key}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(StockApiClient.class);

    public StockApiClient(
            @Qualifier("twelveDataWebClient") WebClient webClient,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public JsonNode fetchTimeSeries(String symbols, int outputSize) throws JsonProcessingException {
        return fetchTimeSeries(symbols, "1min", outputSize, null, null, null);
    }

    public JsonNode fetchIntradayForDate(String symbols, LocalDate date) throws JsonProcessingException {
        return fetchTimeSeries(symbols, "1min", 390, date, null, null);
    }

    public JsonNode fetchDailyRange(String symbols, LocalDate startDate, LocalDate endDate) throws JsonProcessingException {
        return fetchTimeSeries(symbols, "1day", null, null, startDate, endDate);
    }

    public JsonNode fetchTimeSeries(
            String symbols,
            String interval,
            Integer outputSize,
            LocalDate date,
            LocalDate startDate,
            LocalDate endDate
    ) throws JsonProcessingException {
        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info(
                        "Calling TwelveData: symbols={}, interval={}, outputSize={}, date={}, startDate={}, endDate={}, attempt={}",
                        symbols,
                        interval,
                        outputSize,
                        date,
                        startDate,
                        endDate,
                        attempt
                );

                String response = webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/time_series")
                                .queryParam("symbol", symbols)
                                .queryParam("interval", interval)
                                .queryParamIfPresent("outputsize", java.util.Optional.ofNullable(outputSize))
                                .queryParam("order", "asc")
                                .queryParamIfPresent("date", java.util.Optional.ofNullable(date))
                                .queryParamIfPresent("start_date", java.util.Optional.ofNullable(startDate))
                                .queryParamIfPresent("end_date", java.util.Optional.ofNullable(endDate))
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
