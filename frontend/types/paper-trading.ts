import type { ApiNumber, DelayedStockFields } from '@/types/stocks';

export type PaperTradingAccountResponse = {
  accountId: number | null;
  cashBalance: ApiNumber;
  startingCash: ApiNumber;
  currentSessionNumber: number | null;
  lastResetAt: string | null;
  status: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type DelayedPriceMetadataResponse = DelayedStockFields & {
  lastBackendUpdatedAt?: string | null;
};

export type PaperPositionResponse = {
  positionId: number | null;
  symbol: string;
  companyName: string | null;
  quantity: number | null;
  averageCost: ApiNumber;
  totalCost: ApiNumber;
  investedCost: ApiNumber;
  currentPrice: ApiNumber;
  marketValue: ApiNumber;
  unrealizedProfitLoss: ApiNumber;
  unrealizedProfitLossPercent: ApiNumber;
  portfolioWeightPercent: ApiNumber;
  riskCategory: string | null;
  lastUpdated: string | null;
  valuationPrice: ApiNumber;
  valuationMarketValue: ApiNumber;
  valuationDataNote: string | null;
  delayedPriceMetadata: DelayedPriceMetadataResponse | null;
};

export type PaperPortfolioResponse = {
  userId: number | null;
  cashBalance: ApiNumber;
  startingCash: ApiNumber;
  totalInvestedCost: ApiNumber;
  estimatedMarketValue: ApiNumber;
  totalPortfolioValue: ApiNumber;
  unrealizedProfitLoss: ApiNumber;
  realizedProfitLoss: ApiNumber;
  realizedProfitLossAfterFees?: ApiNumber;
  totalProfitLoss?: ApiNumber;
  totalProfitLossPercent?: ApiNumber;
  todayOpenPositionProfitLoss?: ApiNumber;
  todayRealizedProfitLossAfterFees?: ApiNumber;
  todayProfitLoss?: ApiNumber;
  todayProfitLossPercent?: ApiNumber;
  todayProfitLossComplete?: boolean | null;
  todayProfitLossNote?: string | null;
  returnPercentage: ApiNumber;
  totalFeesPaid: ApiNumber;
  currentSessionNumber: number | null;
  lastResetAt: string | null;
  positions: PaperPositionResponse[];
  pricedPositionCount: number | null;
  unpricedPositionCount: number | null;
  portfolioValuationComplete: boolean | null;
  portfolioDataNote: string | null;
};

export type PaperTradeSide = 'BUY' | 'SELL' | 'RESET' | string;

export type PaperTradeTransactionResponse = {
  transactionId: number | null;
  symbol: string | null;
  side: PaperTradeSide | null;
  quantity: number | null;
  executionPrice: ApiNumber;
  price: ApiNumber;
  grossAmount: ApiNumber;
  fee: ApiNumber;
  netAmount: ApiNumber;
  totalAmount: ApiNumber;
  realizedProfitLoss: ApiNumber;
  realizedProfitLossAfterFees?: ApiNumber;
  cashBalanceAfter: ApiNumber;
  isCurrentSession: boolean | null;
  sessionNumber: number | null;
  executedAt: string | null;
  transactionTime: string | null;
};

export type PaperTradeExecutionResponse = {
  account: PaperTradingAccountResponse | null;
  position: PaperPositionResponse | null;
  transaction: PaperTradeTransactionResponse | null;
  delayedPriceMetadata: DelayedPriceMetadataResponse | null;
};

export type PaperTradeRequest = {
  symbol: string;
  quantity: number;
};

export type PaperTradeTransactionPageResponse = {
  transactions: PaperTradeTransactionResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
