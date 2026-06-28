export type ApiNumber = number | string | null;

export type StockTimeframe = '1D' | '5D' | '1M' | '3M' | 'YTD' | '1Y';
export type StockHistoryApiTimeframe = StockTimeframe | '7D';

export const STOCK_TIMEFRAMES: StockTimeframe[] = ['1D', '5D', '1M', '3M', 'YTD', '1Y'];

export type StockExplanationTimeframe = '1D' | '5D' | '1M' | '3M';

export const STOCK_EXPLANATION_TIMEFRAMES: StockExplanationTimeframe[] = ['1D', '5D', '1M', '3M'];

export type DelayedStockFields = {
  displayedPrice: ApiNumber;
  displayedPercentChange: ApiNumber;
  displayedMarketTime: string | null;
  targetDisplayMarketTime: string | null;
  dataDelayMinutes: number | null;
  priceFreshnessStatus: string | null;
  priceFreshnessLabel?: string | null;
  previousClose?: ApiNumber;
  displayedAbsoluteChange?: ApiNumber;
  isPriceAvailable: boolean | null;
  isTradeExecutable: boolean | null;
  dataNote: string | null;
  priceSource: string | null;
  marketTimeZone: string | null;
};

export type StockListItemResponse = DelayedStockFields & {
  stockId: number | null;
  symbol: string;
  companyName: string;
  currentPrice: ApiNumber;
  percentChange: ApiNumber;
  lastUpdated: string | null;
  isMarketOpen: boolean | null;
  timezone: string | null;
  source: string | null;
  riskCategory: string | null;
  baselineRiskCategory: string | null;
  trend: string | null;
  volatilityLabel: string | null;
  volumeTrend: string | null;
  priceConsistency: string | null;
  isFallback: boolean | null;
  missingDataCount: number | null;
  latestAnalysisSnapshotId: number | null;
  isWatchlisted: boolean | null;
  lastBackendUpdatedAt: string | null;
};

export type StockListResponse = {
  userId: number;
  stocks: StockListItemResponse[];
  message: string | null;
};

export type StockDetailResponse = StockListItemResponse & {
  highPrice: ApiNumber;
  lowPrice: ApiNumber;
  dataSource: string | null;
  analysisDataSource: string | null;
  snapshotHash: string | null;
  aiExplanationAvailable: boolean | null;
  aiExplanationEndpoint: string | null;
  tradeSupported: boolean | null;
  previousClose: ApiNumber;
  displayedAbsoluteChange: ApiNumber;
  displayedVolume: number | null;
  snapshotHighPrice: ApiNumber;
  snapshotLowPrice: ApiNumber;
  snapshotTimeframe: string | null;
};

export type StockHistoryPointResponse = {
  timestamp: string | null;
  tradingDate: string | null;
  price?: ApiNumber;
  open?: ApiNumber;
  high?: ApiNumber;
  low?: ApiNumber;
  close?: ApiNumber;
  openPrice: ApiNumber;
  highPrice: ApiNumber;
  lowPrice: ApiNumber;
  closePrice: ApiNumber;
  volume: number | null;
  source: string | null;
};

export type StockHistoryResponse = DelayedStockFields & {
  symbol: string;
  timeframe: StockHistoryApiTimeframe;
  previousClose?: ApiNumber;
  displayedAbsoluteChange?: ApiNumber;
  source: string | null;
  granularity?: 'INTRADAY_1MIN' | 'DAILY' | string | null;
  lineChartSupported?: boolean | null;
  candlestickSupported?: boolean | null;
  expectedPointCount?: number | null;
  actualPointCount?: number | null;
  missingDataCount?: number | null;
  includedTradingDays?: number | null;
  requestedTradingDays?: number | null;
  timezone?: string | null;
  dataSource?: string | null;
  isFallback?: boolean | null;
  completenessNote?: string | null;
  points: StockHistoryPointResponse[];
  message: string | null;
};

export type StockExplanationResponse = {
  symbol: string;
  timeframe: string;
  explanation: string | null;
  cached: boolean;
  available: boolean;
  analysisSnapshotId: number | null;
  dataStartDate: string | null;
  dataEndDate: string | null;
  dataSource: string | null;
  isFallback: boolean | null;
  baselineRiskCategory: string | null;
  riskCategory: string | null;
  message: string | null;
};

export type WatchlistStockResponse = DelayedStockFields & {
  stockId: number | null;
  symbol: string;
  companyName: string;
  displayOrder?: number | null;
  currentPrice: ApiNumber;
  percentChange: ApiNumber;
  trend: string | null;
  volatilityLabel: string | null;
  riskCategory: string | null;
  isWatchlisted: boolean | null;
  lastBackendUpdatedAt: string | null;
};

export type WatchlistResponse = {
  userId: number;
  watchlistedStocks: WatchlistStockResponse[];
  message: string | null;
};

export type WatchlistActionResponse = {
  message: string | null;
  changed: boolean | null;
  stock: WatchlistStockResponse | null;
};

export type WatchlistBatchRemoveResponse = {
  removedSymbols: string[];
  notFoundSymbols?: string[];
  ignoredSymbols?: string[];
  remainingWatchlistedStocks: WatchlistStockResponse[];
  message: string | null;
};
