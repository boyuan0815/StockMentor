package net.boyuan.stockmentor.papertrading.service;

import net.boyuan.stockmentor.papertrading.dto.*;

import java.util.List;

public interface PaperTradingService {
    PaperTradingAccountResponse getCurrentUserAccount();

    PaperPortfolioResponse getCurrentUserPortfolio();

    List<PaperTradeTransactionResponse> getCurrentUserTransactions();

    PaperTradeExecutionResponse buyForCurrentUser(PaperTradeRequest request);

    PaperTradeExecutionResponse sellForCurrentUser(PaperTradeRequest request);
}
