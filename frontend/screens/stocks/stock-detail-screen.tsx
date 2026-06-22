import { type Href, useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Animated,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  View,
  type LayoutChangeEvent,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
  type ScrollView as ScrollViewType,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { stocksApi } from '@/api/stocks';
import { watchlistApi } from '@/api/watchlist';
import { ActionButton } from '@/components/foundation/action-button';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import { AiExplanationDrawer } from '@/components/stocks/ai-explanation-drawer';
import { StockHistoryView } from '@/components/stocks/stock-history-view';
import { StockMarketNotice } from '@/components/stocks/stock-market-notice';
import { TimeframeSelector } from '@/components/stocks/timeframe-selector';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import { useRefreshCooldown } from '@/hooks/use-refresh-cooldown';
import { useAuthSession } from '@/providers/auth-session-provider';
import { useToast } from '@/providers/toast-provider';
import type { ApiNumber, StockDetailResponse, StockHistoryResponse, StockTimeframe } from '@/types/stocks';
import { STOCK_TIMEFRAMES } from '@/types/stocks';
import { saveRecentViewedStockSymbol } from '@/utils/recent-stocks';
import {
  formatPercent,
  formatPrice,
  formatSignedCurrencyChange,
  formatVolume,
  getMarketStatusWithTime,
  getMovementColor,
  getPreferredPercentChange,
  getPreferredPrice,
  getStockApiErrorMessage,
  normalizeStockSymbol,
  toNumber,
} from '@/utils/stock-display';

type StockDetailScreenProps = {
  returnTo?: string;
  searchFrom?: string;
  searchSymbol?: string;
  symbol: string;
};

type HeaderState = 'empty' | 'identity' | 'quote';

const IDENTITY_TRIGGER_BUFFER = -20;
const QUOTE_TRIGGER_BUFFER = 0;

export function StockDetailScreen({
  returnTo,
  searchFrom,
  searchSymbol,
  symbol,
}: StockDetailScreenProps) {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const guardedRefresh = useRefreshCooldown();
  const { credentials } = useAuthSession();
  const { showToast } = useToast();
  const normalizedSymbol = normalizeStockSymbol(symbol);
  const detailRequestIdRef = useRef(0);
  const historyRequestIdRef = useRef(0);
  const scrollViewRef = useRef<ScrollViewType | null>(null);
  const scrollY = useRef(new Animated.Value(0)).current;
  const [headerHeight, setHeaderHeight] = useState(insets.top + 48);
  const [contentStackY, setContentStackY] = useState<number | null>(null);
  const [pricePanelYInStack, setPricePanelYInStack] = useState<number | null>(null);
  const [identityBlockYInPanel, setIdentityBlockYInPanel] = useState<number | null>(null);
  const [identityBlockHeight, setIdentityBlockHeight] = useState(0);
  const [priceBlockYInPanel, setPriceBlockYInPanel] = useState<number | null>(null);
  const [priceBlockHeight, setPriceBlockHeight] = useState(0);
  const [aiSectionY, setAiSectionY] = useState(0);
  const [headerState, setHeaderState] = useState<HeaderState>('empty');
  const lastScrollYRef = useRef(0);
  const [detail, setDetail] = useState<StockDetailResponse | null>(null);
  const [history, setHistory] = useState<StockHistoryResponse | null>(null);
  const [selectedTimeframe, setSelectedTimeframe] = useState<StockTimeframe>('1D');
  const [isDetailLoading, setIsDetailLoading] = useState(true);
  const [isHistoryLoading, setIsHistoryLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isWatchlistPending, setIsWatchlistPending] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [historyError, setHistoryError] = useState<string | null>(null);

  const loadDetail = useCallback(
    async (mode: 'initial' | 'refresh' = 'initial') => {
      const requestId = detailRequestIdRef.current + 1;
      detailRequestIdRef.current = requestId;

      if (!credentials) {
        setDetailError('Sign in again to load this stock.');
        setIsDetailLoading(false);
        setIsRefreshing(false);
        return;
      }

      if (mode === 'refresh') {
        setIsRefreshing(true);
      } else {
        setIsDetailLoading(true);
      }

      setDetailError(null);

      try {
        const response = await stocksApi.getStockDetail(credentials, normalizedSymbol);
        if (detailRequestIdRef.current !== requestId) {
          return;
        }
        setDetail(response);
        void saveRecentViewedStockSymbol(response.symbol);
      } catch (error) {
        if (detailRequestIdRef.current !== requestId) {
          return;
        }
        setDetailError(getStockApiErrorMessage(error, 'Stock detail could not be loaded.'));
      } finally {
        if (detailRequestIdRef.current === requestId) {
          setIsDetailLoading(false);
          setIsRefreshing(false);
        }
      }
    },
    [credentials, normalizedSymbol],
  );

  const loadHistory = useCallback(
    async (timeframe: StockTimeframe) => {
      const requestId = historyRequestIdRef.current + 1;
      historyRequestIdRef.current = requestId;

      if (!credentials) {
        setHistoryError('Sign in again to load stock history.');
        setIsHistoryLoading(false);
        return;
      }

      setIsHistoryLoading(true);
      setHistoryError(null);

      try {
        const response = await stocksApi.getStockHistory(credentials, normalizedSymbol, timeframe);
        if (historyRequestIdRef.current !== requestId) {
          return;
        }
        setHistory(response);
      } catch (error) {
        if (historyRequestIdRef.current !== requestId) {
          return;
        }
        setHistoryError(getStockApiErrorMessage(error, 'Stock history could not be loaded.'));
      } finally {
        if (historyRequestIdRef.current === requestId) {
          setIsHistoryLoading(false);
        }
      }
    },
    [credentials, normalizedSymbol],
  );

  useEffect(() => {
    detailRequestIdRef.current += 1;
    historyRequestIdRef.current += 1;
    setDetail(null);
    setHistory(null);
    setDetailError(null);
    setHistoryError(null);
    setSelectedTimeframe('1D');
    setIsDetailLoading(true);
    setIsHistoryLoading(true);
    setIsRefreshing(false);
    setIsWatchlistPending(false);
    setHeaderState('empty');
    setContentStackY(null);
    setPricePanelYInStack(null);
    setIdentityBlockYInPanel(null);
    setIdentityBlockHeight(0);
    setPriceBlockYInPanel(null);
    setPriceBlockHeight(0);
    void loadDetail();
    void loadHistory('1D');
  }, [loadDetail, loadHistory]);

  const handleRefresh = () => {
    guardedRefresh(() => {
      void loadDetail('refresh');
      void loadHistory(selectedTimeframe);
    });
  };

  const handleSelectTimeframe = (timeframe: StockTimeframe) => {
    if (isHistoryLoading || timeframe === selectedTimeframe) {
      return;
    }

    setSelectedTimeframe(timeframe);
    void loadHistory(timeframe);
  };

  const handleToggleWatchlist = async () => {
    if (!credentials || !detail || isWatchlistPending) {
      return;
    }

    const isWatchlisted = Boolean(detail.isWatchlisted);
    setIsWatchlistPending(true);

    try {
      const response = isWatchlisted
        ? await watchlistApi.removeSymbol(credentials, normalizedSymbol)
        : await watchlistApi.addSymbol(credentials, normalizedSymbol);
      const nextWatchlisted = response.stock?.isWatchlisted ?? !isWatchlisted;
      setDetail((current) =>
        current
          ? {
              ...current,
              isWatchlisted: nextWatchlisted,
            }
          : current,
      );
      showToast(
        `${normalizedSymbol} ${nextWatchlisted ? 'added to' : 'removed from'} watchlist.`,
        'success',
      );
    } catch (error) {
      showToast(getStockApiErrorMessage(error, 'Watchlist could not be updated.'), 'error');
    } finally {
      setIsWatchlistPending(false);
    }
  };

  const openPaperTradePlaceholder = () => {
    router.push({
      pathname: '/paper-trading/buy',
      params: buildPaperTradeParams(normalizedSymbol, returnTo, searchFrom, searchSymbol),
    } as Href);
  };

  const handleBack = () => {
    if (returnTo === 'stocks') {
      router.replace('/stocks' as Href);
      return;
    }

    if (returnTo === 'watchlist') {
      router.replace('/dashboard' as Href);
      return;
    }

    if (returnTo === 'search-context') {
      router.replace({
        pathname: '/stocks/search-context',
        params: buildSearchContextParams(searchFrom, searchSymbol),
      } as Href);
      return;
    }

    if (returnTo === 'search-tab') {
      router.replace('/stocks/search' as Href);
      return;
    }

    router.replace('/stocks' as Href);
  };

  const measurementsReady =
    contentStackY !== null &&
    pricePanelYInStack !== null &&
    identityBlockYInPanel !== null &&
    priceBlockYInPanel !== null &&
    headerHeight > 0 &&
    identityBlockHeight > 0 &&
    priceBlockHeight > 0;
  const identityBottomY = measurementsReady
    ? contentStackY + pricePanelYInStack + identityBlockYInPanel + identityBlockHeight
    : 0;
  const priceBottomY = measurementsReady
    ? contentStackY + pricePanelYInStack + priceBlockYInPanel + priceBlockHeight
    : 0;

  const updateHeaderState = useCallback(
    (scrollOffset: number) => {
      lastScrollYRef.current = scrollOffset;
      if (!measurementsReady) {
        setHeaderState((current) => (current === 'empty' ? current : 'empty'));
        return;
      }

      const coveredY = scrollOffset + headerHeight;
      const nextState: HeaderState =
        coveredY >= priceBottomY + QUOTE_TRIGGER_BUFFER
          ? 'quote'
          : coveredY >= identityBottomY + IDENTITY_TRIGGER_BUFFER
            ? 'identity'
            : 'empty';
      setHeaderState((current) => (current === nextState ? current : nextState));
    },
    [headerHeight, identityBottomY, measurementsReady, priceBottomY],
  );

  useEffect(() => {
    updateHeaderState(lastScrollYRef.current);
  }, [updateHeaderState]);

  const scrollToAiSection = useCallback(() => {
    const scroll = () => {
      scrollViewRef.current?.scrollTo({
        animated: true,
        y: Math.max(0, aiSectionY - headerHeight - 2),
      });
    };

    requestAnimationFrame(scroll);
    setTimeout(scroll, 90);
    setTimeout(scroll, 240);
  }, [aiSectionY, headerHeight]);

  return (
    <View style={styles.root}>
      <View
        onLayout={(event) => setHeaderHeight(event.nativeEvent.layout.height)}
        style={[styles.fixedHeader, { paddingTop: insets.top + 2 }]}>
        <HeaderIconButton
          accessibilityLabel="Back"
          icon="chevron.left"
          onPress={handleBack}
        />
        <View style={styles.fixedHeaderTitle}>
          {headerState !== 'empty' ? (
            <Text selectable numberOfLines={1} style={styles.fixedSymbol}>
              {detail?.symbol ?? normalizedSymbol}
            </Text>
          ) : null}
          {headerState === 'identity' ? (
            <Text selectable numberOfLines={1} style={styles.fixedCompany}>
              {detail?.companyName ?? 'Stock detail'}
            </Text>
          ) : null}
          {detail && headerState === 'quote' ? (
            <View style={styles.fixedQuoteRow}>
              <Text
                selectable
                numberOfLines={1}
                style={[
                  styles.fixedQuoteText,
                  { color: getMovementColor(getPreferredPercentChange(detail)) },
                ]}>
                {formatPrice(getPreferredPrice(detail))}
              </Text>
              <MovementMarker percentChange={getPreferredPercentChange(detail)} size={12} />
              {formatSignedCurrencyChange(detail.displayedAbsoluteChange) ? (
                <Text
                  selectable
                  numberOfLines={1}
                  style={[
                    styles.fixedQuoteChangeText,
                    { color: getMovementColor(getPreferredPercentChange(detail)) },
                  ]}>
                  {formatSignedCurrencyChange(detail.displayedAbsoluteChange)}
                </Text>
              ) : null}
              <Text
                selectable
                numberOfLines={1}
                style={[
                  styles.fixedQuotePercentText,
                  { color: getMovementColor(getPreferredPercentChange(detail)) },
                ]}>
                {formatPercent(getPreferredPercentChange(detail))}
              </Text>
            </View>
          ) : null}
        </View>
        <HeaderIconButton
          accessibilityLabel="Search stocks"
          icon="magnifyingglass"
          onPress={() =>
            router.push({
              pathname: '/stocks/search-context',
              params: { from: 'detail', symbol: normalizedSymbol },
            } as Href)
          }
        />
        <HeaderIconButton
          accessibilityLabel="Refresh stock detail"
          icon="arrow.clockwise"
          onPress={handleRefresh}
        />
        <HeaderIconButton
          accessibilityLabel={detail?.isWatchlisted ? 'Remove from watchlist' : 'Add to watchlist'}
          disabled={!detail || isWatchlistPending}
          icon={detail?.isWatchlisted ? 'heart.fill' : 'heart'}
          onPress={handleToggleWatchlist}
          tone={detail?.isWatchlisted ? 'danger' : 'default'}
        />
      </View>

      <Animated.ScrollView
        ref={scrollViewRef}
        alwaysBounceVertical
        bounces
        contentContainerStyle={[
          styles.content,
          {
            paddingBottom: Math.max(70, insets.bottom + 58),
            paddingTop: headerHeight,
          },
        ]}
        contentInsetAdjustmentBehavior="never"
        keyboardShouldPersistTaps="handled"
        onScroll={Animated.event(
          [{ nativeEvent: { contentOffset: { y: scrollY } } }],
          {
            listener: (event: NativeSyntheticEvent<NativeScrollEvent>) =>
              updateHeaderState(event.nativeEvent.contentOffset.y),
            useNativeDriver: false,
          },
        )}
        overScrollMode="never"
        refreshControl={<RefreshControl onRefresh={handleRefresh} refreshing={isRefreshing} />}
        scrollEventThrottle={16}
        style={styles.scroller}>
        {detailError ? <ErrorBanner title="Stock needs attention" message={detailError} /> : null}

        {isDetailLoading && !detail ? (
          <View style={styles.stack}>
            <Text selectable style={styles.loadingLabel}>
              Loading stock...
            </Text>
            <SkeletonRows count={3} />
          </View>
        ) : detailError && !detail ? (
          <View style={styles.stack}>
            <ActionButton
              accessibilityHint="Returns to the stock list."
              label="Back to stock list"
              onPress={handleBack}
              variant="ghost"
            />
            <ActionButton
              accessibilityHint="Retries loading this stock detail from the backend."
              label="Try loading stock again"
              onPress={() => void loadDetail()}
              variant="secondary"
            />
          </View>
        ) : detail ? (
          <View
            key={normalizedSymbol}
            onLayout={(event) => setContentStackY(event.nativeEvent.layout.y)}
            style={styles.stack}>
            <StockMarketNotice stocks={[detail]} />

            <View onLayout={(event) => setPricePanelYInStack(event.nativeEvent.layout.y)}>
              <PricePanel
                detail={detail}
                onIdentityLayout={(event) => {
                  setIdentityBlockYInPanel(event.nativeEvent.layout.y);
                  setIdentityBlockHeight(event.nativeEvent.layout.height);
                }}
                onPriceLayout={(event) => {
                  setPriceBlockYInPanel(event.nativeEvent.layout.y);
                  setPriceBlockHeight(event.nativeEvent.layout.height);
                }}
              />
            </View>

            <View style={styles.historySection}>
              <View style={styles.sectionHeader}>
                <Text selectable style={styles.sectionTitle}>
                  History
                </Text>
              </View>
              <TimeframeSelector
                onSelect={handleSelectTimeframe}
                pending={isHistoryLoading}
                selectedTimeframe={selectedTimeframe}
                timeframes={STOCK_TIMEFRAMES}
              />
              <StockHistoryView
                errorMessage={historyError}
                history={history}
                loading={isHistoryLoading}
                onRetry={() => void loadHistory(selectedTimeframe)}
              />
            </View>

            <View onLayout={(event) => setAiSectionY(event.nativeEvent.layout.y)}>
              <AiExplanationDrawer
                credentials={credentials}
                onContentReady={scrollToAiSection}
                onOpen={scrollToAiSection}
                symbol={detail.symbol}
                timeframe={selectedTimeframe}
              />
            </View>
          </View>
        ) : (
          <Text selectable style={styles.loadingLabel}>
            Stock unavailable.
          </Text>
        )}
      </Animated.ScrollView>

      <View style={[styles.footer, { paddingBottom: Math.max(6, Math.min(insets.bottom, 14)) }]}>
        <ActionButton
          accessibilityHint={`Opens the placeholder paper trade page for ${normalizedSymbol}. It does not execute a trade.`}
          label="Practice Trade"
          onPress={openPaperTradePlaceholder}
          style={styles.footerButton}
        />
      </View>
    </View>
  );
}

function HeaderIconButton({
  accessibilityLabel,
  disabled = false,
  icon,
  onPress,
  tone = 'default',
}: {
  accessibilityLabel: string;
  disabled?: boolean;
  icon: 'chevron.left' | 'magnifyingglass' | 'arrow.clockwise' | 'heart' | 'heart.fill';
  onPress: () => void;
  tone?: 'default' | 'danger';
}) {
  return (
    <Pressable
      accessibilityLabel={accessibilityLabel}
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.headerIcon,
        pressed && !disabled ? styles.headerIconPressed : undefined,
        disabled ? styles.headerIconDisabled : undefined,
      ]}>
      <IconSymbol
        color={tone === 'danger' ? Colors.light.destructive : Colors.light.text}
        name={icon}
        size={20}
      />
    </Pressable>
  );
}

