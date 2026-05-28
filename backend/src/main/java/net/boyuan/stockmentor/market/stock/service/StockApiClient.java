package net.boyuan.stockmentor.market.stock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

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
        // TwelveData's 1day range behaves like end_date is exclusive, so keep our service API inclusive
        // and ask the upstream API for one extra day.
        return fetchTimeSeries(symbols, "1day", null, null, startDate, endDate.plusDays(1));
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

                JsonNode root = objectMapper.readTree(response);
                return normalizeTimeSeriesResponse(symbols, root);
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

    private JsonNode normalizeTimeSeriesResponse(String symbols, JsonNode root) {
        if (root == null) {
            return root;
        }

        List<String> symbolList = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isBlank())
                .map(String::toUpperCase)
                .toList();

        if (symbolList.size() == 1 && root.has("values")) {
            ObjectNode wrapped = objectMapper.createObjectNode();
            wrapped.set(symbolList.get(0), root);
            return wrapped;
        }

        return root;
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
