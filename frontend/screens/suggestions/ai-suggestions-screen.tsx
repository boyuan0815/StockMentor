import { useFocusEffect } from '@react-navigation/native';
import { type Href, useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { aiSuggestionsApi } from '@/api/ai-suggestions';
import { ActionButton } from '@/components/foundation/action-button';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import { PaperHeader } from '@/components/paper-trading/paper-trading-ui';
import { HighlightedText } from '@/components/stocks/highlighted-text';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Radius, Spacing } from '@/constants/theme';
import { useRefreshCooldown } from '@/hooks/use-refresh-cooldown';
import { useAuthSession } from '@/providers/auth-session-provider';
import { useToast } from '@/providers/toast-provider';
import type {
  AiSuggestionQuoteFields,
  RemainingStockResponse,
  StockAiSuggestionResponse,
  SuggestedStockResponse,
} from '@/types/ai-suggestions';
import {
  formatBackendDateTime,
  formatPercent,
  formatPrice,
  getMovementColor,
  getStockApiErrorMessage,
  labelize,
  toNumber,
} from '@/utils/stock-display';

type LoadMode = 'initial' | 'refresh';
type PendingAction = {
  itemId: number;
  type: 'dismiss' | 'watchlist';
} | null;

type RankTheme = {
  accent: string;
  badgeBackground: string;
  badgeText: string;
  buttonBackground: string;
  buttonBorder: string;
  buttonText: string;
  divider: string;
  headerBackground: string;
  scoreBackground: string;
  scoreText: string;
};

const RANK_THEMES: Record<'gold' | 'silver' | 'bronze', RankTheme> = {
  gold: {
    accent: '#C89205',
    badgeBackground: '#E2BE3F',
    badgeText: '#4A3700',
    buttonBackground: '#D9AA14',
    buttonBorder: '#C79506',
    buttonText: '#FFFFFF',
    divider: '#D5AA27',
    headerBackground: '#FFF9E8',
    scoreBackground: '#D8A20E',
    scoreText: '#FFFFFF',
  },
  silver: {
    accent: '#7D8795',
    badgeBackground: '#CBD5E1',
    badgeText: '#263241',
    buttonBackground: '#8793A4',
    buttonBorder: '#758295',
    buttonText: '#FFFFFF',
    divider: '#CBD5E1',
    headerBackground: '#F8FAFC',
    scoreBackground: '#94A3B8',
    scoreText: '#FFFFFF',
  },
  bronze: {
    accent: '#B76C2D',
    badgeBackground: '#D99557',
    badgeText: '#4D260B',
    buttonBackground: '#F8EAD8',
    buttonBorder: '#DA9B65',
    buttonText: '#8A4B1C',
    divider: '#D99A63',
    headerBackground: '#FFF7ED',
    scoreBackground: '#C8793C',
    scoreText: '#FFFFFF',
  },
};

