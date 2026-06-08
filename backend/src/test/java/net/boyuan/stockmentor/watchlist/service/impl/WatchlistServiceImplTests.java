package net.boyuan.stockmentor.watchlist.service.impl;

import net.boyuan.stockmentor.analysis.repository.StockAnalysisSnapshotRepository;
import net.boyuan.stockmentor.auth.entity.AppUser;
import net.boyuan.stockmentor.auth.model.AppUserRole;
import net.boyuan.stockmentor.auth.model.AppUserStatus;
import net.boyuan.stockmentor.auth.service.CurrentUserService;
import net.boyuan.stockmentor.market.stock.entity.Stock;
import net.boyuan.stockmentor.market.stock.repository.StockRepository;
import net.boyuan.stockmentor.watchlist.dto.WatchlistActionResponse;
import net.boyuan.stockmentor.watchlist.dto.WatchlistResponse;
import net.boyuan.stockmentor.watchlist.entity.UserWatchlist;
import net.boyuan.stockmentor.watchlist.repository.UserWatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceImplTests {
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private UserWatchlistRepository watchlistRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockAnalysisSnapshotRepository snapshotRepository;

    private WatchlistServiceImpl service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new WatchlistServiceImpl(currentUserService, watchlistRepository, stockRepository, snapshotRepository);
        user = new AppUser();
        user.setUserId(1L);
        user.setEmail("beginner@example.com");
        user.setUsername("beginner");
        user.setRole(AppUserRole.BEGINNER_INVESTOR);
        user.setStatus(AppUserStatus.ACTIVE);
        user.setIsDeleted(false);
        when(currentUserService.getCurrentUser()).thenReturn(user);
        lenient().when(snapshotRepository.findTopBySymbolAndTimeframeOrderByCreatedAtDesc(anyString(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void getWatchlistReturnsCurrentUserSymbolsOnly() {
        UserWatchlist watchlist = new UserWatchlist();
        watchlist.setUser(user);
        watchlist.setSymbol("MSFT");
        when(watchlistRepository.findByUserUserId(1L)).thenReturn(List.of(watchlist));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT")));

        WatchlistResponse response = service.getCurrentUserWatchlist();

        assertEquals(1L, response.userId());
        assertEquals(1, response.watchlistedStocks().size());
        assertEquals("MSFT", response.watchlistedStocks().get(0).symbol());
        verify(watchlistRepository).findByUserUserId(1L);
    }

    @Test
    void addSupportedSymbolSucceeds() {
        when(watchlistRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.empty());
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT")));

        WatchlistActionResponse response = service.addSymbolToCurrentUserWatchlist("msft");

        assertTrue(response.changed());
        assertTrue(response.stock().isWatchlisted());
        verify(watchlistRepository).save(any(UserWatchlist.class));
    }

    @Test
    void addDuplicateSymbolIsIdempotent() {
        UserWatchlist existing = new UserWatchlist();
        existing.setUser(user);
        existing.setSymbol("MSFT");
        when(watchlistRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.of(existing));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT")));

        WatchlistActionResponse response = service.addSymbolToCurrentUserWatchlist("MSFT");

        assertFalse(response.changed());
        assertTrue(response.stock().isWatchlisted());
        verify(watchlistRepository, never()).save(any(UserWatchlist.class));
    }

    @Test
    void unsupportedSymbolRejects() {
        assertThrows(IllegalArgumentException.class, () -> service.addSymbolToCurrentUserWatchlist("META"));
    }

    @Test
    void deleteExistingSymbolSucceeds() {
        UserWatchlist existing = new UserWatchlist();
        existing.setUser(user);
        existing.setSymbol("MSFT");
        when(watchlistRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.of(existing));
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT")));

        WatchlistActionResponse response = service.removeSymbolFromCurrentUserWatchlist("MSFT");

        assertTrue(response.changed());
        assertFalse(response.stock().isWatchlisted());
        verify(watchlistRepository).delete(existing);
    }

    @Test
    void deleteMissingSymbolIsFriendly() {
        when(watchlistRepository.findByUserUserIdAndSymbol(1L, "MSFT")).thenReturn(Optional.empty());
        when(stockRepository.findBySymbolIn(anyCollection())).thenReturn(List.of(stock("MSFT")));

        WatchlistActionResponse response = service.removeSymbolFromCurrentUserWatchlist("MSFT");

        assertFalse(response.changed());
        assertFalse(response.stock().isWatchlisted());
        verify(watchlistRepository, never()).delete(any(UserWatchlist.class));
    }

    private Stock stock(String symbol) {
        Stock stock = new Stock();
        stock.setStockId(5L);
        stock.setSymbol(symbol);
        stock.setCompanyName("Microsoft");
        stock.setCurrentPrice(BigDecimal.valueOf(428.05));
        stock.setPercentChange(BigDecimal.valueOf(3.35));
        return stock;
    }
}
