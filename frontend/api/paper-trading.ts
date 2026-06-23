import { apiRequest } from '@/api/client';
import type { BasicAuthCredentials } from '@/types/auth';
import type {
  PaperPortfolioResponse,
  PaperTradeExecutionResponse,
  PaperTradeRequest,
  PaperTradeTransactionResponse,
  PaperTradingAccountResponse,
} from '@/types/paper-trading';
import { normalizeStockSymbol } from '@/utils/stock-display';

type TransactionFilters = {
  currentSessionOnly?: boolean;
  side?: string | null;
  size?: number;
  symbol?: string | null;
};

export const paperTradingApi = {
  getAccount(credentials: BasicAuthCredentials) {
    return apiRequest<PaperTradingAccountResponse>('/api/paper-trading/account', {
      credentials,
    });
  },

  getPortfolio(credentials: BasicAuthCredentials) {
    return apiRequest<PaperPortfolioResponse>('/api/paper-trading/portfolio', {
      credentials,
    });
  },

  resetPortfolio(credentials: BasicAuthCredentials) {
    return apiRequest<PaperPortfolioResponse>('/api/paper-trading/portfolio/reset', {
      credentials,
      method: 'POST',
    });
  },

  buy(credentials: BasicAuthCredentials, request: PaperTradeRequest) {
    return apiRequest<PaperTradeExecutionResponse>('/api/paper-trading/buy', {
      body: {
        quantity: request.quantity,
        symbol: normalizeStockSymbol(request.symbol),
      },
      credentials,
      method: 'POST',
    });
  },

  sell(credentials: BasicAuthCredentials, request: PaperTradeRequest) {
    return apiRequest<PaperTradeExecutionResponse>('/api/paper-trading/sell', {
      body: {
        quantity: request.quantity,
        symbol: normalizeStockSymbol(request.symbol),
      },
      credentials,
      method: 'POST',
    });
  },

  getTransactions(credentials: BasicAuthCredentials, filters: TransactionFilters = {}) {
    const params = new URLSearchParams();
    params.set('size', String(filters.size ?? 50));

    if (filters.currentSessionOnly !== undefined) {
      params.set('currentSessionOnly', String(filters.currentSessionOnly));
    }
    if (filters.side) {
      params.set('side', filters.side);
    }
    if (filters.symbol) {
      params.set('symbol', normalizeStockSymbol(filters.symbol));
    }

    return apiRequest<PaperTradeTransactionResponse[]>(
      `/api/paper-trading/transactions?${params.toString()}`,
      {
        credentials,
      },
    );
  },

  getTransaction(credentials: BasicAuthCredentials, transactionId: string | number) {
    return apiRequest<PaperTradeTransactionResponse>(
      `/api/paper-trading/transactions/${transactionId}`,
      {
        credentials,
      },
    );
  },
};
