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
    public List<PaperTradeTransactionResponse> getTransactions(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Boolean currentSessionOnly
    ) {
        return paperTradingService.getCurrentUserTransactions(symbol, side, from, to, page, size, currentSessionOnly);
    }

    @GetMapping("/transactions/page")
    public PaperTradeTransactionPageResponse getTransactionsPage(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Boolean currentSessionOnly
    ) {
        return paperTradingService.getCurrentUserTransactionsPage(symbol, side, from, to, page, size, currentSessionOnly);
    }

    @GetMapping("/transactions/{transactionId:\\d+}")
    public PaperTradeTransactionResponse getTransaction(@PathVariable Long transactionId) {
        return paperTradingService.getCurrentUserTransaction(transactionId);
    }

    @PostMapping("/portfolio/reset")
    public PaperPortfolioResponse resetPortfolio() {
        return paperTradingService.resetCurrentUserPortfolio();
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
