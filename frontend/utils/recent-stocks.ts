import { safeGetItem, safeSetItem } from '@/utils/safe-storage';

const RECENT_VIEWED_STOCKS_KEY = 'stockmentor.recentViewedStocks.v1';
const MAX_RECENT_VIEWED_STOCKS = 3;

export async function loadRecentViewedStockSymbols() {
  try {
    const stored = await safeGetItem(RECENT_VIEWED_STOCKS_KEY);
    const parsed = stored ? JSON.parse(stored) : [];
    return Array.isArray(parsed)
      ? parsed.filter((item): item is string => typeof item === 'string').slice(0, MAX_RECENT_VIEWED_STOCKS)
      : [];
  } catch {
    return [];
  }
}

export async function saveRecentViewedStockSymbol(symbol: string) {
  try {
    const normalizedSymbol = symbol.trim().toUpperCase();
    if (!normalizedSymbol) {
      return;
    }

    const current = await loadRecentViewedStockSymbols();
    const next = [
      normalizedSymbol,
      ...current.filter((item) => item.toUpperCase() !== normalizedSymbol),
    ].slice(0, MAX_RECENT_VIEWED_STOCKS);
    await safeSetItem(RECENT_VIEWED_STOCKS_KEY, JSON.stringify(next));
  } catch {
    // Storage is best-effort only; viewing a stock must never crash the app.
  }
}
