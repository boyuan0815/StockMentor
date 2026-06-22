import { normalizeUnknownApiError } from '@/api/errors';
import type {
  ApiNumber,
  DelayedStockFields,
  StockExplanationTimeframe,
  StockHistoryPointResponse,
  StockListItemResponse,
  StockTimeframe,
} from '@/types/stocks';

const moneyFormatter = new Intl.NumberFormat('en-US', {
  currency: 'USD',
  maximumFractionDigits: 2,
  minimumFractionDigits: 2,
  style: 'currency',
});

const integerFormatter = new Intl.NumberFormat('en-US', {
  maximumFractionDigits: 0,
});

export function normalizeStockSymbol(symbol: string | string[] | null | undefined) {
  const rawSymbol = Array.isArray(symbol) ? symbol[0] : symbol;
  return (rawSymbol ?? '').trim().toUpperCase();
}

export function toNumber(value: ApiNumber) {
  if (value === null || value === undefined || value === '') {
    return null;
  }

  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

export function formatPrice(value: ApiNumber) {
  const parsed = toNumber(value);
  return parsed === null ? 'Price unavailable' : moneyFormatter.format(parsed);
}

export function formatPlainNumber(value: ApiNumber) {
  const parsed = toNumber(value);
  return parsed === null ? 'Unavailable' : parsed.toFixed(2);
}

export function formatPercent(value: ApiNumber) {
  const parsed = toNumber(value);
  if (parsed === null) {
    return 'Change unavailable';
  }

  const sign = parsed > 0 ? '+' : '';
  return `${sign}${parsed.toFixed(2)}%`;
}

export function formatSignedCurrencyChange(value: ApiNumber) {
  const parsed = toNumber(value);
  if (parsed === null) {
    return null;
  }

  const sign = parsed > 0 ? '+' : parsed < 0 ? '-' : '';
  return `${sign}${moneyFormatter.format(Math.abs(parsed))}`;
}

export function formatBackendDateTime(value: string | null | undefined) {
  if (!value) {
    return 'Unavailable';
  }

  const normalized = value.replace('T', ' ');
  const withoutFraction = normalized.replace(/\.\d+$/, '');
  return withoutFraction.length > 16 ? withoutFraction.slice(0, 16) : withoutFraction;
}

export function formatBackendTime(value: string | null | undefined) {
  const formatted = formatBackendDateTime(value);
  return formatted === 'Unavailable' ? formatted : formatted.slice(-5);
}

export function formatVolume(value: number | null | undefined) {
  return value === null || value === undefined ? 'Volume unavailable' : integerFormatter.format(value);
}

export function labelize(value: string | null | undefined) {
  if (!value) {
    return 'Unavailable';
  }

  return value
    .replace(/_/g, ' ')
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export function getMovementTone(value: ApiNumber) {
  const parsed = toNumber(value);
  if (parsed === null || parsed === 0) {
    return 'neutral';
  }

  return parsed > 0 ? 'positive' : 'negative';
}

export function getMovementColor(value: ApiNumber) {
  const tone = getMovementTone(value);
  if (tone === 'positive') {
    return '#166534';
  }
  if (tone === 'negative') {
    return '#B91C1C';
  }
  return '#64748B';
}

export function getPreferredPrice(stock: StockListItemResponse | DelayedStockFields) {
  if ('currentPrice' in stock && stock.displayedPrice === null) {
    return stock.currentPrice;
  }

  return stock.displayedPrice;
}

export function getPreferredPercentChange(stock: StockListItemResponse | DelayedStockFields) {
  if ('percentChange' in stock && stock.displayedPercentChange === null) {
    return stock.percentChange;
  }

  return stock.displayedPercentChange;
}

export function getStockSourceLabel(stock: StockListItemResponse | DelayedStockFields) {
  if (stock.priceSource) {
    return labelize(stock.priceSource);
  }

  if ('source' in stock && stock.source) {
    return labelize(stock.source);
  }

  return 'Stored backend data';
}

export function getFreshnessLabel(stock: StockListItemResponse | DelayedStockFields) {
  return stock.priceFreshnessStatus ? labelize(stock.priceFreshnessStatus) : 'Stored data';
}

export function getMarketNoticeCopy(stocks: Array<DelayedStockFields | StockListItemResponse>) {
  const stock = stocks.find((item) => item.priceFreshnessStatus || item.dataNote) ?? stocks[0];
  const status = stock?.priceFreshnessStatus ?? null;
  const compatibilityPrice =
    stock && 'currentPrice' in stock ? (stock as StockListItemResponse).currentPrice : null;
  const hasDisplayedPrice =
    toNumber(stock?.displayedPrice ?? null) !== null ||
    toNumber(compatibilityPrice) !== null;

  switch (status) {
    case 'AVAILABLE':
      return {
        label:
          'US market is open. Prices are 15-minute delayed for learning and paper-trading practice.',
      };
    case 'NOT_READY':
    case 'NOT_READY_WITH_DAILY_FALLBACK':
      if (hasDisplayedPrice) {
        return {
          label:
            'US market has not opened yet. Latest stored prices are shown for learning and paper-trading practice.',
        };
      }
      return {
        label: 'Delayed prices are not ready yet. Pull to refresh later.',
      };
    case 'MARKET_CLOSED':
    case 'MARKET_CLOSED_PENDING_DAILY_CLOSE':
    case 'FALLBACK_DAILY':
      return {
        label:
          'US market is closed. Latest stored prices are shown for learning and paper-trading practice.',
      };
    case 'STALE':
    case 'UNAVAILABLE':
      return {
        label: 'Market data is temporarily unavailable. Pull to refresh later.',
      };
    default:
      return {
        label: 'US market data is delayed for education.',
      };
  }
}

export function getMarketStatusLabel(stock: DelayedStockFields | StockListItemResponse) {
  const compatibilityPrice =
    'currentPrice' in stock ? (stock as StockListItemResponse).currentPrice : null;
  const hasDisplayedPrice =
    toNumber(stock.displayedPrice ?? null) !== null ||
    toNumber(compatibilityPrice) !== null;

  switch (stock.priceFreshnessStatus) {
    case 'AVAILABLE':
      return 'Market Open';
    case 'NOT_READY':
    case 'NOT_READY_WITH_DAILY_FALLBACK':
      return hasDisplayedPrice ? 'Latest Stored Prices' : 'Price Pending';
    case 'MARKET_CLOSED':
    case 'MARKET_CLOSED_PENDING_DAILY_CLOSE':
    case 'FALLBACK_DAILY':
      return 'Market Closed';
    case 'STALE':
    case 'UNAVAILABLE':
      return 'Data Unavailable';
    default:
      return 'Delayed Data';
  }
}

export function getMarketStatusWithTime(stock: DelayedStockFields | StockListItemResponse) {
  const label = getMarketStatusLabel(stock);
  const time = formatBackendDateTime(stock.displayedMarketTime ?? stock.targetDisplayMarketTime);

  return time === 'Unavailable' ? label : `${label} ${time} ET`;
}

export function isAiExplanationTimeframe(
  timeframe: StockTimeframe,
): timeframe is StockExplanationTimeframe {
  return timeframe === '1D' || timeframe === '7D' || timeframe === '1M' || timeframe === '3M';
}

export function getPriceAvailabilityCopy(stock: StockListItemResponse | DelayedStockFields) {
  if (stock.isPriceAvailable === false) {
    return 'Delayed price is not ready yet.';
  }

  if (toNumber(stock.displayedPrice) !== null) {
    return '15-minute delayed educational market data.';
  }

  if ('currentPrice' in stock && toNumber(stock.currentPrice) !== null) {
    return 'Showing latest stored compatibility price because delayed display metadata is unavailable.';
  }

  return 'No stored price is available yet.';
}

export function getStockApiErrorMessage(error: unknown, fallback: string) {
  const apiError = normalizeUnknownApiError(error);

  if (apiError.code === 'MISSING_BASE_URL' || apiError.code === 'INVALID_BASE_URL') {
    return 'StockMentor does not have a valid backend address configured. Check EXPO_PUBLIC_API_BASE_URL and restart Expo.';
  }

  if (apiError.code === 'TIMEOUT') {
    return 'The backend did not respond in time. Try again after checking that the backend is running.';
  }

  if (apiError.code === 'NETWORK_ERROR' || apiError.status === 0) {
    return 'StockMentor could not reach the backend. Check the backend server and network connection, then try again.';
  }

  if (apiError.status === 401) {
    return 'Your session could not be verified. Sign in again to continue.';
  }

  if (apiError.status === 403) {
    return 'This account does not have access to stock learning screens.';
  }

  if (apiError.status === 404) {
    return 'StockMentor could not find that stock in the supported learning list.';
  }

  return apiError.message || fallback;
}

export function getHistoryPointLabel(point: StockHistoryPointResponse) {
  if (point.timestamp) {
    return formatBackendDateTime(point.timestamp);
  }

  return point.tradingDate ?? 'Date unavailable';
}
