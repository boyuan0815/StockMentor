package net.boyuan.stockmentor.papertrading.service;

import net.boyuan.stockmentor.papertrading.dto.*;

import java.util.List;

public interface PaperTradingService {
    PaperTradingAccountResponse getCurrentUserAccount();

    PaperPortfolioResponse getCurrentUserPortfolio();

    List<PaperTradeTransactionResponse> getCurrentUserTransactions(
            String symbol,
            String side,
            String from,
            String to,
            Integer page,
            Integer size,
            Boolean currentSessionOnly
    );

    PaperTradeTransactionPageResponse getCurrentUserTransactionsPage(
            String symbol,
            String side,
            String from,
            String to,
            Integer page,
            Integer size,
            Boolean currentSessionOnly
    );

    PaperTradeTransactionResponse getCurrentUserTransaction(Long transactionId);

    PaperPortfolioResponse resetCurrentUserPortfolio();

    PaperTradeExecutionResponse buyForCurrentUser(PaperTradeRequest request);

    PaperTradeExecutionResponse sellForCurrentUser(PaperTradeRequest request);
}