export function AiSuggestionsScreen() {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const { credentials, user } = useAuthSession();
  const { showToast } = useToast();
  const guardedRefresh = useRefreshCooldown();
  const requestInFlightRef = useRef(false);
  const writeInFlightRef = useRef(false);
  const hasLoadedRef = useRef(false);
  const scrollRef = useRef<ScrollView | null>(null);
  const credentialsKey = credentials ? String(user?.userId ?? credentials.username) : null;
  const [response, setResponse] = useState<StockAiSuggestionResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isRefreshingBatch, setIsRefreshingBatch] = useState(false);
  const [pendingAction, setPendingAction] = useState<PendingAction>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadSuggestions = useCallback(
    async (mode: LoadMode = 'initial') => {
      if (requestInFlightRef.current || writeInFlightRef.current) {
        return;
      }

      if (!credentials) {
        setErrorMessage('Sign in again to load AI suggestions.');
        setIsLoading(false);
        setIsRefreshing(false);
        return;
      }

      requestInFlightRef.current = true;
      if (mode === 'refresh') {
        setIsRefreshing(true);
      } else if (!hasLoadedRef.current) {
        setIsLoading(true);
      }
      setErrorMessage(null);

      try {
        const nextResponse = await aiSuggestionsApi.getSuggestions(credentials);
        setResponse(normalizeSuggestionResponse(nextResponse));
      } catch (error) {
        setErrorMessage(getStockApiErrorMessage(error, 'AI suggestions could not be loaded.'));
      } finally {
        hasLoadedRef.current = true;
        requestInFlightRef.current = false;
        setIsLoading(false);
        setIsRefreshing(false);
      }
    },
    [credentials],
  );

  useEffect(() => {
    hasLoadedRef.current = false;
    requestInFlightRef.current = false;
    writeInFlightRef.current = false;
    setResponse(null);
    setPendingAction(null);
    setErrorMessage(null);
    setIsRefreshing(false);
    setIsRefreshingBatch(false);
    setIsLoading(Boolean(credentials));
  }, [credentialsKey, credentials]);

  useEffect(() => {
    if (!credentials || hasLoadedRef.current) {
      return;
    }
    void loadSuggestions('initial');
  }, [credentials, loadSuggestions]);

  useFocusEffect(
    useCallback(() => {
      scrollRef.current?.scrollTo({ animated: false, y: 0 });
      return undefined;
    }, []),
  );

  const suggestedStocks = response?.suggestedStocks ?? [];
  const remainingStocks = response?.remainingStocks ?? [];
  const refreshAllowed = response?.refreshAllowed === true;
  const readInFlight = isLoading || isRefreshing;
  const writeInFlight = isRefreshingBatch || Boolean(pendingAction);
  const refreshDisabled = !refreshAllowed || readInFlight || writeInFlight;
  const itemActionsDisabled = readInFlight || writeInFlight;
  const cooldownText = getCooldownText(response);
  const fallbackVisible = Boolean(response?.fallbackUsed || response?.batchStatus?.startsWith('FALLBACK'));

  const openStock = useCallback(
    (symbol: string) => {
      router.push({
        pathname: '/stocks/[symbol]',
        params: { symbol, returnTo: 'suggestions' },
      } as Href);
    },
    [router],
  );

  const handleSafeReadRefresh = () => {
    if (writeInFlightRef.current || writeInFlight) {
      return;
    }
    guardedRefresh(() => void loadSuggestions('refresh'));
  };

  const handleRefreshSuggestions = async () => {
    if (!credentials || refreshDisabled || requestInFlightRef.current || writeInFlightRef.current) {
      return;
    }

    writeInFlightRef.current = true;
    setIsRefreshingBatch(true);
    try {
      const nextResponse = await aiSuggestionsApi.refreshSuggestions(credentials);
      setResponse(normalizeSuggestionResponse(nextResponse));
      setErrorMessage(null);
      showToast('Educational suggestions updated.', 'success');
    } catch (error) {
      showToast(getStockApiErrorMessage(error, 'Suggestions could not be refreshed.'), 'error');
    } finally {
      writeInFlightRef.current = false;
      setIsRefreshingBatch(false);
    }
  };

  const handleDismiss = async (item: SuggestedStockResponse) => {
    if (!credentials || pendingAction || isRefreshingBatch || isRefreshing || requestInFlightRef.current || writeInFlightRef.current) {
      return;
    }

    writeInFlightRef.current = true;
    setPendingAction({ itemId: item.itemId, type: 'dismiss' });
    try {
      const nextResponse = await aiSuggestionsApi.dismissSuggestion(credentials, item.itemId);
      setResponse(normalizeSuggestionResponse(nextResponse));
      showToast(`${item.symbol} dismissed from suggestions.`, 'success');
    } catch (error) {
      showToast(getStockApiErrorMessage(error, `${item.symbol} could not be dismissed.`), 'error');
    } finally {
      writeInFlightRef.current = false;
      setPendingAction(null);
    }
  };

  const handleWatchlist = async (item: SuggestedStockResponse) => {
    if (
      !credentials ||
      pendingAction ||
      isRefreshingBatch ||
      isRefreshing ||
      requestInFlightRef.current ||
      writeInFlightRef.current ||
      item.isWatchlisted === true
    ) {
      return;
    }

    writeInFlightRef.current = true;
    setPendingAction({ itemId: item.itemId, type: 'watchlist' });
    try {
      const nextResponse = await aiSuggestionsApi.watchlistSuggestion(credentials, item.itemId);
      setResponse(normalizeSuggestionResponse(nextResponse));
      showToast(`${item.symbol} added to watchlist.`, 'success');
    } catch (error) {
      showToast(getStockApiErrorMessage(error, `${item.symbol} could not be added to watchlist.`), 'error');
    } finally {
      writeInFlightRef.current = false;
      setPendingAction(null);
    }
  };

  return (
    <View style={styles.container}>
      <View style={[styles.fixedHeader, { paddingTop: insets.top + 2 }]}>
        <PaperHeader
          brandIcon
          onRefresh={handleSafeReadRefresh}
          refreshAccessibilityLabel="Reload cached suggestions"
          refreshDisabled={readInFlight || writeInFlight}
          title="Suggestions"
        />
      </View>
      <ScrollView
        ref={scrollRef}
        alwaysBounceVertical
        bounces
        contentContainerStyle={[
          styles.content,
          { paddingBottom: Math.max(Spacing.xxl, insets.bottom + Spacing.xl) },
        ]}
        contentInsetAdjustmentBehavior="never"
        overScrollMode="never"
        refreshControl={
          <RefreshControl
            enabled={!itemActionsDisabled}
            onRefresh={handleSafeReadRefresh}
            refreshing={isRefreshing}
          />
        }
        style={styles.scroller}>
        {isLoading ? (
          <SkeletonRows count={4} />
        ) : errorMessage ? (
          <ErrorState message={errorMessage} onRetry={() => void loadSuggestions('refresh')} />
        ) : !response || suggestedStocks.length === 0 ? (
          <>
            <EmptySuggestions
              cooldownText={cooldownText}
              onRefresh={handleRefreshSuggestions}
              refreshDisabled={refreshDisabled}
              refreshLabel={isRefreshingBatch ? 'Refreshing...' : 'Request updated suggestions'}
            />
            <RemainingStocksSection onOpenStock={openStock} stocks={remainingStocks} />
          </>
        ) : (
          <>
            <BatchSummary
              cooldownText={cooldownText}
              fallbackVisible={fallbackVisible}
              isRefreshing={isRefreshingBatch}
              onRefresh={handleRefreshSuggestions}
              response={response}
              refreshDisabled={refreshDisabled}
            />
            <View style={styles.suggestionList}>
              {suggestedStocks.map((item, index) => (
                <SuggestionCard
                  actionsDisabled={itemActionsDisabled}
                  item={item}
                  key={item.itemId}
                  onDismiss={() => void handleDismiss(item)}
                  onOpen={() => openStock(item.symbol)}
                  onWatchlist={() => void handleWatchlist(item)}
                  pendingAction={pendingAction}
                  theme={themeForRank(item.rankNo ?? index + 1)}
                />
              ))}
            </View>
            <RemainingStocksSection onOpenStock={openStock} stocks={remainingStocks} />
          </>
        )}
      </ScrollView>
    </View>
  );
}