function MovementMarker({ percentChange, size = 16 }: { percentChange: ApiNumber; size?: number }) {
  const parsed = toNumber(percentChange);
  if (parsed === null || parsed === 0) {
    return null;
  }

  return (
    <Text
      selectable={false}
      style={[
        styles.movementMarker,
        {
          color: getMovementColor(percentChange),
          fontSize: size,
          lineHeight: size + 2,
        },
      ]}>
      {parsed > 0 ? '▲' : '▼'}
    </Text>
  );
}

function PricePanel({
  detail,
  onIdentityLayout,
  onPriceLayout,
}: {
  detail: StockDetailResponse;
  onIdentityLayout: (event: LayoutChangeEvent) => void;
  onPriceLayout: (event: LayoutChangeEvent) => void;
}) {
  const percentChange = getPreferredPercentChange(detail);
  const movementColor = getMovementColor(percentChange);
  const absoluteChange = formatSignedCurrencyChange(detail.displayedAbsoluteChange);
  const volume = detail.displayedVolume == null ? null : formatVolume(detail.displayedVolume);

  return (
    <View style={styles.pricePanel}>
      <View onLayout={onIdentityLayout} style={styles.identityCopy}>
        <View style={styles.identityRow}>
          <Text selectable style={styles.symbol}>
            {detail.symbol}
          </Text>
          <Text selectable numberOfLines={1} style={styles.company}>
            {detail.companyName}
          </Text>
        </View>
        <Text selectable numberOfLines={1} style={styles.marketStatus}>
          {getMarketStatusWithTime(detail)}
        </Text>
      </View>
      <View onLayout={onPriceLayout} style={styles.priceBottomRow}>
        <View style={styles.priceStack}>
          <View style={styles.priceValueRow}>
            <Text selectable numberOfLines={1} style={[styles.price, { color: movementColor }]}>
              {formatPrice(getPreferredPrice(detail))}
            </Text>
            <MovementMarker percentChange={percentChange} size={15} />
          </View>
          <View style={styles.changeRow}>
            {absoluteChange ? (
              <Text selectable style={[styles.change, { color: movementColor }]}>
                {absoluteChange}
              </Text>
            ) : null}
            <Text selectable style={[styles.change, { color: movementColor }]}>
              {formatPercent(percentChange)}
            </Text>
          </View>
        </View>
        <View style={styles.rangeStack}>
          <RangeLine label="High" value={formatPrice(detail.highPrice)} />
          <RangeLine label="Low" value={formatPrice(detail.lowPrice)} />
          {volume ? <RangeLine label="Volume" value={volume} /> : null}
        </View>
      </View>
    </View>
  );
}

