import { normalizeUnknownApiError } from '@/api/errors';
import type {
  PaperPositionResponse,
  PaperTradeSide,
  PaperTradeTransactionResponse,
} from '@/types/paper-trading';
import type { ApiNumber } from '@/types/stocks';
import {
  formatBackendDateTime,
  formatPercent,
  formatPrice,
  formatSignedCurrencyChange,
  labelize,
  normalizeStockSymbol,
  toNumber,
} from '@/utils/stock-display';

const quantityPattern = /^[1-9]\d*$/;

export const PAPER_TRADE_MAX_QUANTITY = Number.MAX_SAFE_INTEGER;

export function validatePaperTradeQuantity(
  rawQuantity: string,
  options: { maxHolding?: number | null; maxQuantity?: number } = {},
) {
  const trimmed = rawQuantity.trim();
  if (!quantityPattern.test(trimmed)) {
    return {
      message: 'Enter a positive whole-share quantity.',
      quantity: null,
    };
  }

  const quantity = Number(trimmed);
  const maxQuantity = options.maxQuantity ?? PAPER_TRADE_MAX_QUANTITY;

  if (!Number.isSafeInteger(quantity) || quantity > maxQuantity) {
    return {
      message: `Quantity must be ${maxQuantity.toLocaleString('en-US')} shares or fewer.`,
      quantity: null,
    };
  }

  if (options.maxHolding !== undefined && options.maxHolding !== null && quantity > options.maxHolding) {
    return {
      message: 'Sell quantity cannot exceed your current holding.',
      quantity: null,
    };
  }

  return { message: null, quantity };
}

export function formatPaperMoney(value: ApiNumber) {
  return formatPrice(value).replace('Price unavailable', 'Unavailable');
}

export function formatPaperPercent(value: ApiNumber) {
  return formatPercent(value).replace('Change unavailable', 'Unavailable');
}

export function formatSignedPaperMoney(value: ApiNumber) {
  return formatSignedCurrencyChange(value) ?? 'Unavailable';
}

export function formatPaperDateTime(value: string | null | undefined) {
  return formatBackendDateTime(value);
}

export function formatQuantity(value: number | null | undefined) {
  return value === null || value === undefined ? 'Unavailable' : value.toLocaleString('en-US');
}

export function getPositionSymbol(position: PaperPositionResponse | null | undefined) {
  return normalizeStockSymbol(position?.symbol);
}

export function getTransactionSideLabel(side: PaperTradeSide | null | undefined) {
  if (!side) {
    return 'Unavailable';
  }
  if (side === 'RESET') {
    return 'Portfolio reset';
  }
  return labelize(side);
}

export function isResetTransaction(transaction: PaperTradeTransactionResponse) {
  return transaction.side === 'RESET' || !transaction.symbol;
}

export function getTransactionDisplayTitle(transaction: PaperTradeTransactionResponse) {
  if (isResetTransaction(transaction)) {
    return 'Portfolio reset';
  }

  return `${getTransactionSideLabel(transaction.side)} ${normalizeStockSymbol(transaction.symbol)}`;
}

export function getTradeResultSummary(transaction: PaperTradeTransactionResponse | null | undefined) {
  if (!transaction) {
    return 'Practice trade completed.';
  }

  if (isResetTransaction(transaction)) {
    return 'Simulated portfolio reset.';
  }

  const side = getTransactionSideLabel(transaction.side).toLowerCase();
  const symbol = normalizeStockSymbol(transaction.symbol);
  return `${side} ${formatQuantity(transaction.quantity)} ${symbol} at ${formatPaperMoney(
    transaction.executionPrice ?? transaction.price,
  )}.`;
}

export function getSelectedStockTradeWarning(stock: {
  isPriceAvailable?: boolean | null;
  isTradeExecutable?: boolean | null;
}) {
  if (stock.isTradeExecutable === false || stock.isPriceAvailable === false) {
    return 'Backend delayed execution price may be unavailable, so this practice trade could be rejected.';
  }

  return null;
}

export function getPaperTradingApiErrorMessage(error: unknown, fallback: string) {
  const apiError = normalizeUnknownApiError(error);
  const message = apiError.message.toLowerCase();

  if (apiError.status === 401) {
    return 'Sign in again to continue practice trading.';
  }
  if (apiError.code === 'TIMEOUT') {
    return 'The backend did not respond in time. Try again after checking that it is running.';
  }
  if (apiError.code === 'NETWORK_ERROR' || apiError.status === 0) {
    return 'StockMentor could not reach the backend. Check the server and network connection.';
  }
  if (message.includes('insufficient') || message.includes('cash')) {
    return 'Not enough simulated cash for this practice trade.';
  }
  if (message.includes('unsupported') || message.includes('not supported')) {
    return 'This stock is not supported for practice trading.';
  }
  if (message.includes('no open paper position') || message.includes('no position')) {
    return 'No holding for this symbol.';
  }
  if (message.includes('exceeds held') || message.includes('exceed')) {
    return 'Sell quantity exceeds your current holding.';
  }
  if (message.includes('price') && (message.includes('unavailable') || message.includes('not available'))) {
    return 'Practice trade price is unavailable right now. Try again later.';
  }

  return apiError.message || fallback;
}

export function toPaperNumber(value: ApiNumber) {
  return toNumber(value);
}