function BatchSummary({
  cooldownText,
  fallbackVisible,
  isRefreshing,
  onRefresh,
  refreshDisabled,
  response,
}: {
  cooldownText: string | null;
  fallbackVisible: boolean;
  isRefreshing: boolean;
  onRefresh: () => void;
  refreshDisabled: boolean;
  response: StockAiSuggestionResponse;
}) {
  return (
    <View style={styles.summary}>
      <View style={styles.summaryHeader}>
        <View style={styles.summaryCopy}>
          <Text selectable style={styles.summaryTitle}>
            Current learning batch
          </Text>
          <Text selectable style={styles.summaryMeta}>
            {formatBatchMeta(response)}
          </Text>
        </View>
        <ActionButton
          disabled={refreshDisabled}
          label={isRefreshing ? 'Refreshing...' : 'AI refresh'}
          onPress={onRefresh}
          style={styles.refreshButton}
        />
      </View>
      <Text selectable style={styles.safetyText}>
        Educational paper-trading suggestions only. Not financial advice or a future price prediction.
      </Text>
      {response.batchSummary ? (
        <Text selectable style={styles.summaryText}>
          {response.batchSummary}
        </Text>
      ) : null}
      {response.message ? (
        <Text selectable style={styles.messageText}>
          {response.message}
        </Text>
      ) : null}
      {cooldownText ? (
        <Text selectable style={styles.cooldownText}>
          {cooldownText}
        </Text>
      ) : null}
      {fallbackVisible ? (
        <View style={styles.fallbackNotice}>
          <Text selectable style={styles.fallbackText}>
            Fallback suggestions are shown because the latest AI batch was not fully available.
          </Text>
        </View>
      ) : null}
    </View>
  );
}

