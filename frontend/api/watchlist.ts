import { apiRequest } from '@/api/client';
import type { BasicAuthCredentials } from '@/types/auth';
import type { WatchlistActionResponse, WatchlistResponse } from '@/types/stocks';
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
};
