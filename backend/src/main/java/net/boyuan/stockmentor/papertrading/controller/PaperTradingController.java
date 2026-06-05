package net.boyuan.stockmentor.papertrading.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.papertrading.dto.*;
import net.boyuan.stockmentor.papertrading.service.PaperTradingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/paper-trading")
public class PaperTradingController {
    private final PaperTradingService paperTradingService;

    @GetMapping("/account")
    public PaperTradingAccountResponse getAccount() {
        return paperTradingService.getCurrentUserAccount();
    }

    @GetMapping("/portfolio")
    public PaperPortfolioResponse getPortfolio() {
        return paperTradingService.getCurrentUserPortfolio();
    }

    @GetMapping("/transactions")
    public List<PaperTradeTransactionResponse> getTransactions() {
        return paperTradingService.getCurrentUserTransactions();
    }

    @PostMapping("/buy")
    public PaperTradeExecutionResponse buy(@Valid @RequestBody PaperTradeRequest request) {
        return paperTradingService.buyForCurrentUser(request);
    }

    @PostMapping("/sell")
    public PaperTradeExecutionResponse sell(@Valid @RequestBody PaperTradeRequest request) {
        return paperTradingService.sellForCurrentUser(request);
    }
}
