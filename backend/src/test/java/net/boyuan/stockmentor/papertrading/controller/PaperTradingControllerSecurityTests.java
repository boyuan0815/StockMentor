package net.boyuan.stockmentor.papertrading.controller;

import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.model.DelayedMarketPrice;
import net.boyuan.stockmentor.market.stock.model.DelayedPriceFreshnessStatus;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.market.stock.service.DelayedMarketPriceService;
import net.boyuan.stockmentor.papertrading.repository.PaperPositionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradeTransactionRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradingAccountRepository;
import net.boyuan.stockmentor.papertrading.model.PaperTradeSide;
import net.boyuan.stockmentor.userbehavior.entity.UserBehaviorProfile;
import net.boyuan.stockmentor.userbehavior.repository.UserBehaviorProfileRepository;
import net.boyuan.stockmentor.userprofile.model.BehaviorConfidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaperTradingControllerSecurityTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private PaperTradingAccountRepository accountRepository;
    @Autowired
    private PaperPositionRepository positionRepository;
    @Autowired
    private PaperTradeTransactionRepository transactionRepository;
    @Autowired
    private UserBehaviorProfileRepository behaviorProfileRepository;
    @MockitoBean
    private DelayedMarketPriceService delayedMarketPriceService;

    private AppUser authUser;
    private AppUser otherUser;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        positionRepository.deleteAll();
        accountRepository.deleteAll();
        behaviorProfileRepository.deleteAll();
        authUser = ensureUser("paper-auth@example.com", "paper-auth");
        otherUser = ensureUser("paper-other@example.com", "paper-other");
        ensureStock("MSFT", "100.00");
        ensureStock("KO", "83.50");
        ensureStock("JNJ", "239.88");
        when(delayedMarketPriceService.resolveForDisplay(anyString()))
                .thenAnswer(invocation -> delayedPrice(invocation.getArgument(0)));
    }

    @Test
    void unauthenticatedPaperTradingEndpointsRejectRequests() throws Exception {
        mockMvc.perform(get("/api/paper-trading/account")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/paper-trading/portfolio")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/paper-trading/transactions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/paper-trading/transactions/page")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/paper-trading/transactions/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/paper-trading/portfolio/reset")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/paper-trading/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/paper-trading/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanAccessOwnAccountPortfolioAndTransactions() throws Exception {
        mockMvc.perform(get("/api/paper-trading/account")
                        .with(httpBasic("paper-auth@example.com", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/paper-trading/portfolio")
                        .with(httpBasic("paper-auth@example.com", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions").isArray());

        mockMvc.perform(get("/api/paper-trading/transactions")
                        .with(httpBasic("paper-auth@example.com", "password")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/paper-trading/transactions/page")
                        .with(httpBasic("paper-auth@example.com", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void malformedJsonReturnsBadRequestWithClearMessage() throws Exception {
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{quantity:3,symbol:MSFT}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid JSON request body"));
    }

    @Test
    void invalidRequestBodyReturnsBadRequestWithValidationMessage() throws Exception {
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Quantity must be positive"));
    }

    @Test
    void fractionalQuantityReturnsBadRequestWithoutPartialTradeRows() throws Exception {
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"quantity\":1.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertTrue(accountRepository.findByUserUserId(authUser.getUserId()).isEmpty());
        assertTrue(positionRepository.findByUserUserId(authUser.getUserId()).isEmpty());
        assertTrue(transactionRepository.findTop50ByUserUserIdOrderByExecutedAtDesc(authUser.getUserId()).isEmpty());
        assertTrue(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(authUser.getUserId()).isEmpty());
    }

    @Test
    void nonNumericQuantityReturnsBadRequestWithoutPartialTradeRows() throws Exception {
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"quantity\":\"one\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertTrue(accountRepository.findByUserUserId(authUser.getUserId()).isEmpty());
        assertTrue(positionRepository.findByUserUserId(authUser.getUserId()).isEmpty());
        assertTrue(transactionRepository.findTop50ByUserUserIdOrderByExecutedAtDesc(authUser.getUserId()).isEmpty());
        assertTrue(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(authUser.getUserId()).isEmpty());
    }

    @Test
    void unsupportedSymbolReturnsBadRequestWithServiceMessageAndNoPartialTradeRows() throws Exception {
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"META\",\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported paper-trading symbol: META"));

        assertTrue(accountRepository.findByUserUserId(authUser.getUserId()).isEmpty());
        assertTrue(positionRepository.findByUserUserId(authUser.getUserId()).isEmpty());
        assertTrue(transactionRepository.findTop50ByUserUserIdOrderByExecutedAtDesc(authUser.getUserId()).isEmpty());
        assertTrue(behaviorProfileRepository.findTopByUserUserIdOrderByUpdatedAtDesc(authUser.getUserId()).isEmpty());
    }

    @Test
    void validBuyAndSellReturnOk() throws Exception {
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.side").value("BUY"))
                .andExpect(jsonPath("$.transaction.executionPrice").value(100.0000))
                .andExpect(jsonPath("$.delayedPriceMetadata.priceFreshnessStatus").value("DELAYED_15_MINUTES"))
                .andExpect(jsonPath("$.position.quantity").value(3));

        mockMvc.perform(post("/api/paper-trading/sell")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.side").value("SELL"))
                .andExpect(jsonPath("$.position.quantity").value(2));
    }

    @Test
    void successfulBuyCommitsBehaviorProfileWhenThresholdIsMet() throws Exception {
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"KO\",\"quantity\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"JNJ\",\"quantity\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"KO\",\"quantity\":1}"))
                .andExpect(status().isOk());

        UserBehaviorProfile profile = behaviorProfileRepository
                .findTopByUserUserIdOrderByUpdatedAtDesc(authUser.getUserId())
                .orElseThrow();

        assertEquals(BehaviorConfidence.MEDIUM, profile.getBehaviorConfidence());
        assertEquals("conservative", profile.getFavoriteRiskCategory());
        assertTrue(profile.getMostTradedSymbols().contains("KO"));
        assertTrue(profile.getMostTradedSymbols().contains("JNJ"));
        assertEquals(3, transactionRepository.findTop50ByUserUserIdOrderByExecutedAtDesc(authUser.getUserId()).size());
    }

    @Test
    void buyIgnoresFrontendProvidedUserId() throws Exception {
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + otherUser.getUserId() + ",\"symbol\":\"MSFT\",\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.symbol").value("MSFT"));

        assertTrue(accountRepository.findByUserUserId(authUser.getUserId()).isPresent());
        assertTrue(accountRepository.findByUserUserId(otherUser.getUserId()).isEmpty());
    }

    @Test
    void buyIgnoresFrontendProvidedPrice() throws Exception {
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"quantity\":1,\"price\":9999.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.executionPrice").value(100.0000))
                .andExpect(jsonPath("$.transaction.grossAmount").value(100.0000))
                .andExpect(jsonPath("$.delayedPriceMetadata.displayedPrice").value(100.00));
    }

    @Test
    void resetClearsOnlyCurrentUserPortfolioAndCreatesResetMarker() throws Exception {
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"quantity\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-other@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"quantity\":1}"))
                .andExpect(status().isOk());

        long behaviorProfileCount = behaviorProfileRepository.count();

        mockMvc.perform(post("/api/paper-trading/portfolio/reset")
                        .with(httpBasic("paper-auth@example.com", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(1000000.0000))
                .andExpect(jsonPath("$.startingCash").value(1000000.0000))
                .andExpect(jsonPath("$.currentSessionNumber").value(2))
                .andExpect(jsonPath("$.positions").isEmpty());

        assertTrue(positionRepository.findByUserUserId(authUser.getUserId()).isEmpty());
        assertTrue(!positionRepository.findByUserUserId(otherUser.getUserId()).isEmpty());
        assertTrue(transactionRepository.findTop50ByUserUserIdOrderByExecutedAtDesc(authUser.getUserId()).stream()
                .anyMatch(transaction -> transaction.getSide() == PaperTradeSide.RESET
                        && transaction.getSymbol() == null
                        && Boolean.TRUE.equals(transaction.getIsCurrentSession())));
        assertTrue(transactionRepository.findTop50ByUserUserIdOrderByExecutedAtDesc(authUser.getUserId()).stream()
                .anyMatch(transaction -> transaction.getSide() == PaperTradeSide.BUY
                        && Boolean.FALSE.equals(transaction.getIsCurrentSession())));
        assertTrue(behaviorProfileRepository.count() == behaviorProfileCount);
    }

    @Test
    void transactionDetailIsScopedToCurrentUserAndFiltersSupportReset() throws Exception {
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"quantity\":1}"))
                .andExpect(status().isOk());
        Long ownTransactionId = transactionRepository.findTop50ByUserUserIdOrderByExecutedAtDesc(authUser.getUserId())
                .get(0)
                .getTransactionId();

        mockMvc.perform(get("/api/paper-trading/transactions/" + ownTransactionId)
                        .with(httpBasic("paper-auth@example.com", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(ownTransactionId))
                .andExpect(jsonPath("$.fee").value(1.0000))
                .andExpect(jsonPath("$.netAmount").value(101.0000));

        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-other@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"quantity\":1}"))
                .andExpect(status().isOk());
        Long otherTransactionId = transactionRepository.findTop50ByUserUserIdOrderByExecutedAtDesc(otherUser.getUserId())
                .get(0)
                .getTransactionId();

        mockMvc.perform(get("/api/paper-trading/transactions/" + otherTransactionId)
                        .with(httpBasic("paper-auth@example.com", "password")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/paper-trading/portfolio/reset")
                        .with(httpBasic("paper-auth@example.com", "password")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/paper-trading/transactions?side=RESET&currentSessionOnly=true")
                        .with(httpBasic("paper-auth@example.com", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].side").value("RESET"))
                .andExpect(jsonPath("$[0].symbol").doesNotExist());
    }

    @Test
    void transactionPageIsScopedToCurrentUserAndDoesNotConflictWithDetailRoute() throws Exception {
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"quantity\":1}"))
                .andExpect(status().isOk());
        Long ownTransactionId = transactionRepository.findTop50ByUserUserIdOrderByExecutedAtDesc(authUser.getUserId())
                .get(0)
                .getTransactionId();

        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-other@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"KO\",\"quantity\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/paper-trading/transactions/page?userId=" + otherUser.getUserId())
                        .with(httpBasic("paper-auth@example.com", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions[0].symbol").value("MSFT"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/paper-trading/transactions/" + ownTransactionId)
                        .with(httpBasic("paper-auth@example.com", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(ownTransactionId));
    }

    @Test
    void transactionPageRejectsInvalidFilters() throws Exception {
        mockMvc.perform(get("/api/paper-trading/transactions/page?symbol=META")
                        .with(httpBasic("paper-auth@example.com", "password")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/paper-trading/transactions/page?side=DIVIDEND")
                        .with(httpBasic("paper-auth@example.com", "password")))
                .andExpect(status().isBadRequest());
    }

    private AppUser ensureUser(String email, String username) {
        return appUserRepository.findByEmailOrUsername(email, username)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    AppUser user = new AppUser();
                    user.setEmail(email);
                    user.setUsername(username);
                    user.setPasswordHash(passwordEncoder.encode("password"));
                    user.setRole(AppUserRole.BEGINNER_INVESTOR);
                    user.setStatus(AppUserStatus.ACTIVE);
                    user.setIsDeleted(false);
                    user.setOnboardingCompleted(true);
                    user.setCreatedAt(now);
                    user.setUpdatedAt(now);
                    return appUserRepository.save(user);
                });
    }

    private void ensureStock(String symbol, String price) {
        if (stockRepository.findBySymbolIn(List.of(symbol)).stream().findFirst().isPresent()) {
            return;
        }
        Stock stock = new Stock();
        stock.setSymbol(symbol);
        stock.setCompanyName("Microsoft");
        stock.setCurrentPrice(new BigDecimal(price));
        stock.setPercentChange(BigDecimal.ZERO);
        stock.setCreatedAt(LocalDateTime.now());
        stock.setUpdatedAt(LocalDateTime.now());
        stockRepository.save(stock);
    }

    private DelayedMarketPrice delayedPrice(String symbol) {
        BigDecimal price = switch (symbol) {
            case "KO" -> new BigDecimal("83.50");
            case "JNJ" -> new BigDecimal("239.88");
            default -> new BigDecimal("100.00");
        };
        return new DelayedMarketPrice(
                symbol,
                price,
                BigDecimal.ZERO,
                LocalDateTime.of(2026, 1, 5, 9, 45),
                LocalDateTime.of(2026, 1, 5, 9, 45),
                15,
                DelayedPriceFreshnessStatus.DELAYED_15_MINUTES,
                true,
                true,
                "Practice trades use StockMentor's delayed stored price, not a live market quote.",
                DelayedMarketPriceService.INTRADAY_PRICE_SOURCE,
                "America/New_York",
                LocalDateTime.of(2026, 1, 5, 10, 0),
                LocalDateTime.of(2026, 1, 5, 9, 45).toLocalDate()
        );
    }
}
