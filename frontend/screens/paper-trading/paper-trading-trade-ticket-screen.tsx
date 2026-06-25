import { type Href, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { paperTradingApi } from '@/api/paper-trading';
import { stocksApi } from '@/api/stocks';
import { ActionButton } from '@/components/foundation/action-button';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import {
  ConfirmOverlay,
  FieldRow,
  InlineNotice,
  PaperHeader,
  PaperSection,
  PositionsTable,
} from '@/components/paper-trading/paper-trading-ui';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import { useMinuteBoundaryRefresh } from '@/hooks/use-minute-boundary-refresh';
import { useRefreshCooldown } from '@/hooks/use-refresh-cooldown';
import { useAuthSession } from '@/providers/auth-session-provider';
import { useToast } from '@/providers/toast-provider';
import type { BasicAuthCredentials } from '@/types/auth';
import type { PaperPortfolioResponse } from '@/types/paper-trading';
import type { StockDetailResponse } from '@/types/stocks';
import {
  formatPaperMoney,
  formatQuantity,
  getPaperTradingApiErrorMessage,
  getSelectedStockTradeWarning,
  toPaperNumber,
  validatePaperTradeQuantity,
} from '@/utils/paper-trading-display';
import {
  formatPercent,
  getMovementColor,
  getPreferredPercentChange,
  getPreferredPrice,
  normalizeStockSymbol,
} from '@/utils/stock-display';

type TradeDirection = 'BUY' | 'SELL';
type SellMode = 'PARTIAL' | 'ALL';
const PAPER_TRADE_FEE = 1;
const BUY_GREEN = '#00A862';

type PaperTradingTradeTicketScreenProps = {
  from?: string;
  initialDirection: TradeDirection;
  returnTo?: string;
  searchFrom?: string;
  searchSymbol?: string;
  symbol?: string;
};

export function PaperTradingTradeTicketScreen({
  from,
  initialDirection,
  returnTo,
  searchFrom,
  searchSymbol,
  symbol,
}: PaperTradingTradeTicketScreenProps) {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const guardedRefresh = useRefreshCooldown();
  const { credentials } = useAuthSession();
  const { showToast } = useToast();
  const requestIdRef = useRef(0);
  const inFlightRef = useRef(false);
  const hasLoadedRef = useRef(false);
  const routeSymbol = normalizeStockSymbol(symbol);
  const [stock, setStock] = useState<StockDetailResponse | null>(null);
  const [portfolio, setPortfolio] = useState<PaperPortfolioResponse | null>(null);
  const [direction, setDirection] = useState<TradeDirection>(initialDirection);
  const [sellMode, setSellMode] = useState<SellMode>('PARTIAL');
  const [quantity, setQuantity] = useState('');
  const [confirmQuantity, setConfirmQuantity] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const loadTicketData = useCallback(
    async (mode: 'soft' | 'refresh' = 'soft') => {
      if (inFlightRef.current || !routeSymbol) {
        setIsLoading(false);
        return;
      }

      const requestId = requestIdRef.current + 1;
      requestIdRef.current = requestId;

      if (!credentials) {
        setErrorMessage('Sign in again to load this paper trade ticket.');
        setIsLoading(false);
        setIsRefreshing(false);
        return;
      }

      inFlightRef.current = true;
      if (mode === 'refresh') {
        setIsRefreshing(true);
      } else if (!hasLoadedRef.current) {
        setIsLoading(true);
      }
      setErrorMessage(null);

      const [stockResult, portfolioResult] = await Promise.allSettled([
        stocksApi.getStockDetail(credentials, routeSymbol),
        paperTradingApi.getPortfolio(credentials),
      ]);

      if (requestIdRef.current !== requestId) {
        inFlightRef.current = false;
        return;
      }

      if (stockResult.status === 'fulfilled') {
        setStock(stockResult.value);
      } else {
        setErrorMessage(getPaperTradingApiErrorMessage(stockResult.reason, 'Selected stock could not be loaded.'));
      }

      if (portfolioResult.status === 'fulfilled') {
        setPortfolio(portfolioResult.value);
      } else {
        setErrorMessage(getPaperTradingApiErrorMessage(portfolioResult.reason, 'Portfolio could not be loaded.'));
      }

      hasLoadedRef.current = true;
      inFlightRef.current = false;
      setIsLoading(false);
      setIsRefreshing(false);
    },
    [credentials, routeSymbol],
  );

  const selectedPosition = useMemo(
    () => portfolio?.positions?.find((position) => position.symbol === routeSymbol) ?? null,
    [portfolio, routeSymbol],
  );
  const holdingQuantity = selectedPosition?.quantity ?? null;
  const hasHolding = holdingQuantity !== null && holdingQuantity > 0;
  const sellDisabled = !hasHolding;
  const singleShareSell = direction === 'SELL' && holdingQuantity === 1;

  useEffect(() => {
    setStock(null);
    setPortfolio(null);
    setConfirmQuantity(null);
    setSubmitError(null);
    setQuantity('1');
    setSellMode('PARTIAL');
    setDirection(initialDirection);
    hasLoadedRef.current = false;
    void loadTicketData('soft');
  }, [initialDirection, loadTicketData, routeSymbol]);

  useEffect(() => {
    if (initialDirection === 'SELL' && !isLoading) {
      setDirection(hasHolding ? 'SELL' : 'BUY');
    }
  }, [hasHolding, initialDirection, isLoading]);

  useMinuteBoundaryRefresh({
    enabled: Boolean(routeSymbol) && !confirmQuantity && !isSubmitting,
    onRefresh: () => loadTicketData('soft'),
  });

  const handleRefresh = () => {
    guardedRefresh(() => void loadTicketData('refresh'));
  };

  const handleBack = () => {
    if (from === 'detail' && routeSymbol) {
      router.replace({
        pathname: '/stocks/[symbol]',
        params: buildDetailParams(routeSymbol, returnTo, searchFrom, searchSymbol),
      } as Href);
      return;
    }
    if (from === 'stocks') {
      router.replace('/stocks' as Href);
      return;
    }
    router.replace('/paper-trading' as Href);
  };

  const handleQuantityChange = (value: string) => {
    setSubmitError(null);
    const digits = value.replace(/\D/g, '');
    const sanitized = digits.replace(/^0+/, '');

    if (direction === 'SELL' && sellMode === 'PARTIAL' && holdingQuantity !== null) {
      const trimmed = sanitized.trim();
      if (/^[1-9]\d*$/.test(trimmed)) {
        const nextQuantity = Number(trimmed);
        if (Number.isSafeInteger(nextQuantity) && nextQuantity >= holdingQuantity) {
          setQuantity(String(holdingQuantity));
          setSellMode('ALL');
          return;
        }
      }
    }

    setQuantity(sanitized);
  };

  const switchDirection = (nextDirection: TradeDirection) => {
    if (nextDirection === 'SELL' && sellDisabled) {
      showToast('You do not currently hold this stock.');
      return;
    }
    setDirection(nextDirection);
    setConfirmQuantity(null);
    setSubmitError(null);
    setQuantity('1');
    setSellMode('PARTIAL');
  };

  const switchSellMode = (nextMode: SellMode) => {
    if (nextMode === 'PARTIAL' && holdingQuantity === 1) {
      setSellMode('ALL');
      setQuantity('1');
      return;
    }
    setSellMode(nextMode);
    setConfirmQuantity(null);
    setSubmitError(null);
    setQuantity(nextMode === 'ALL' && holdingQuantity !== null ? String(holdingQuantity) : '1');
  };

  useEffect(() => {
    if (direction === 'SELL' && holdingQuantity === 1) {
      setSellMode('ALL');
      setQuantity('1');
    }
  }, [direction, holdingQuantity]);

  const adjustQuantity = (delta: 1 | -1) => {
    if (direction === 'SELL' && sellMode === 'ALL') {
      return;
    }
    const current = /^[1-9]\d*$/.test(quantity) ? Number(quantity) : 0;
    const next = Math.max(1, current + delta);
    handleQuantityChange(String(next));
  };

  const reviewTrade = () => {
    if (!routeSymbol) {
      return;
    }
    if (direction === 'SELL' && sellDisabled) {
      return;
    }

    const rawQuantity = direction === 'SELL' && sellMode === 'ALL' ? String(holdingQuantity ?? '') : quantity;
    const validation = validatePaperTradeQuantity(rawQuantity, {
      maxHolding: direction === 'SELL' ? holdingQuantity : undefined,
    });

    if (validation.message || validation.quantity === null) {
      return;
    }

    setConfirmQuantity(validation.quantity);
  };

  const submitTrade = async () => {
    if (!credentials || !confirmQuantity || !routeSymbol || isSubmitting) {
      return;
    }

    setIsSubmitting(true);
    try {
      if (direction === 'BUY') {
        await paperTradingApi.buy(credentials, { quantity: confirmQuantity, symbol: routeSymbol });
      } else {
        await paperTradingApi.sell(credentials, { quantity: confirmQuantity, symbol: routeSymbol });
      }
      setConfirmQuantity(null);
      setSubmitError(null);
      showToast('Order submitted.');
      await reloadPaperTradingReads(credentials);
      router.replace('/paper-trading?tab=history' as Href);
    } catch (error) {
      setSubmitError(getPaperTradingApiErrorMessage(error, `Paper ${direction.toLowerCase()} could not be completed.`));
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!routeSymbol) {
    return (
      <ScrollView
        contentContainerStyle={[
          styles.content,
          { paddingBottom: Math.max(Spacing.xxl, insets.bottom + Spacing.xl), paddingTop: insets.top + 2 },
        ]}
        style={styles.container}>
        <PaperHeader onBack={handleBack} title="Paper Trade" />
        <View style={styles.invalidWrap}>
          <EmptyState
            title="Select a stock first"
            description="Open Paper Trade from a stock row, stock detail page, or portfolio position."
          />
          <View style={styles.resultActions}>
            <ActionButton label="View Stocks" onPress={() => router.replace('/stocks' as Href)} />
            <ActionButton label="Back to Portfolio" onPress={() => router.replace('/paper-trading' as Href)} variant="ghost" />
          </View>
        </View>
      </ScrollView>
    );
  }

  const percentChange = stock ? getPreferredPercentChange(stock) : null;
  const movementColor = getMovementColor(percentChange);
  const tradeWarning = stock ? getSelectedStockTradeWarning(stock) : null;
  const portfolioPositions = portfolio?.positions ?? [];
  const priceNumber = stock ? toPaperNumber(getPreferredPrice(stock)) : null;
  const cashNumber = portfolio ? toPaperNumber(portfolio.cashBalance) : null;
  const activeQuantity =
    direction === 'SELL' && (sellMode === 'ALL' || singleShareSell) && holdingQuantity !== null
      ? holdingQuantity
      : /^[1-9]\d*$/.test(quantity)
        ? Number(quantity)
        : null;
  const estimatedAmount =
    priceNumber !== null && activeQuantity !== null ? formatPaperMoney(priceNumber * activeQuantity) : 'Unavailable';
  const grossAmountNumber = priceNumber !== null && activeQuantity !== null ? priceNumber * activeQuantity : null;
  const estimatedTicketAmount = formatTicketAmount(grossAmountNumber);
  const settlementAmountNumber =
    grossAmountNumber === null ? null : direction === 'BUY' ? grossAmountNumber + PAPER_TRADE_FEE : grossAmountNumber - PAPER_TRADE_FEE;
  const maxBuyQuantity =
    priceNumber !== null && priceNumber > 0 && cashNumber !== null
      ? Math.max(0, Math.floor((cashNumber - PAPER_TRADE_FEE) / priceNumber))
      : null;
  const remainingSellQuantity =
    direction === 'SELL' && holdingQuantity !== null && activeQuantity !== null
      ? Math.max(0, holdingQuantity - activeQuantity)
      : null;
  const ticketReady = Boolean(stock && portfolio);
  const quantityIsValid =
    validatePaperTradeQuantity(
      direction === 'SELL' && (sellMode === 'ALL' || singleShareSell) ? String(holdingQuantity ?? '') : quantity,
      { maxHolding: direction === 'SELL' ? holdingQuantity : undefined },
    ).quantity !== null;
  const maxQuantityLabel = direction === 'BUY' ? 'Max Qty to Buy (Cash):' : 'Max Qty to Sell:';
  const maxQuantityText =
    direction === 'BUY'
      ? maxBuyQuantity === null
        ? 'Unavailable'
        : maxBuyQuantity.toLocaleString('en-US')
      : formatQuantity(holdingQuantity);
  const quantityLocked = direction === 'SELL' && (sellMode === 'ALL' || singleShareSell);
  const estimatedTicketAmountLabel =
    estimatedTicketAmount === 'Unavailable' ? 'Unavailable' : `${estimatedTicketAmount} USD`;

  return (
    <ScrollView
      alwaysBounceVertical
      bounces
      contentContainerStyle={[
        styles.content,
        {
          paddingBottom: Math.max(Spacing.xxl, insets.bottom + Spacing.xl),
          paddingTop: insets.top + 2,
        },
      ]}
      contentInsetAdjustmentBehavior="never"
      keyboardShouldPersistTaps="handled"
      overScrollMode="never"
      refreshControl={<RefreshControl onRefresh={handleRefresh} refreshing={isRefreshing} />}
      style={styles.container}>
      <PaperHeader onBack={handleBack} onRefresh={handleRefresh} title="Paper Trade" />

      {errorMessage ? <ErrorBanner title="Paper trade needs attention" message={errorMessage} /> : null}
      {!ticketReady ? (
        <PaperSection title="Loading ticket">
          <SkeletonRows count={6} />
        </PaperSection>
      ) : null}

      {ticketReady && stock && portfolio ? (
        <PaperSection>
          <View style={styles.accountRow}>
            <View style={styles.accountIdentity}>
              <View style={styles.marketBadge}>
                <USFlagIcon />
              </View>
              <View style={styles.accountCopy}>
                <Text numberOfLines={1} selectable style={styles.accountLabel}>
                  Net Assets · USD
                </Text>
                <Text selectable style={styles.accountValue}>
                  {formatPaperMoney(portfolio.totalPortfolioValue)}
                </Text>
              </View>
            </View>
            <Pressable
              accessibilityLabel="View Portfolio"
              accessibilityRole="button"
              onPress={() => router.replace('/paper-trading' as Href)}
              style={({ pressed }) => [styles.viewLink, pressed ? styles.pressed : undefined]}>
              <Text style={styles.viewLinkText}>View</Text>
            </Pressable>
          </View>
          <View style={styles.quoteHeader}>
            <View style={styles.quoteIdentity}>
              <Text selectable style={styles.symbol}>{stock.symbol}</Text>
              <Text selectable numberOfLines={1} style={styles.company}>{stock.companyName}</Text>
            </View>
            <View style={styles.quoteNumbers}>
              <Text selectable numberOfLines={1} style={[styles.price, { color: movementColor }]}>{formatPaperMoney(getPreferredPrice(stock))}</Text>
              <Text selectable numberOfLines={1} style={[styles.percent, { color: movementColor }]}>{formatPercent(percentChange)}</Text>
            </View>
          </View>
          {tradeWarning ? <InlineNotice message={tradeWarning} tone="warn" /> : null}
        </PaperSection>
      ) : null}

      {ticketReady ? (
        <PaperSection>
          <FormRow label="Direction">
            <View style={styles.directionSwitch}>
              <SegmentButton active={direction === 'BUY'} label="Buy" onPress={() => switchDirection('BUY')} tone="buy" />
              <SegmentButton active={direction === 'SELL'} label="Sell" onPress={() => switchDirection('SELL')} tone="sell" />
            </View>
          </FormRow>
          <FormRow label="Order Type">
            <Text selectable style={styles.readOnlyValue}>Market</Text>
          </FormRow>
          {direction === 'SELL' ? (
            <FormRow label="Quantity mode">
              <View style={styles.modeSwitch}>
                <SegmentButton
                  active={sellMode === 'PARTIAL'}
                  disabled={singleShareSell}
                  label="Partial"
                  onPress={() => switchSellMode('PARTIAL')}
                  secondary
                  tone="neutral"
                />
                <SegmentButton
                  active={sellMode === 'ALL' || singleShareSell}
                  label="All"
                  onPress={() => switchSellMode('ALL')}
                  secondary
                  tone="neutral"
                />
              </View>
            </FormRow>
          ) : null}
          <FormRow label="Quantity">
            <View style={styles.quantityControl}>
              <TextInput
                accessibilityLabel={`${direction === 'BUY' ? 'Buy' : 'Sell'} quantity`}
                editable={!quantityLocked}
                keyboardType="number-pad"
                onChangeText={handleQuantityChange}
                placeholder="1"
                placeholderTextColor={Colors.light.mutedText}
                style={[
                  styles.quantityInput,
                  quantityLocked ? styles.quantityInputDisabled : undefined,
                ]}
                value={quantityLocked && holdingQuantity !== null ? String(holdingQuantity) : quantity}
              />
              <Pressable
                accessibilityLabel="Decrease quantity"
                accessibilityRole="button"
                disabled={quantityLocked}
                onPress={() => adjustQuantity(-1)}
                style={({ pressed }) => [
                  styles.quantityStep,
                  quantityLocked ? styles.quantityStepDisabled : undefined,
                  pressed && !quantityLocked ? styles.quantityStepPressed : undefined,
                ]}>
                <IconSymbol color={quantityLocked ? Colors.light.mutedText : '#374151'} name="minus" size={22} />
              </Pressable>
              <Pressable
                accessibilityLabel="Increase quantity"
                accessibilityRole="button"
                disabled={quantityLocked}
                onPress={() => adjustQuantity(1)}
                style={({ pressed }) => [
                  styles.quantityStep,
                  quantityLocked ? styles.quantityStepDisabled : undefined,
                  pressed && !quantityLocked ? styles.quantityStepPressed : undefined,
                ]}>
                <IconSymbol color={quantityLocked ? Colors.light.mutedText : '#374151'} name="plus" size={22} />
              </Pressable>
            </View>
          </FormRow>
          <View style={styles.limitLine}>
            <Text selectable style={styles.limitLabel}>
              {maxQuantityLabel}{' '}
            </Text>
            <Text
              selectable
              style={[styles.limitValue, { color: direction === 'BUY' ? BUY_GREEN : Colors.light.destructive }]}>
              {maxQuantityText}
            </Text>
          </View>
          <FormRow label="Amount">
            <Text selectable style={styles.readOnlyValue}>{estimatedTicketAmountLabel}</Text>
          </FormRow>
          <ActionButton
            disabled={!stock || isSubmitting || !quantityIsValid || (direction === 'SELL' && sellDisabled)}
            label={direction === 'BUY' ? 'Buy' : 'Sell'}
            onPress={reviewTrade}
            style={[
              styles.tradeSubmitButton,
              direction === 'BUY' ? styles.buyActive : styles.sellActive,
            ]}
            variant={direction === 'BUY' ? 'primary' : 'danger'}
          />
        </PaperSection>
      ) : null}

      {ticketReady && portfolioPositions.length > 0 ? (
        <PaperSection title={`Positions(${portfolioPositions.length})`}>
          <PositionsTable
            onOpenSell={(position) =>
              router.replace({
                pathname: '/paper-trading/sell',
                params: { from: 'portfolio', symbol: position.symbol },
              } as Href)
            }
            positions={portfolioPositions}
          />
        </PaperSection>
      ) : null}

      <ConfirmOverlay
        confirmLabel={direction === 'BUY' ? 'Buy' : 'Sell'}
        danger={direction === 'SELL'}
        onCancel={() => {
          setConfirmQuantity(null);
          setSubmitError(null);
        }}
        onConfirm={() => void submitTrade()}
        pending={isSubmitting}
        pendingLabel={direction === 'BUY' ? 'Buying...' : 'Selling...'}
        title={direction === 'BUY' ? 'Confirm buy paper trade' : 'Confirm sell paper trade'}
        visible={confirmQuantity !== null}>
        <View style={styles.confirmRows}>
          <FieldRow
            label="Direction"
            tone={direction === 'BUY' ? 'positive' : 'negative'}
            value={direction === 'BUY' ? 'Buy' : 'Sell'}
          />
          {direction === 'SELL' ? <FieldRow label="Quantity mode" value={sellMode === 'ALL' ? 'All' : 'Partial'} /> : null}
          <FieldRow label="Quantity" value={formatQuantity(confirmQuantity)} />
          <FieldRow label="Price" value={stock ? formatPaperMoney(getPreferredPrice(stock)) : 'Unavailable'} />
          <FieldRow label="Estimated amount" value={estimatedAmount} />
          <FieldRow label="Fee" value={formatPaperMoney(PAPER_TRADE_FEE)} />
          <FieldRow
            label={direction === 'BUY' ? 'Estimated total cost' : 'Estimated cash received'}
            value={settlementAmountNumber === null ? 'Unavailable' : formatPaperMoney(settlementAmountNumber)}
          />
          {direction === 'SELL' ? (
            <FieldRow
              label="Remaining quantity"
              value={remainingSellQuantity === null ? 'Unavailable' : `${formatQuantity(remainingSellQuantity)} shares`}
            />
          ) : null}
          {submitError ? (
            <View style={styles.modalError}>
              <Text selectable style={styles.modalErrorText}>{submitError}</Text>
            </View>
          ) : null}
        </View>
      </ConfirmOverlay>
    </ScrollView>
  );
}

async function reloadPaperTradingReads(credentials: BasicAuthCredentials) {
  await Promise.allSettled([
    paperTradingApi.getAccount(credentials),
    paperTradingApi.getPortfolio(credentials),
    paperTradingApi.getTransactions(credentials, { currentSessionOnly: true, size: 50 }),
  ]);
}

function FormRow({ children, label }: { children: ReactNode; label: string }) {
  return (
    <View style={styles.formRow}>
      <Text selectable style={styles.formLabel}>
        {label}
      </Text>
      <View style={styles.formControl}>{children}</View>
    </View>
  );
}

function SegmentButton({
  active,
  disabled = false,
  label,
  onPress,
  secondary = false,
  tone,
}: {
  active: boolean;
  disabled?: boolean;
  label: string;
  onPress: () => void;
  secondary?: boolean;
  tone: 'buy' | 'sell' | 'neutral';
}) {
  const activeStyle = secondary
    ? styles.segmentSecondaryActive
    : tone === 'buy'
      ? styles.segmentBuy
      : tone === 'sell'
        ? styles.segmentSell
        : styles.segmentNeutral;
  return (
    <Pressable
      accessibilityLabel={label}
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.segmentButton,
        secondary ? styles.segmentButtonSecondary : undefined,
        active ? activeStyle : undefined,
        disabled ? styles.segmentButtonDisabled : undefined,
        pressed ? styles.pressed : undefined,
      ]}>
      <Text
        style={[
          styles.segmentText,
          secondary ? styles.segmentTextSecondary : undefined,
          active ? (secondary ? styles.segmentTextSecondaryActive : styles.segmentTextActive) : undefined,
        ]}>
        {label}
      </Text>
    </Pressable>
  );
}