function buildSearchContextParams(searchFrom: string | undefined, searchSymbol: string | undefined) {
  const params: Record<string, string> = {};
  if (searchFrom) {
    params.from = searchFrom;
  }
  if (searchSymbol) {
    params.symbol = searchSymbol;
  }
  return params;
}

function buildPaperTradeParams(
  symbol: string,
  returnTo: string | undefined,
  searchFrom: string | undefined,
  searchSymbol: string | undefined,
) {
  const params: Record<string, string> = {
    from: 'detail',
    symbol,
  };

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

function RangeLine({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.rangeLine}>
      <Text selectable style={styles.rangeLabel}>
        {label}
      </Text>
      <Text selectable adjustsFontSizeToFit minimumFontScale={0.82} numberOfLines={1} style={styles.rangeValue}>
        {value}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  scroller: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  fixedHeader: {
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.98)',
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    gap: Spacing.xs,
    left: 0,
    paddingBottom: 6,
    paddingHorizontal: Spacing.sm,
    position: 'absolute',
    right: 0,
    top: 0,
    zIndex: 10,
  },
  fixedHeaderTitle: {
    flex: 1,
    justifyContent: 'center',
    minHeight: 48,
    minWidth: 0,
  },
  fixedSymbol: {
    color: Colors.light.text,
    fontSize: 15,
    fontWeight: '700',
    lineHeight: 19,
  },
  fixedCompany: {
    color: Colors.light.mutedText,
    fontSize: 12,
    lineHeight: 16,
  },
  fixedQuoteRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 1,
    minHeight: 18,
  },
  fixedQuoteText: {
    color: Colors.light.text,
    fontSize: 11,
    fontVariant: ['tabular-nums'],
    fontWeight: '600',
    lineHeight: 14,
  },
  fixedQuoteChangeText: {
    color: Colors.light.text,
    fontSize: 11,
    fontVariant: ['tabular-nums'],
    fontWeight: '600',
    lineHeight: 14,
    marginLeft: 12,
  },
  fixedQuotePercentText: {
    color: Colors.light.text,
    fontSize: 11,
    fontVariant: ['tabular-nums'],
    fontWeight: '600',
    lineHeight: 14,
    marginLeft: 10,
  },
  headerIcon: {
    alignItems: 'center',
    height: 40,
    justifyContent: 'center',
    width: 36,
  },
  headerIconPressed: {
    opacity: 0.7,
  },
  headerIconDisabled: {
    opacity: 0.45,
  },
  content: {
    gap: 0,
    paddingHorizontal: 0,
    width: '100%',
  },
  stack: {
    gap: Spacing.sm,
  },
  loadingLabel: {
    color: Colors.light.mutedText,
    fontSize: 13,
    fontWeight: '500',
    paddingHorizontal: Spacing.md,
    paddingTop: Spacing.md,
  },
  pricePanel: {
    backgroundColor: Colors.light.surface,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    gap: Spacing.sm,
    paddingHorizontal: Spacing.md,
    paddingVertical: 9,
  },
  identityCopy: {
    gap: 2,
    minWidth: 0,
  },
  identityRow: {
    alignItems: 'baseline',
    flexDirection: 'row',
    gap: Spacing.sm,
    minWidth: 0,
  },
  symbol: {
    color: Colors.light.text,
    fontSize: 22,
    fontWeight: '700',
    letterSpacing: 0,
    lineHeight: 27,
  },
  company: {
    color: Colors.light.text,
    flex: 1,
    fontSize: 13,
    fontWeight: '500',
    lineHeight: 18,
  },
  marketStatus: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '600',
    lineHeight: 16,
  },
  priceBottomRow: {
    alignItems: 'flex-end',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.md,
    justifyContent: 'space-between',
  },
  priceStack: {
    flexGrow: 1,
    flexShrink: 0,
    gap: Spacing.xs,
    minWidth: 178,
  },
  priceValueRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 1,
  },
  price: {
    fontSize: 32,
    fontVariant: ['tabular-nums'],
    fontWeight: '700',
    lineHeight: 38,
  },
  change: {
    fontSize: 15,
    fontVariant: ['tabular-nums'],
    fontWeight: '600',
  },
  changeRow: {
    alignItems: 'center',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  rangeStack: {
    alignItems: 'flex-end',
    flexShrink: 0,
    gap: Spacing.xs,
    marginLeft: Spacing.sm,
    width: 152,
  },
  rangeLine: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
    justifyContent: 'space-between',
    width: '100%',
  },
  rangeLabel: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '600',
    width: 44,
  },
  rangeValue: {
    color: Colors.light.text,
    fontSize: 11,
    fontVariant: ['tabular-nums'],
    fontWeight: '700',
    flex: 1,
    textAlign: 'right',
  },
  labelsStrip: {
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.xs,
  },
  sectionHeader: {
    gap: Spacing.xs,
  },
  sectionTitle: {
    color: Colors.light.text,
    fontSize: 17,
    fontWeight: '700',
  },
  pills: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  historySection: {
    gap: Spacing.md,
  },
  footer: {
    backgroundColor: 'transparent',
    borderTopWidth: 0,
    left: 0,
    paddingHorizontal: Spacing.md,
    paddingTop: 2,
    position: 'absolute',
    right: 0,
    bottom: 0,
  },
  footerButton: {
    backgroundColor: '#052344',
    borderColor: '#052344',
    minHeight: 38,
    paddingVertical: 5,
  },
  movementMarker: {
    fontWeight: '800',
    includeFontPadding: false,
    marginLeft: -1,
  },
});
