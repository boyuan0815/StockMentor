import type { ApiNumber } from '@/types/stocks';

export type TextHighlightSegmentResponse = {
  startIndex: number;
  endIndex: number;
  style: 'positive' | 'negative' | 'emphasis' | string;
};

export type DelayedPriceMetadataResponse = {
  displayedPrice: ApiNumber;
  displayedPercentChange: ApiNumber;
  displayedMarketTime: string | null;
  targetDisplayMarketTime: string | null;
  dataDelayMinutes: number | null;
  priceFreshnessStatus: string | null;
  priceFreshnessLabel: string | null;
  isPriceAvailable: boolean | null;
  isTradeExecutable: boolean | null;
  dataNote: string | null;
  priceSource: string | null;
  marketTimeZone: string | null;
  lastBackendUpdatedAt: string | null;
  previousClose: ApiNumber;
  displayedAbsoluteChange: ApiNumber;
};

export type AiSuggestionQuoteFields = {
  currentPrice: ApiNumber;
  percentChange: ApiNumber;
  displayedPrice: ApiNumber;
  displayedAbsoluteChange: ApiNumber;
  displayedPercentChange: ApiNumber;
  previousClose: ApiNumber;
  priceFreshnessStatus: string | null;
  priceFreshnessLabel: string | null;
  delayedPriceMetadata: DelayedPriceMetadataResponse | null;
  displayDataSource: string | null;
  displayedMarketTime: string | null;
};

export type SuggestedStockResponse = AiSuggestionQuoteFields & {
  itemId: number;
  stockId: number | null;
  symbol: string;
  companyName: string;
  rankNo: number | null;
  matchScore: number | null;
  riskLevel: string | null;
  suggestionLabel: string | null;
  shortReason: string | null;
  detailReason: string | null;
  shortReasonHighlights?: TextHighlightSegmentResponse[] | null;
  detailReasonHighlights?: TextHighlightSegmentResponse[] | null;
  status: string | null;
  snapshotId: number | null;
  trend: string | null;
  volatilityLabel: string | null;
  volumeTrend: string | null;
  priceConsistency: string | null;
  isFallback: boolean | null;
  missingDataCount: number | null;
  isWatchlisted: boolean | null;
};

export type RemainingStockResponse = AiSuggestionQuoteFields & {
  stockId: number | null;
  symbol: string;
  companyName: string;
  trend: string | null;
  volatilityLabel: string | null;
  riskCategory: string | null;
  isSuggested: boolean | null;
  isWatchlisted: boolean | null;
};

export type StockAiSuggestionResponse = {
  userId: number;
  batchId: number | null;
  batchStatus: string | null;
  triggerReason: string | null;
  batchSummary: string | null;
  analysisTimeframe: string | null;
  generatedAt: string | null;
  expiresAt: string | null;
  fallbackUsed: boolean | null;
  refreshAllowed: boolean | null;
  nextRefreshAllowedAt: string | null;
  suggestedStocks: SuggestedStockResponse[];
  remainingStocks: RemainingStockResponse[];
  message: string | null;
};
