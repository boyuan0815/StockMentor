import { apiRequest } from '@/api/client';
import type { BasicAuthCredentials } from '@/types/auth';
import type { StockAiSuggestionResponse } from '@/types/ai-suggestions';

const AI_SUGGESTION_REFRESH_TIMEOUT_MS = 90_000;

export const aiSuggestionsApi = {
  getSuggestions(credentials: BasicAuthCredentials) {
    return apiRequest<StockAiSuggestionResponse>('/api/stocks/ai-suggestions', {
      credentials,
    });
  },

  refreshSuggestions(credentials: BasicAuthCredentials) {
    return apiRequest<StockAiSuggestionResponse>('/api/stocks/ai-suggestions/refresh', {
      credentials,
      method: 'POST',
      timeoutMs: AI_SUGGESTION_REFRESH_TIMEOUT_MS,
    });
  },

  dismissSuggestion(credentials: BasicAuthCredentials, itemId: number) {
    return apiRequest<StockAiSuggestionResponse>(
      `/api/stocks/ai-suggestions/items/${itemId}/dismiss`,
      {
        credentials,
        method: 'PATCH',
      },
    );
  },

  watchlistSuggestion(credentials: BasicAuthCredentials, itemId: number) {
    return apiRequest<StockAiSuggestionResponse>(
      `/api/stocks/ai-suggestions/items/${itemId}/watchlist`,
      {
        credentials,
        method: 'PATCH',
      },
    );
  },
};
