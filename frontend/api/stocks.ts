import { apiRequest } from '@/api/client';
import type { BasicAuthCredentials } from '@/types/auth';
import type {
  StockDetailResponse,
  StockExplanationResponse,
  StockExplanationTimeframe,
  StockHistoryResponse,
  StockListResponse,
  StockTimeframe,
} from '@/types/stocks';
import { normalizeStockSymbol } from '@/utils/stock-display';

export const stocksApi = {
  getStocks(credentials: BasicAuthCredentials) {
    return apiRequest<StockListResponse>('/api/stocks', {
      credentials,
    });
  },

  getStockDetail(credentials: BasicAuthCredentials, symbol: string) {
    return apiRequest<StockDetailResponse>(`/api/stocks/${normalizeStockSymbol(symbol)}`, {
      credentials,
    });
  },

  getStockHistory(
    credentials: BasicAuthCredentials,
    symbol: string,
    timeframe: StockTimeframe,
  ) {
    return apiRequest<StockHistoryResponse>(
      `/api/stocks/${normalizeStockSymbol(symbol)}/history?timeframe=${timeframe}`,
      {
        credentials,
      },
    );
  },

  getStockExplanation(
    credentials: BasicAuthCredentials,
    symbol: string,
    timeframe: StockExplanationTimeframe = '5D',
  ) {
    return apiRequest<StockExplanationResponse>(
      `/api/stocks/${normalizeStockSymbol(symbol)}/ai-explanation?timeframe=${timeframe}`,
      {
        credentials,
      },
    );
  },
};