function SuggestionCard({
  actionsDisabled,
  item,
  onDismiss,
  onOpen,
  onWatchlist,
  pendingAction,
  theme,
}: {
  actionsDisabled: boolean;
  item: SuggestedStockResponse;
  onDismiss: () => void;
  onOpen: () => void;
  onWatchlist: () => void;
  pendingAction: PendingAction;
  theme: RankTheme;
}) {
  const display = getDisplayValues(item);
  const movementColor = getMovementColor(display.percentChange);
  const alreadyWatchlisted = item.isWatchlisted === true;
  const currentAction = pendingAction?.itemId === item.itemId ? pendingAction.type : null;

  return (
    <View style={styles.cardShell}>
      <Pressable
        accessibilityHint={`Opens ${item.symbol} stock detail from suggestions.`}
        accessibilityLabel={`${item.symbol} suggestion card`}
        accessibilityRole="button"
        onPress={onOpen}
        style={({ pressed }) => [styles.cardPressable, pressed ? styles.cardPressed : undefined]}>
        <View style={[styles.cardHeader, { backgroundColor: theme.headerBackground }]}>
          <View style={[styles.rankBadge, { backgroundColor: theme.badgeBackground }]}>
            <Text selectable style={[styles.rankText, { color: theme.badgeText }]}>
              {item.rankNo ?? '-'}
            </Text>
          </View>
          <View style={styles.identity}>
            <Text selectable numberOfLines={1} style={styles.symbol}>
              {item.symbol}
            </Text>
            <Text selectable numberOfLines={1} style={styles.company}>
              {item.companyName}
            </Text>
          </View>
          <View style={styles.quoteInline}>
            <Text selectable numberOfLines={1} style={[styles.topQuoteText, { color: movementColor }]}>
              {formatPrice(display.price)}
            </Text>
            <Text selectable numberOfLines={1} style={[styles.topQuoteText, { color: movementColor }]}>
              {formatPercent(display.percentChange)}
            </Text>
          </View>
          {item.matchScore !== null && item.matchScore !== undefined ? (
            <View style={[styles.scoreBox, { backgroundColor: theme.scoreBackground }]}>
              <Text selectable style={[styles.scoreNumber, { color: theme.scoreText }]}>
                {item.matchScore}%
              </Text>
              <Text selectable style={[styles.scoreLabel, { color: theme.scoreText }]}>
                MATCH
              </Text>
            </View>
          ) : null}
        </View>
        <View style={[styles.divider, { backgroundColor: theme.divider }]} />
        <View style={styles.cardBody}>
          <View style={styles.labelWrap}>
            {item.suggestionLabel ? <InfoPill label={item.suggestionLabel} tone="blue" /> : null}
            {item.riskLevel ? <InfoPill label={`${labelize(item.riskLevel)} risk`} tone="amber" /> : null}
            {item.priceFreshnessLabel ? <InfoPill label={item.priceFreshnessLabel} tone="violet" /> : null}
            {alreadyWatchlisted ? <InfoPill label="Watchlisted" tone="green" /> : null}
          </View>

          {item.shortReason ? (
            <HighlightedText
              highlights={item.shortReasonHighlights}
              style={styles.shortReason}
              text={item.shortReason}
            />
          ) : null}
          {item.detailReason ? (
            <View style={styles.reasonBlock}>
              <Text selectable style={[styles.reasonLabel, { color: theme.accent }]}>
                WHY IT FITS
              </Text>
              <HighlightedText
                highlights={item.detailReasonHighlights}
                style={styles.detailReason}
                text={item.detailReason}
              />
            </View>
          ) : null}
        </View>
      </Pressable>

      <View style={styles.cardActions}>
        <SuggestionActionButton
          disabled={actionsDisabled}
          iconName="xmark"
          label={currentAction === 'dismiss' ? 'Dismissing...' : 'Dismiss'}
          onPress={onDismiss}
          variant="dismiss"
        />
        <SuggestionActionButton
          disabled={actionsDisabled || alreadyWatchlisted}
          iconName={alreadyWatchlisted ? 'checkmark' : 'plus'}
          label={
            alreadyWatchlisted
              ? 'Watchlisted'
              : currentAction === 'watchlist'
                ? 'Adding...'
                : 'Add to watchlist'
          }
          onPress={onWatchlist}
          theme={theme}
          variant={alreadyWatchlisted ? 'done' : 'watchlist'}
        />
      </View>
    </View>
  );
}

