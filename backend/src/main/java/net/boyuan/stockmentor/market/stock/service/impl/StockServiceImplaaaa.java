//package net.boyuan.stockmentor.market.stock.service.impl;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import net.boyuan.stockmentor.common.util.MarketTimeService;
//import net.boyuan.stockmentor.market.stock.entity.Stock;
//import net.boyuan.stockmentor.market.stock.repository.StockRepository;
//import net.boyuan.stockmentor.market.stock.service.StockService;
//import net.boyuan.stockmentor.market.stockpricehistory.entity.StockPriceHistory;
//import net.boyuan.stockmentor.market.stockpricehistory.repository.StockPriceHistoryRepository;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.client.WebClient;
//import org.springframework.web.reactive.function.client.WebClientRequestException;
//import org.springframework.web.reactive.function.client.WebClientResponseException;
//
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.time.ZoneId;
//import java.time.ZonedDateTime;
//import java.util.*;
//import java.util.stream.Collectors;
//
//import static net.boyuan.stockmentor.common.util.StockMetadata.COMPANY_MAP;
//
//@Service
////@AllArgsConstructor
//@RequiredArgsConstructor
//public class StockServiceImplaaaa implements StockService {
//
////    THE MAIN REASON WHY WE NEED TO USE @RequiredArgsConstructor INSTEAD OF @AllArgsConstructor:
//    @Value("${twelvedata.api.key}")
//    private String apiKey;
//
////    private StockRepository stockRepository;
////    private StockPriceHistoryRepository historyRepository;
//
////    @RequiredArgsConstructor only trace "final" dependency fields
//    private final StockRepository stockRepository;
//    private final StockPriceHistoryRepository historyRepository;
//    private final MarketTimeService marketTimeService;
//    private final WebClient webClient;
//    private final ObjectMapper objectMapper;
//
//    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
//    private static final Logger log = LoggerFactory.getLogger(StockServiceImplaaaa.class);
//
//    @Override
//    public void fetchAndSave(String symbols) {
////        default outputSize set to 30
//        fetchAndSave(symbols, 30);
//    }
//
//    @Override
////    @Transactional
//    public void fetchAndSave(String symbols, int outputSize) {
//
//        try {
//            log.info("Fetching TwelveData: symbols={}, outputSize={}", symbols, outputSize);
//
////            String url = "https://api.twelvedata.com/time_series?symbol="
////                    + symbols
////                    + "&interval=1min&outputsize="
////                    + outputSize
////                    + "&order=asc&apikey="
////                    + apiKey;
//
////            RestTemplate restTemplate = new RestTemplate();
////            String response = restTemplate.getForObject(url, String.class);
//            String response = webClient.get()
////                    uri = uniform resource identifier
////                    .uri(url)
//                    .uri(uriBuilder -> uriBuilder
//                            .path("/time_series")
//                            .queryParam("symbol", symbols)
//                            .queryParam("interval", "1min")
//                            .queryParam("outputsize", outputSize)
//                            .queryParam("order", "asc")
//                            .queryParam("apikey", apiKey)
//                            .build()
//                    )
//                    .header("X-API-Version", "last")
////                    .header(headers -> headers.setBearerAuth(token))
////                    .header("Authorization", "Bearer “ + token)
////                    .header("Content-Type", "application/json")
//
////                    tell the WebClient already prepared to "retrieve" or "read" response body (returned data), if not, then only send request config only
//                    .retrieve()
//
////                    convert the response body (returned data) in data type Mono<Response.class>
////                    will asynchronously convert data type from Mono<Response.class> to Response.class
////                    Mono means return 0 or one future result (similar with async/promise result)
//                    .bodyToMono(String.class)
//
////                    similar with "await", which means need to wait API return response, and converts Mono<String> to String first before next line code
////                    keep asynchronous action like bodyToMono(...) to act as synchronous action
//                    .block();
//
////            ObjectMapper mapper = new ObjectMapper();
//            JsonNode root = objectMapper.readTree(response);
//
//            // ❗ API error handling MUST IMMEDIATELY BE CONSIDERED to avoid wrongly assign value to symbolList and others
//            if (root.has("code")) {
////                "get" is less safer compare to "path" if the field is missing
////                log.error("API Error: {}" , root.get("message").asText());
//                String code = root.path("code").asText();
//                String message = root.path("message").asText("Unknown error");
//
//                log.error("API Error: code={}, message={}", code, message);
//                return;
//            }
//
//            List<String> symbolList = new ArrayList<>();
////            Given API return JSON:
////            {
////                  "NSDA" : {"meta": {...}, "values": {...}},
////                  "TSLA" : {"meta": {...}, "values": {...}},
////                  ...
////            }
////            JsonNode object's .fieldNames() means return only the KEYS: "NVDA", "TSLA"
////            "symbolList::add" called method reference(shortcut for a lambda expression), same with "item -> symbolList.add(item);"
//            root.fieldNames().forEachRemaining(symbolList::add);
//
////            List<Stock> stocks = stockRepository.findAll();
////            Purpose: Convert List<String> to Map<String, Stock>
////            Step 1: .stream() will convert the List to a Stream so that can use the functional operations on it
////            Step 2: .collect() will gather all stream objects into a new data structure(type)
////                    Collectors.toMap() will specifically collect into a Map
////            Step 3: Stock::getSymbol means retrieve each Stock object's symbol that returned from the StockRepository
////                    s -> s just put anything like stock -> stock ; x -> x ; item -> item (each Stock object itself from the StockRepository)
//            Map<String, Stock> stockMap = stockRepository.findBySymbolIn(symbolList)
//                    .stream()
//                    .collect(Collectors.toMap(Stock::getSymbol, s -> s));
//
////              Can move to outside loop:
//            LocalDate marketDate = LocalDate.now(NY_ZONE);
//
//            List<StockPriceHistory> stockPriceHistoriesToSave = new ArrayList<>();
////            JsonNode object's .fields() means return KEY + VALUE pairs: "NVDA" -> {meta, values}, "TSLA" -> {meta, values}
//              JsonNode object's .properties().forEach(entry -> ...) is the latest writing style
//            root.properties().forEach(entry -> {
//
//                String symbol = entry.getKey();
//                JsonNode stockNode = entry.getValue();
//
//                if (!stockNode.has("values")) {
//                    log.warn("Symbol={} returned no values from API", symbol);
//                    return;
//                }
//
//                JsonNode values = stockNode.get("values");
//
//                Stock stock = stockMap.computeIfAbsent(symbol, key -> {
//                   Stock s = new Stock();
//                    s.setSymbol(symbol);
//                    s.setCreatedAt(LocalDateTime.now());
//                    s.setSource("TwelveData");
//                    s.setTimezone("America/New_York");
//                    return s;
//                });
//
//                LocalDateTime start = LocalDateTime.parse(
//                        values.get(0).get("datetime").asText().replace(" ", "T")
//                );
//
//                LocalDateTime end = LocalDateTime.parse(
//                        values.get(values.size() - 1).get("datetime").asText().replace(" ", "T")
//                );
//
//                List<LocalDateTime> existingTimestamps = historyRepository.findExistingTimestamps(
//                        symbol,
//                        "1min",
//                        start,
//                        end
//                );
//
//                Set<LocalDateTime> existingTimestampSet = new HashSet<>(existingTimestamps);
//                List<JsonNode> newValues = new ArrayList<>();
//
//
//                for (JsonNode v : values) {
//
//                    LocalDateTime marketTime = LocalDateTime.parse(
//                            v.get("datetime").asText().replace(" ", "T")
//                    );
//
////                    final fetch of each day might fetch previous day's data
////                    OR
////                    first fetch of the day might fetch previous day's data
////                    need to be removed
//                    boolean isDuplicate = existingTimestampSet.contains(marketTime);
//                    boolean isToday = marketTime.toLocalDate().equals(marketDate);
//                    if (isDuplicate || !isToday) {
//                        continue;
//                    }
////                    for updateStock calculation only
//                    newValues.add(v);
//
////                    boolean exists = historyRepository
////                            .existsBySymbolAndTimestampAndTimeInterval(
////                                    symbol,
////                                    marketTime,
////                                    "1min"
////                            );
////
////                    if (exists) continue;
//                }
//
//                for (JsonNode v : newValues) {
//
//                    LocalDateTime marketTime = LocalDateTime.parse(
//                            v.get("datetime").asText().replace(" ", "T")
//                    );
//
//                    StockPriceHistory history = new StockPriceHistory();
//
//                    history.setStock(stock);
//                    history.setSymbol(symbol);
//                    history.setTimestamp(marketTime);
//                    history.setOpenPrice(new BigDecimal(v.get("open").asText()));
//                    history.setHighPrice(new BigDecimal(v.get("high").asText()));
//                    history.setLowPrice(new BigDecimal(v.get("low").asText()));
//                    history.setClosePrice(new BigDecimal(v.get("close").asText()));
//                    history.setVolume(v.get("volume").asLong());
//                    history.setTimeInterval("1min");
//                    history.setSource("TwelveData");
//                    history.setCreatedAt(LocalDateTime.now());
//
//                    stockPriceHistoriesToSave.add(history);
//                }
//
//                // 🔥 update latest
//                if (!newValues.isEmpty()) {
////                    JsonNode latestNewValue = newValues.get(newValues.size() - 1);
//                    updateStock(stock, newValues);
//                }
//            });
//
////            List<Stock> stocksToSave = new ArrayList<>(stockMap.values());
//            if (!stockMap.isEmpty()) {
//                stockRepository.saveAll(stockMap.values());
//            }
//
//            if (!stockPriceHistoriesToSave.isEmpty()) {
//                    historyRepository.saveAll(stockPriceHistoriesToSave);
//                    log.info("Saved {} new stock history rows at America/New_York time: {}", stockPriceHistoriesToSave.size(), ZonedDateTime.now(NY_ZONE));
//            } else {
//                log.info("No new stock history rows to save");
//            }
//        } catch (WebClientRequestException e) {
//            log.error("TwelveData connection error: {}", e.getMessage(), e);
//        } catch (WebClientResponseException e) {
//            log.error("TwelveData HTTP error: status={}, body={}",
//                    e.getStatusCode(),
//                    e.getResponseBodyAsString());
//        } catch (Exception e) {
//            //            e.printStackTrace();
//            log.error("Failed to fetch and save stock data for symbols={}", symbols, e);
//        }
//    }
//
//    // ✅ helper method
//    private void updateStock(Stock stock, List<JsonNode> newValues) {
//
//        if (newValues == null || newValues.isEmpty()) {
//            return;
//        }
//        JsonNode latest = newValues.get(newValues.size() - 1);
//
////        BigDecimal open = new BigDecimal(latest.get("open").asText());
//        BigDecimal close = new BigDecimal(latest.get("close").asText());
////        BigDecimal low = new BigDecimal(latest.get("low").asText());
////        BigDecimal high = new BigDecimal(latest.get("high").asText());
////        Long latestVolume = latest.get("volume").asLong();
//
//        LocalDateTime marketTime = LocalDateTime.parse(
//                latest.get("datetime").asText().replace(" ", "T")
//        );
//        LocalDate currentDate = marketTime.toLocalDate();
//
//        boolean isNewDay = stock.getLastUpdated() == null || !stock.getLastUpdated().toLocalDate().equals(currentDate);
//
//        if (isNewDay) {
//            // new day reset
//            stock.setDayHigh(null);
//            stock.setDayLow(null);
//            stock.setVolume(0L);
//        }
//
////        prevent null pointer exception if apps suddenly restart
//        if (stock.getDayOpen() == null || isNewDay) {
//            BigDecimal dayOpen = new BigDecimal(newValues.get(0).get("open").asText());
//            stock.setDayOpen(dayOpen);
//        }
//
////        stock.setCompanyName(COMPANY_MAP.get(stock.getSymbol()));
//        // first argument : "key" in the "MAP"
//        // second argument: default value
//        String companyName = COMPANY_MAP.getOrDefault(stock.getSymbol(), stock.getSymbol());
//        if (!companyName.equals(stock.getCompanyName())) {
//            stock.setCompanyName(companyName);
//        }
//
//        BigDecimal batchHigh = null;
//        BigDecimal batchLow = null;
//        long batchVolume = 0L;
//
//        for (JsonNode v : newValues) {
//            BigDecimal high = new BigDecimal(v.get("high").asText());
//            BigDecimal low = new BigDecimal(v.get("low").asText());
//            long volume = v.get("volume").asLong();
//
//            batchHigh = (batchHigh == null) ? high : batchHigh.max(high);
//            batchLow = (batchLow == null) ? low : batchLow.min(low);
//            batchVolume += volume;
//        }
//
//        // .max(...) because BigDecimal class, not data type
//        // a.max(b) return a or b depends on which one is bigger
//        stock.setDayHigh(stock.getDayHigh() == null ? batchHigh : stock.getDayHigh().max(batchHigh));
//        stock.setDayLow(stock.getDayLow() == null ? batchLow : stock.getDayLow().min(batchLow));
//        stock.setVolume(stock.getVolume() == null ? batchVolume : stock.getVolume() + batchVolume);
//
////        percentChange = (currentPrice - dayOpenPrice) / dayOpenPrice
//        if (stock.getDayOpen() != null) {
//            BigDecimal percentChange = close
//                    .subtract(stock.getDayOpen())
//                    .divide(stock.getDayOpen(), 4, RoundingMode.HALF_UP)
//                    .multiply(BigDecimal.valueOf(100));
//            stock.setPercentChange(percentChange);
//        } else {
//            log.warn("dayOpen is null for symbol = {}, skipping percentChange", stock.getSymbol());
//        }
//
//        stock.setIsMarketOpen(marketTimeService.isMarketOpen());
//        stock.setCurrentPrice(close);
//        stock.setLastUpdated(marketTime);
//        stock.setUpdatedAt(LocalDateTime.now());
//
////        stockRepository.save(stock);
//    }
//}