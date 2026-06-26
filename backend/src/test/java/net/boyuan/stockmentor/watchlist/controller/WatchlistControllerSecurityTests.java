package net.boyuan.stockmentor.watchlist.controller;

import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.model.DelayedMarketPrice;
import net.boyuan.stockmentor.market.stock.model.DelayedPriceFreshnessStatus;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.market.stock.service.DelayedMarketPriceService;
import net.boyuan.stockmentor.watchlist.entity.UserWatchlist;
import net.boyuan.stockmentor.watchlist.model.WatchlistSource;
import net.boyuan.stockmentor.watchlist.repository.UserWatchlistRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WatchlistControllerSecurityTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private UserWatchlistRepository watchlistRepository;
    @MockitoBean
    private DelayedMarketPriceService delayedMarketPriceService;

    private AppUser authUser;
    private AppUser otherUser;

    @BeforeEach
    void setUp() {
        watchlistRepository.deleteAll();
        authUser = ensureUser("watch-auth@example.com", "watch-auth");
        otherUser = ensureUser("watch-other@example.com", "watch-other");
        ensureStock("MSFT");
        ensureStock("AAPL");
        ensureStock("KO");
        when(delayedMarketPriceService.resolveForDisplay(anyString()))
                .thenAnswer(invocation -> delayedPrice(invocation.getArgument(0)));
    }

    @Test
    void unauthenticatedWatchlistEditEndpointsRejectRequests() throws Exception {
        mockMvc.perform(patch("/api/watchlist/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[\"MSFT\"]}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/watchlist/batch-remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[\"MSFT\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanReorderOnlyOwnedWatchlistWithoutAdminToken() throws Exception {
        watchlist(authUser, "MSFT", 0);
        watchlist(authUser, "AAPL", 1);
        watchlist(otherUser, "KO", 0);

        mockMvc.perform(patch("/api/watchlist/reorder")
                        .with(httpBasic("watch-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + otherUser.getUserId() + ",\"symbols\":[\"AAPL\",\"MSFT\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watchlistedStocks[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.watchlistedStocks[1].symbol").value("MSFT"));

        List<UserWatchlist> authRows = watchlistRepository
                .findByUserUserIdOrderByDisplayOrderAscCreatedAtAscWatchlistIdAsc(authUser.getUserId());
        assertEquals(List.of("AAPL", "MSFT"), authRows.stream().map(UserWatchlist::getSymbol).toList());
        assertTrue(watchlistRepository.existsByUserUserIdAndSymbol(otherUser.getUserId(), "KO"));
    }

    @Test
    void reorderRejectsDuplicateUnsupportedUnownedOrMissingSymbols() throws Exception {
        watchlist(authUser, "MSFT", 0);
        watchlist(authUser, "AAPL", 1);
        watchlist(otherUser, "KO", 0);

        mockMvc.perform(patch("/api/watchlist/reorder")
                        .with(httpBasic("watch-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[\"MSFT\",\"MSFT\"]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/watchlist/reorder")
                        .with(httpBasic("watch-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[\"MSFT\",\"META\"]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/watchlist/reorder")
                        .with(httpBasic("watch-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[\"MSFT\",\"KO\"]}"))
                .andExpect(status().isBadRequest());

        List<UserWatchlist> authRows = watchlistRepository
                .findByUserUserIdOrderByDisplayOrderAscCreatedAtAscWatchlistIdAsc(authUser.getUserId());
        assertEquals(List.of("MSFT", "AAPL"), authRows.stream().map(UserWatchlist::getSymbol).toList());
    }

    @Test
    void batchRemoveReturnsRemovedNotFoundAndRemainingSymbols() throws Exception {
        watchlist(authUser, "MSFT", 0);
        watchlist(authUser, "AAPL", 1);
        watchlist(otherUser, "KO", 0);

        mockMvc.perform(post("/api/watchlist/batch-remove")
                        .with(httpBasic("watch-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[\"MSFT\",\"KO\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removedSymbols[0]").value("MSFT"))
                .andExpect(jsonPath("$.notFoundSymbols[0]").value("KO"))
                .andExpect(jsonPath("$.remainingWatchlistedStocks[0].symbol").value("AAPL"));

        assertTrue(watchlistRepository.existsByUserUserIdAndSymbol(otherUser.getUserId(), "KO"));
    }

    @Test
    void batchRemoveUnsupportedSymbolReturnsBadRequestAndRemovesNothing() throws Exception {
        watchlist(authUser, "MSFT", 0);

        mockMvc.perform(post("/api/watchlist/batch-remove")
                        .with(httpBasic("watch-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\":[\"META\"]}"))
                .andExpect(status().isBadRequest());

        assertTrue(watchlistRepository.existsByUserUserIdAndSymbol(authUser.getUserId(), "MSFT"));
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

    private void ensureStock(String symbol) {
        if (stockRepository.findBySymbolIn(List.of(symbol)).stream().findFirst().isPresent()) {
            return;
        }
        Stock stock = new Stock();
        stock.setSymbol(symbol);
        stock.setCompanyName(symbol + " Inc");
        stock.setCurrentPrice(new BigDecimal("100.00"));
        stock.setPercentChange(BigDecimal.ZERO);
        stock.setCreatedAt(LocalDateTime.now());
        stock.setUpdatedAt(LocalDateTime.now());
        stockRepository.save(stock);
    }

    private void watchlist(AppUser user, String symbol, int displayOrder) {
        UserWatchlist row = new UserWatchlist();
        row.setUser(user);
        row.setSymbol(symbol);
        row.setDisplayOrder(displayOrder);
        row.setSource(WatchlistSource.MANUAL);
        row.setCreatedAt(LocalDateTime.now().plusSeconds(displayOrder));
        row.setUpdatedAt(row.getCreatedAt());
        watchlistRepository.save(row);
    }

    private DelayedMarketPrice delayedPrice(String symbol) {
        return new DelayedMarketPrice(
                symbol,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                LocalDateTime.of(2026, 1, 5, 9, 45),
                LocalDateTime.of(2026, 1, 5, 9, 45),
                15,
                DelayedPriceFreshnessStatus.DELAYED_15_MINUTES,
                true,
                true,
                "15-minute delayed educational market data",
                DelayedMarketPriceService.INTRADAY_PRICE_SOURCE,
                "America/New_York",
                LocalDateTime.of(2026, 1, 5, 10, 0),
                LocalDate.of(2026, 1, 5),
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                DelayedPriceFreshnessStatus.DELAYED_15_MINUTES.label()
        );
    }
}