function SuggestionActionButton({
  disabled,
  iconName,
  label,
  onPress,
  theme,
  variant,
}: {
  disabled: boolean;
  iconName: Parameters<typeof IconSymbol>[0]['name'];
  label: string;
  onPress: () => void;
  theme?: RankTheme;
  variant: 'dismiss' | 'watchlist' | 'done';
}) {
  const watchlist = variant === 'watchlist';
  const done = variant === 'done';
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.actionButton,
        watchlist
          ? {
              backgroundColor: theme?.buttonBackground ?? '#052344',
              borderColor: theme?.buttonBorder ?? '#052344',
            }
          : undefined,
        done ? styles.doneButton : undefined,
        pressed && !disabled ? styles.actionPressed : undefined,
        disabled ? styles.actionDisabled : undefined,
      ]}>
      <IconSymbol
        color={watchlist ? theme?.buttonText ?? '#FFFFFF' : done ? '#8A4B1C' : Colors.light.mutedText}
        name={iconName}
        size={16}
      />
      <Text
        numberOfLines={1}
        style={[
          styles.actionText,
          watchlist ? { color: theme?.buttonText ?? '#FFFFFF' } : undefined,
          done ? styles.doneText : undefined,
        ]}>
        {label}
      </Text>
    </Pressable>
  );
}

function RemainingStocksSection({
  onOpenStock,
  stocks,
}: {
  onOpenStock: (symbol: string) => void;
  stocks: RemainingStockResponse[];
}) {
  const rows = stocks.slice(0, 8);

  if (rows.length === 0) {
    return null;
  }

  return (
    <View style={styles.remainingSection}>
      <Text selectable style={styles.remainingTitle}>
        Other supported stocks
      </Text>
      <View style={styles.remainingRows}>
        {rows.map((stock, index) => (
          <RemainingRow
            isLast={index === rows.length - 1}
            key={stock.symbol}
            onPress={() => onOpenStock(stock.symbol)}
            stock={stock}
          />
        ))}
      </View>
    </View>
  );
}

