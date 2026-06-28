import { apiRequest } from '@/api/client';
import type { BasicAuthCredentials } from '@/types/auth';
import type {
  WatchlistActionResponse,
  WatchlistBatchRemoveResponse,
  WatchlistResponse,
} from '@/types/stocks';
import { normalizeStockSymbol } from '@/utils/stock-display';

export const watchlistApi = {
  getWatchlist(credentials: BasicAuthCredentials) {
    return apiRequest<WatchlistResponse>('/api/watchlist', {
      credentials,
    });
  },

  addSymbol(credentials: BasicAuthCredentials, symbol: string) {
    return apiRequest<WatchlistActionResponse>(`/api/watchlist/${normalizeStockSymbol(symbol)}`, {
      method: 'POST',
      credentials,
    });
  },

  removeSymbol(credentials: BasicAuthCredentials, symbol: string) {
    return apiRequest<WatchlistActionResponse>(`/api/watchlist/${normalizeStockSymbol(symbol)}`, {
      method: 'DELETE',
      credentials,
    });
  },

  reorderSymbols(credentials: BasicAuthCredentials, symbols: string[]) {
    return apiRequest<WatchlistResponse>('/api/watchlist/reorder', {
      body: {
        symbols: symbols.map((symbol) => normalizeStockSymbol(symbol)),
      },
      method: 'PATCH',
      credentials,
    });
  },

  batchRemoveSymbols(credentials: BasicAuthCredentials, symbols: string[]) {
    return apiRequest<WatchlistBatchRemoveResponse>('/api/watchlist/batch-remove', {
      body: {
        symbols: symbols.map((symbol) => normalizeStockSymbol(symbol)),
      },
      method: 'POST',
      credentials,
    });
  },
};
