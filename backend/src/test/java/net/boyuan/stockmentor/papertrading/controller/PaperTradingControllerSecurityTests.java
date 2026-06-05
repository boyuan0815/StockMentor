package net.boyuan.stockmentor.papertrading.controller;

import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.repository.AppUserRepository;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.papertrading.repository.PaperTradingAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private AppUser authUser;
    private AppUser otherUser;

    @BeforeEach
    void setUp() {
        authUser = ensureUser("paper-auth@example.com", "paper-auth");
        otherUser = ensureUser("paper-other@example.com", "paper-other");
        ensureStock("MSFT", "100.00");
    }

    @Test
    void unauthenticatedPaperTradingEndpointsRejectRequests() throws Exception {
        mockMvc.perform(get("/api/paper-trading/account")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/paper-trading/portfolio")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/paper-trading/transactions")).andExpect(status().isUnauthorized());
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
    }

    @Test
    void invalidRequestBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/paper-trading/buy")
                        .with(httpBasic("paper-auth@example.com", "password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"MSFT\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
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
}