function RemainingRow({
  isLast,
  onPress,
  stock,
}: {
  isLast: boolean;
  onPress: () => void;
  stock: RemainingStockResponse;
}) {
  const display = getDisplayValues(stock);
  const color = getMovementColor(display.percentChange);
  const priceText = toNumber(display.price) === null ? 'Unavailable' : formatPrice(display.price);

  return (
    <Pressable
      accessibilityHint={`Opens ${stock.symbol} stock detail.`}
      accessibilityLabel={`${stock.symbol} stock detail`}
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.remainingRow,
        isLast ? styles.remainingRowLast : undefined,
        pressed ? styles.remainingRowPressed : undefined,
      ]}>
      <View style={styles.remainingIdentity}>
        <Text selectable numberOfLines={1} style={styles.remainingSymbol}>
          {stock.symbol}
        </Text>
        <Text selectable numberOfLines={1} style={styles.remainingCompany}>
          {stock.companyName}
        </Text>
      </View>
      <View style={styles.remainingQuote}>
        <Text selectable numberOfLines={1} style={[styles.remainingPrice, { color }]}>
          {priceText}
        </Text>
        <Text selectable numberOfLines={1} style={[styles.remainingPercent, { color }]}>
          {formatPercent(display.percentChange)}
        </Text>
      </View>
      <IconSymbol color={Colors.light.mutedText} name="chevron.right" size={18} />
    </Pressable>
  );
}

function EmptySuggestions({
  cooldownText,
  onRefresh,
  refreshDisabled,
  refreshLabel,
}: {
  cooldownText: string | null;
  onRefresh: () => void;
  refreshDisabled: boolean;
  refreshLabel: string;
}) {
  return (
    <View style={styles.emptyWrap}>
      <EmptyState
        title="No cached suggestions yet"
        description="Request an updated educational suggestion batch after completing onboarding or when refresh is available."
      />
      <Text selectable style={styles.safetyText}>
        StockMentor shows learning suggestions only, not real financial advice.
      </Text>
      {cooldownText ? (
        <Text selectable style={styles.cooldownText}>
          {cooldownText}
        </Text>
      ) : null}
      <ActionButton disabled={refreshDisabled} label={refreshLabel} onPress={onRefresh} />
    </View>
  );
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <View style={styles.errorWrap}>
      <ErrorBanner title="Suggestions need attention" message={message} />
      <ActionButton label="Retry cached suggestions" onPress={onRetry} />
    </View>
  );
}

function InfoPill({ label, tone }: { label: string; tone: 'blue' | 'green' | 'amber' | 'violet' }) {
  return (
    <View
      style={[
        styles.infoPill,
        tone === 'blue' ? styles.bluePill : undefined,
        tone === 'green' ? styles.greenPill : undefined,
        tone === 'amber' ? styles.amberPill : undefined,
        tone === 'violet' ? styles.violetPill : undefined,
      ]}>
      <Text selectable style={[styles.infoPillText, tone === 'green' ? styles.greenPillText : undefined]}>
        {label}
      </Text>
    </View>
  );
}

function normalizeSuggestionResponse(response: StockAiSuggestionResponse): StockAiSuggestionResponse {
  return {
    ...response,
    suggestedStocks: response.suggestedStocks ?? [],
    remainingStocks: response.remainingStocks ?? [],
  };
}

function getDisplayValues(stock: AiSuggestionQuoteFields) {
  return {
    price: stock.displayedPrice ?? stock.delayedPriceMetadata?.displayedPrice ?? stock.currentPrice,
    percentChange:
      stock.displayedPercentChange ??
      stock.delayedPriceMetadata?.displayedPercentChange ??
      stock.percentChange,
  };
}

function getCooldownText(response: StockAiSuggestionResponse | null) {
  if (!response) {
    return null;
  }

  if (response.refreshAllowed === true) {
    return 'AI refresh is available when you want an updated educational batch.';
  }

  if (response.nextRefreshAllowedAt) {
    return `AI refresh available after ${formatBackendDateTime(response.nextRefreshAllowedAt)}.`;
  }

  if (response.refreshAllowed === false) {
    return 'AI refresh is temporarily unavailable.';
  }

  return null;
}