function USFlagIcon() {
  return (
    <View accessibilityLabel="US market" style={styles.flag}>
      {Array.from({ length: 7 }).map((_, index) => (
        <View
          key={index}
          style={[styles.flagStripe, index % 2 === 0 ? styles.flagStripeRed : styles.flagStripeWhite]}
        />
      ))}
      <View style={styles.flagCanton}>
        <View style={styles.flagStarDot} />
        <View style={styles.flagStarDot} />
        <View style={styles.flagStarDot} />
      </View>
    </View>
  );
}

function formatTicketAmount(value: number | null) {
  if (value === null || !Number.isFinite(value)) {
    return 'Unavailable';
  }
  return value.toLocaleString('en-US', {
    maximumFractionDigits: 2,
    minimumFractionDigits: 2,
  });
}

function buildDetailParams(
  symbol: string,
  returnTo: string | undefined,
  searchFrom: string | undefined,
  searchSymbol: string | undefined,
) {
  const params: Record<string, string> = { symbol };
  if (returnTo) {
    params.returnTo = returnTo;
  }
  if (searchFrom) {
    params.searchFrom = searchFrom;
  }
  if (searchSymbol) {
    params.searchSymbol = searchSymbol;
  }
  return params;
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  content: {
    gap: 0,
    paddingHorizontal: 0,
    width: '100%',
  },
  invalidWrap: {
    gap: Spacing.md,
    padding: Spacing.md,
  },
  quoteHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
    marginTop: Spacing.lg,
  },
  quoteIdentity: {
    flex: 1,
    minWidth: 0,
  },
  symbol: {
    color: Colors.light.text,
    fontSize: 17,
    fontWeight: '700',
    lineHeight: 22,
  },
  company: {
    color: Colors.light.mutedText,
    fontSize: 15,
    fontWeight: '400',
    lineHeight: 22,
  },
  quoteNumbers: {
    alignItems: 'flex-end',
    flexDirection: 'row',
    gap: 24,
    flexShrink: 0,
  },
  price: {
    fontSize: 18,
    fontVariant: ['tabular-nums'],
    fontWeight: '500',
  },
  percent: {
    fontSize: 18,
    fontVariant: ['tabular-nums'],
    fontWeight: '500',
  },
  accountRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  accountIdentity: {
    alignItems: 'center',
    flexDirection: 'row',
    flex: 1,
    gap: Spacing.sm,
    minWidth: 0,
  },
  accountCopy: {
    flex: 1,
    minWidth: 0,
  },
  marketBadge: {
    alignItems: 'center',
    backgroundColor: 'transparent',
    borderColor: 'transparent',
    height: 24,
    justifyContent: 'center',
    width: 32,
  },
  flag: {
    borderColor: '#E5E7EB',
    borderRadius: 2,
    borderWidth: 1,
    height: 18,
    overflow: 'hidden',
    position: 'relative',
    width: 28,
  },
  flagStripe: {
    flex: 1,
  },
  flagStripeRed: {
    backgroundColor: '#B91C1C',
  },
  flagStripeWhite: {
    backgroundColor: '#FFFFFF',
  },
  flagCanton: {
    alignItems: 'center',
    backgroundColor: '#1D4ED8',
    flexDirection: 'row',
    gap: 1,
    height: 10,
    justifyContent: 'center',
    left: 0,
    position: 'absolute',
    top: 0,
    width: 12,
  },
  flagStarDot: {
    backgroundColor: '#FFFFFF',
    borderRadius: 999,
    height: 1.5,
    width: 1.5,
  },
  accountLabel: {
    color: Colors.light.mutedText,
    fontSize: 14,
    fontWeight: '400',
    lineHeight: 18,
  },
  accountValue: {
    color: Colors.light.text,
    fontSize: 18,
    fontVariant: ['tabular-nums'],
    fontWeight: '500',
  },
  viewLink: {
    paddingHorizontal: Spacing.sm,
    paddingVertical: Spacing.sm,
  },
  viewLinkText: {
    color: '#2563EB',
    fontSize: 14,
    fontWeight: '700',
  },
  formRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
    minHeight: 42,
    paddingVertical: 3,
  },
  formLabel: {
    color: Colors.light.mutedText,
    fontSize: 15,
    fontWeight: '400',
    width: 112,
  },
  formControl: {
    alignItems: 'flex-start',
    flex: 1,
    minWidth: 0,
  },
  directionSwitch: {
    borderColor: '#CBD5E1',
    borderRadius: 16,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 0,
    overflow: 'hidden',
    width: '100%',
  },
  modeSwitch: {
    borderColor: '#CBD5E1',
    borderRadius: 10,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 0,
    overflow: 'hidden',
    width: '100%',
  },
  segmentButton: {
    alignItems: 'center',
    backgroundColor: '#F8FAFC',
    borderRadius: 0,
    borderWidth: 0,
    flex: 1,
    minHeight: 40,
    justifyContent: 'center',
    paddingHorizontal: Spacing.md,
  },
  segmentButtonSecondary: {
    backgroundColor: '#F8FAFC',
    minHeight: 36,
  },
  segmentButtonDisabled: {
    opacity: 0.55,
  },
  buyActive: {
    backgroundColor: BUY_GREEN,
    borderColor: BUY_GREEN,
    borderRadius: 999,
    minHeight: 48,
  },
  sellActive: {
    backgroundColor: Colors.light.destructive,
    borderColor: Colors.light.destructive,
    borderRadius: 999,
    minHeight: 48,
  },
  tradeSubmitButton: {
    marginBottom: Spacing.xl,
  },
  segmentBuy: {
    backgroundColor: BUY_GREEN,
    borderColor: BUY_GREEN,
  },
  segmentSell: {
    backgroundColor: Colors.light.destructive,
    borderColor: Colors.light.destructive,
  },
  segmentNeutral: {
    backgroundColor: '#052344',
    borderColor: '#052344',
  },
  segmentSecondaryActive: {
    backgroundColor: '#FEE2E2',
  },
  segmentText: {
    color: Colors.light.text,
    fontSize: 15,
    fontWeight: '500',
  },
  segmentTextSecondary: {
    color: Colors.light.mutedText,
    fontSize: 14,
  },
  segmentTextSecondaryActive: {
    color: Colors.light.text,
    fontWeight: '600',
  },
  segmentTextActive: {
    color: '#FFFFFF',
  },
  readOnlyValue: {
    color: Colors.light.text,
    fontSize: 18,
    fontVariant: ['tabular-nums'],
    fontWeight: '400',
    textAlign: 'left',
  },
  quantityControl: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderColor: '#94A3B8',
    borderRadius: 10,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 0,
    width: '100%',
  },
  quantityStep: {
    alignItems: 'center',
    backgroundColor: '#F1F5F9',
    height: 44,
    justifyContent: 'center',
    width: 42,
  },
  quantityStepPressed: {
    backgroundColor: '#E2E8F0',
  },
  quantityStepDisabled: {
    backgroundColor: '#E2E8F0',
  },
  quantityInput: {
    backgroundColor: '#FFFFFF',
    borderWidth: 0,
    color: Colors.light.text,
    flex: 1,
    fontSize: 18,
    minHeight: 44,
    paddingHorizontal: Spacing.md,
    textAlign: 'left',
  },
  quantityInputDisabled: {
    backgroundColor: '#E2E8F0',
    color: Colors.light.mutedText,
  },
  resultActions: {
    gap: Spacing.sm,
  },
  confirmRows: {
    gap: 0,
    paddingHorizontal: Spacing.lg,
  },
  limitLine: {
    alignItems: 'center',
    flexDirection: 'row',
    marginTop: -Spacing.xs,
    minHeight: 16,
    paddingLeft: 112 + Spacing.md,
  },
  limitLabel: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '500',
  },
  limitValue: {
    fontSize: 12,
    fontVariant: ['tabular-nums'],
    fontWeight: '600',
  },
  modalError: {
    backgroundColor: '#FEF2F2',
    borderColor: '#FECACA',
    borderRadius: 8,
    borderWidth: 1,
    marginTop: Spacing.sm,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  modalErrorText: {
    color: Colors.light.destructive,
    fontSize: 13,
    lineHeight: 18,
  },
  pressed: {
    opacity: 0.82,
  },
});