function formatBatchMeta(response: StockAiSuggestionResponse) {
  const parts = [
    response.analysisTimeframe ? `${response.analysisTimeframe} analysis` : null,
    response.generatedAt ? `Generated ${formatBackendDateTime(response.generatedAt)}` : null,
    response.expiresAt ? `Expires ${formatBackendDateTime(response.expiresAt)}` : null,
  ].filter(Boolean);

  return parts.length > 0 ? parts.join(' | ') : 'Stored suggestion batch';
}

function themeForRank(rankNo: number) {
  if (rankNo === 1) {
    return RANK_THEMES.gold;
  }
  if (rankNo === 2) {
    return RANK_THEMES.silver;
  }
  return RANK_THEMES.bronze;
}

const styles = StyleSheet.create({
  actionButton: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderColor: Colors.light.border,
    borderRadius: 999,
    borderWidth: 1,
    flex: 1,
    flexDirection: 'row',
    gap: Spacing.xs,
    justifyContent: 'center',
    minHeight: 44,
    paddingHorizontal: Spacing.sm,
  },
  actionDisabled: {
    opacity: 0.56,
  },
  actionPressed: {
    opacity: 0.82,
    transform: [{ scale: 0.99 }],
  },
  actionText: {
    color: Colors.light.text,
    flexShrink: 1,
    fontSize: 13,
    fontWeight: '800',
  },
  amberPill: {
    backgroundColor: '#FFEDD5',
    borderColor: '#FDBA74',
  },
  bluePill: {
    backgroundColor: '#DBEAFE',
    borderColor: '#BFDBFE',
  },
  cardActions: {
    flexDirection: 'row',
    gap: Spacing.sm,
    paddingBottom: Spacing.md,
    paddingHorizontal: Spacing.md,
  },
  cardBody: {
    gap: Spacing.md,
    padding: Spacing.md,
  },
  cardHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 82,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
  },
  cardPressable: {
    overflow: 'hidden',
  },
  cardPressed: {
    backgroundColor: '#F8FAFC',
  },
  cardShell: {
    backgroundColor: Colors.light.surface,
    borderColor: '#E5E7EB',
    borderRadius: 18,
    borderWidth: 1,
    elevation: 2,
    overflow: 'hidden',
    shadowColor: '#0F172A',
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.08,
    shadowRadius: 8,
  },
  company: {
    color: Colors.light.mutedText,
    fontSize: 12,
    lineHeight: 16,
  },
  container: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  content: {
    gap: Spacing.md,
    paddingBottom: Spacing.xxl,
    paddingHorizontal: Spacing.md,
    paddingTop: Spacing.md,
  },
  cooldownText: {
    color: Colors.light.mutedText,
    fontSize: 13,
    lineHeight: 18,
  },
  detailReason: {
    color: Colors.light.mutedText,
    fontSize: 14,
    lineHeight: 21,
  },
  divider: {
    height: 1,
  },
  doneButton: {
    backgroundColor: '#FFF4E6',
    borderColor: '#D99A63',
  },
  doneText: {
    color: '#8A4B1C',
  },
  emptyWrap: {
    gap: Spacing.md,
  },
  errorWrap: {
    gap: Spacing.md,
  },
  fallbackNotice: {
    backgroundColor: '#FFF7ED',
    borderColor: '#FED7AA',
    borderRadius: Radius.sm,
    borderWidth: 1,
    padding: Spacing.sm,
  },
  fallbackText: {
    color: Colors.light.text,
    fontSize: 13,
    lineHeight: 18,
  },
  fixedHeader: {
    backgroundColor: Colors.light.background,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
  },
  greenPill: {
    backgroundColor: '#DCFCE7',
    borderColor: '#86EFAC',
  },
  greenPillText: {
    color: '#047857',
  },
  identity: {
    flex: 1,
    gap: 2,
    minWidth: 0,
  },
  infoPill: {
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: Spacing.sm,
    paddingVertical: 6,
  },
  infoPillText: {
    color: Colors.light.text,
    fontSize: 12,
    fontWeight: '800',
  },
  labelWrap: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.xs,
  },
  messageText: {
    color: Colors.light.mutedText,
    fontSize: 13,
    lineHeight: 18,
  },
  quoteInline: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
    maxWidth: 112,
  },
  rankBadge: {
    alignItems: 'center',
    borderRadius: 999,
    elevation: 2,
    height: 40,
    justifyContent: 'center',
    shadowColor: '#0F172A',
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.12,
    shadowRadius: 6,
    width: 40,
  },
  rankText: {
    fontSize: 14,
    fontVariant: ['tabular-nums'],
    fontWeight: '900',
  },
  reasonBlock: {
    gap: Spacing.xs,
  },
  reasonLabel: {
    fontSize: 11,
    fontWeight: '900',
  },
  refreshButton: {
    minHeight: 38,
    paddingHorizontal: Spacing.md,
  },
  remainingCompany: {
    color: Colors.light.mutedText,
    fontSize: 12,
  },
  remainingIdentity: {
    flex: 1,
    gap: 2,
    minWidth: 0,
  },
  remainingPercent: {
    fontSize: 12,
    fontVariant: ['tabular-nums'],
    fontWeight: '700',
  },
  remainingPrice: {
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '800',
  },
  remainingQuote: {
    alignItems: 'flex-end',
    width: 110,
  },
  remainingRow: {
    alignItems: 'center',
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 56,
    paddingHorizontal: Spacing.xs,
    paddingVertical: Spacing.sm,
  },
  remainingRowLast: {
    borderBottomWidth: 0,
  },
  remainingRowPressed: {
    backgroundColor: '#F8FAFC',
  },
  remainingRows: {
    overflow: 'hidden',
  },
  remainingSection: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.sm,
    padding: Spacing.md,
  },
  remainingSymbol: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '900',
  },
  remainingTitle: {
    color: Colors.light.text,
    fontSize: 18,
    fontWeight: '900',
  },
  safetyText: {
    color: Colors.light.mutedText,
    fontSize: 12,
    lineHeight: 17,
  },
  scoreBox: {
    alignItems: 'center',
    borderRadius: Radius.md,
    elevation: 2,
    justifyContent: 'center',
    minWidth: 54,
    paddingHorizontal: Spacing.sm,
    paddingVertical: Spacing.xs,
    shadowColor: '#0F172A',
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.14,
    shadowRadius: 6,
  },
  scoreLabel: {
    fontSize: 10,
    fontWeight: '900',
  },
  scoreNumber: {
    fontSize: 16,
    fontVariant: ['tabular-nums'],
    fontWeight: '900',
    lineHeight: 19,
  },
  scroller: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  shortReason: {
    color: Colors.light.text,
    fontSize: 16,
    fontWeight: '900',
    lineHeight: 22,
  },
  suggestionList: {
    gap: Spacing.md,
  },
  summary: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.sm,
    padding: Spacing.md,
  },
  summaryCopy: {
    flex: 1,
    gap: Spacing.xs,
  },
  summaryHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    gap: Spacing.md,
  },
  summaryMeta: {
    color: Colors.light.mutedText,
    fontSize: 12,
    lineHeight: 17,
  },
  summaryText: {
    color: Colors.light.text,
    fontSize: 14,
    lineHeight: 20,
  },
  summaryTitle: {
    color: Colors.light.text,
    fontSize: 16,
    fontWeight: '900',
  },
  symbol: {
    color: Colors.light.text,
    fontSize: 18,
    fontWeight: '900',
    lineHeight: 22,
  },
  topQuoteText: {
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '600',
  },
  violetPill: {
    backgroundColor: '#EDE9FE',
    borderColor: '#DDD6FE',
  },
});
