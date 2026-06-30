import { useFocusEffect } from '@react-navigation/native';
import { type Href, useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
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
import { useMinuteBoundaryRefresh } from '@/hooks/use-minute-boundary-refresh';
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

type LoadMode = 'initial' | 'refresh' | 'background';
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
  doneBackground: string;
  doneBorder: string;
  doneText: string;
  divider: string;
  headerBackground: string;
  scoreBackground: string;
  scoreLabelText: string;
  scoreText: string;
};

const LONG_TOAST_MS = 3600;

const RANK_THEMES: Record<'gold' | 'silver' | 'bronze', RankTheme> = {
  gold: {
    accent: '#C89205',
    badgeBackground: '#E2BE3F',
    badgeText: '#4A3700',
    buttonBackground: '#D9AA14',
    buttonBorder: '#C79506',
    buttonText: '#FFFFFF',
    doneBackground: '#FBF2D2',
    doneBorder: '#E4C977',
    doneText: '#8A6A12',
    divider: '#D5AA27',
    headerBackground: '#FFF9E8',
    scoreBackground: '#D8A20E',
    scoreLabelText: '#9A7414',
    scoreText: '#FFFFFF',
  },
  silver: {
    accent: '#7D8795',
    badgeBackground: '#CBD5E1',
    badgeText: '#263241',
    buttonBackground: '#8793A4',
    buttonBorder: '#758295',
    buttonText: '#FFFFFF',
    doneBackground: '#EEF1F5',
    doneBorder: '#CBD2DE',
    doneText: '#5A6573',
    divider: '#CBD5E1',
    headerBackground: '#F8FAFC',
    scoreBackground: '#94A3B8',
    scoreLabelText: '#6B7686',
    scoreText: '#FFFFFF',
  },
  bronze: {
    accent: '#B76C2D',
    badgeBackground: '#D99557',
    badgeText: '#4D260B',
    buttonBackground: '#F8EAD8',
    buttonBorder: '#DA9B65',
    buttonText: '#8A4B1C',
    doneBackground: '#FAEEE0',
    doneBorder: '#E1C09A',
    doneText: '#8A5526',
    divider: '#D99A63',
    headerBackground: '#FFF7ED',
    scoreBackground: '#C8793C',
    scoreLabelText: '#9A6234',
    scoreText: '#FFFFFF',
  },
};

export function AiSuggestionsScreen() {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const { credentials, user } = useAuthSession();
  const { hideToast, showToast } = useToast();
  const guardedRefresh = useRefreshCooldown();
  const requestInFlightRef = useRef(false);
  const writeInFlightRef = useRef(false);
  const hasLoadedRef = useRef(false);
  const scrollRef = useRef<ScrollView | null>(null);
  const responseWriteVersionRef = useRef(0);
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
      } else if (mode === 'initial' && !hasLoadedRef.current) {
        setIsLoading(true);
      }
      setErrorMessage(null);

      const requestVersion = ++responseWriteVersionRef.current;
      try {
        const nextResponse = await aiSuggestionsApi.getSuggestions(credentials);
        const normalizedResponse = normalizeSuggestionResponse(nextResponse);
        if (requestVersion === responseWriteVersionRef.current) {
          setResponse(normalizedResponse);
        }
      } catch (error) {
        if (requestVersion === responseWriteVersionRef.current) {
          setErrorMessage(getStockApiErrorMessage(error, 'AI suggestions could not be loaded.'));
        }
      } finally {
        if (requestVersion === responseWriteVersionRef.current) {
          hasLoadedRef.current = true;
          requestInFlightRef.current = false;
          setIsLoading(false);
          setIsRefreshing(false);
        }
      }
    },
    [credentials],
  );

  useEffect(() => {
    hasLoadedRef.current = false;
    requestInFlightRef.current = false;
    writeInFlightRef.current = false;
    responseWriteVersionRef.current += 1;
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

  useMinuteBoundaryRefresh({
    enabled: Boolean(credentials),
    onRefresh: async () => {
      await loadSuggestions('background');
    },
  });

  const suggestedStocks = response?.suggestedStocks ?? [];
  const remainingStocks = response?.remainingStocks ?? [];
  const readInFlight = isLoading || isRefreshing;
  const writeInFlight = isRefreshingBatch || Boolean(pendingAction);
  const refreshDisabled = readInFlight || writeInFlight;
  const itemActionsDisabled = readInFlight || writeInFlight;
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
    scrollRef.current?.scrollTo({ animated: true, y: 0 });
    if (!credentials || readInFlight || writeInFlight || requestInFlightRef.current || writeInFlightRef.current) {
      return;
    }

    writeInFlightRef.current = true;
    const writeVersion = ++responseWriteVersionRef.current;
    hideToast();
    setIsRefreshingBatch(true);
    try {
      const cachedResponse = normalizeSuggestionResponse(await aiSuggestionsApi.getSuggestions(credentials));
      const hasVisibleSuggestions = (response?.suggestedStocks?.length ?? 0) > 0;
      if (!hasVisibleSuggestions && writeVersion === responseWriteVersionRef.current) {
        setResponse(cachedResponse);
      }
      if (cachedResponse.refreshAllowed !== true) {
        if (writeVersion === responseWriteVersionRef.current) {
          showToast(cooldownToastMessage(cachedResponse), 'neutral', LONG_TOAST_MS);
        }
        return;
      }

      const nextResponse = await aiSuggestionsApi.refreshSuggestions(credentials);
      const normalizedResponse = normalizeSuggestionResponse(nextResponse);
      if (writeVersion === responseWriteVersionRef.current) {
        setResponse(normalizedResponse);
        setErrorMessage(null);
        showToast(
          refreshToastMessage(normalizedResponse),
          normalizedResponse.refreshAllowed === false
            || normalizedResponse.fallbackUsed
            || normalizedResponse.batchStatus?.startsWith('FALLBACK')
            ? 'neutral'
            : 'success',
          LONG_TOAST_MS,
        );
      }
    } catch (error) {
      if (writeVersion === responseWriteVersionRef.current) {
        showToast(getStockApiErrorMessage(error, 'Suggestions could not be refreshed.'), 'error', LONG_TOAST_MS);
      }
    } finally {
      if (writeVersion === responseWriteVersionRef.current) {
        writeInFlightRef.current = false;
        setIsRefreshingBatch(false);
      }
    }
  };

  const handleDismiss = async (item: SuggestedStockResponse) => {
    if (!credentials || pendingAction || isRefreshingBatch || isRefreshing || requestInFlightRef.current || writeInFlightRef.current) {
      return;
    }

    writeInFlightRef.current = true;
    const writeVersion = ++responseWriteVersionRef.current;
    setPendingAction({ itemId: item.itemId, type: 'dismiss' });
    try {
      const nextResponse = await aiSuggestionsApi.dismissSuggestion(credentials, item.itemId);
      if (writeVersion === responseWriteVersionRef.current) {
        setResponse(normalizeSuggestionResponse(nextResponse));
        showToast(`${item.symbol} dismissed from suggestions.`, 'success');
      }
    } catch (error) {
      if (writeVersion === responseWriteVersionRef.current) {
        showToast(getStockApiErrorMessage(error, `${item.symbol} could not be dismissed.`), 'error');
      }
    } finally {
      if (writeVersion === responseWriteVersionRef.current) {
        writeInFlightRef.current = false;
        setPendingAction(null);
      }
    }
  };

  const handleWatchlist = async (item: SuggestedStockResponse) => {
    if (
      !credentials ||
      pendingAction ||
      isRefreshingBatch ||
      isRefreshing ||
      requestInFlightRef.current ||
      writeInFlightRef.current
    ) {
      return;
    }

    const removeFromWatchlist = item.isWatchlisted === true;
    writeInFlightRef.current = true;
    const writeVersion = ++responseWriteVersionRef.current;
    setPendingAction({ itemId: item.itemId, type: 'watchlist' });
    try {
      const nextResponse = await aiSuggestionsApi.watchlistSuggestion(credentials, item.itemId);
      if (writeVersion === responseWriteVersionRef.current) {
        setResponse(normalizeSuggestionResponse(nextResponse));
        showToast(
          `${item.symbol} ${removeFromWatchlist ? 'removed from' : 'added to'} watchlist.`,
          'success',
        );
      }
    } catch (error) {
      if (writeVersion === responseWriteVersionRef.current) {
        showToast(
          getStockApiErrorMessage(
            error,
            `${item.symbol} could not be ${removeFromWatchlist ? 'removed from' : 'added to'} watchlist.`,
          ),
          'error',
        );
      }
    } finally {
      if (writeVersion === responseWriteVersionRef.current) {
        writeInFlightRef.current = false;
        setPendingAction(null);
      }
    }
  };

  return (
    <View style={styles.container}>
      <View style={[styles.fixedHeader, { paddingTop: insets.top + 2 }]}>
        <PaperHeader
          brandIcon
          onRefresh={handleRefreshSuggestions}
          refreshAccessibilityLabel="Refresh AI suggestions"
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
            <EmptySuggestions />
            <RemainingStocksSection onOpenStock={openStock} stocks={remainingStocks} />
          </>
        ) : (
          <>
            <SuggestionStatus fallbackVisible={fallbackVisible} />
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
      {isRefreshingBatch ? (
        <View pointerEvents="none" style={styles.generatingOverlay}>
          <View style={styles.generatingCard}>
            <ActivityIndicator color={Colors.light.brandNavy} size="large" />
            <Text selectable style={styles.generatingText}>
              Generating AI stock suggestions...
            </Text>
          </View>
        </View>
      ) : null}
    </View>
  );
}

function SuggestionStatus({
  fallbackVisible,
}: {
  fallbackVisible: boolean;
}) {
  if (!fallbackVisible) {
    return null;
  }

  return (
    <View style={styles.statusStack}>
      <View style={styles.fallbackNotice}>
        <Text selectable style={styles.fallbackText}>
          AI suggestions are temporarily unavailable, so fallback learning examples are shown.
        </Text>
      </View>
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
          <View style={styles.headerLeft}>
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
          </View>
          {item.matchScore !== null && item.matchScore !== undefined ? (
            <View style={styles.scoreStack}>
              <View style={[styles.scoreBox, { backgroundColor: theme.scoreBackground }]}>
                <Text selectable style={[styles.scoreNumber, { color: theme.scoreText }]}>
                  {item.matchScore}%
                </Text>
              </View>
              <Text selectable style={[styles.scoreLabel, { color: theme.scoreLabelText }]}>
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
            {alreadyWatchlisted ? <InfoPill label="Watchlisted" tone="green" /> : null}
          </View>

          {item.shortReason ? (
            <Text selectable style={styles.shortReason}>
              {item.shortReason}
            </Text>
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
          disabled={actionsDisabled}
          iconName={alreadyWatchlisted ? 'checkmark' : 'plus'}
          label={
            alreadyWatchlisted
              ? currentAction === 'watchlist'
                ? 'Removing...'
                : 'Watchlisted'
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
        done
          ? {
              backgroundColor: theme?.doneBackground ?? '#FFF4E6',
              borderColor: theme?.doneBorder ?? '#D99A63',
            }
          : undefined,
        pressed && !disabled ? styles.actionPressed : undefined,
        disabled ? styles.actionDisabled : undefined,
      ]}>
      <IconSymbol
        color={watchlist ? theme?.buttonText ?? '#FFFFFF' : done ? theme?.doneText ?? '#8A4B1C' : Colors.light.mutedText}
        name={iconName}
        size={16}
      />
      <Text
        numberOfLines={1}
        style={[
          styles.actionText,
          watchlist ? { color: theme?.buttonText ?? '#FFFFFF' } : undefined,
          done ? { color: theme?.doneText ?? '#8A4B1C' } : undefined,
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
        Other supported stocks:
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

function EmptySuggestions() {
  return (
    <View style={styles.emptyWrap}>
      <EmptyState
        title="No cached suggestions yet"
        description="Complete onboarding, then use the header refresh to request learning suggestions."
      />
      <Text selectable style={styles.safetyText}>
        StockMentor shows learning suggestions only, not real financial advice.
      </Text>
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

function InfoPill({ label, tone }: { label: string; tone: 'blue' | 'green' | 'amber' }) {
  return (
    <View
      style={[
        styles.infoPill,
        tone === 'blue' ? styles.bluePill : undefined,
        tone === 'green' ? styles.greenPill : undefined,
        tone === 'amber' ? styles.amberPill : undefined,
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

function refreshToastMessage(response: StockAiSuggestionResponse) {
  if (response.fallbackUsed || response.batchStatus?.startsWith('FALLBACK')) {
    return 'AI Suggestions are temporarily unavailable, so a simple fallback is shown.';
  }
  const message = response.message?.trim();
  if (message === 'Returned stored AI stock suggestions') {
    return 'Returned existing suggestions because your profile and stock data are unchanged.';
  }
  if (message) {
    return message;
  }
  return 'Generated new AI Stock Suggestions.';
}

function cooldownToastMessage(response: StockAiSuggestionResponse) {
  if (response.nextRefreshAllowedAt) {
    return `AI refresh available after ${formatBackendDateTime(response.nextRefreshAllowedAt)}.`;
  }
  if (response.refreshAllowed === false) {
    return 'AI refresh is still cooling down. Try again later.';
  }
  if (response.message?.trim()) {
    return response.message.trim();
  }
  return 'AI refresh is temporarily unavailable.';
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
    gap: Spacing.md,
    justifyContent: 'space-between',
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
  detailReason: {
    color: Colors.light.mutedText,
    fontSize: 14,
    lineHeight: 21,
  },
  divider: {
    height: 1,
  },
  emptyWrap: {
    gap: Spacing.md,
  },
  errorWrap: {
    gap: Spacing.md,
  },
  fallbackNotice: {
    backgroundColor: '#FEF2F2',
    borderColor: '#FCA5A5',
    borderRadius: Radius.sm,
    borderWidth: 1,
    padding: Spacing.sm,
  },
  fallbackText: {
    color: '#7F1D1D',
    fontSize: 13,
    fontWeight: '700',
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
  generatingCard: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    elevation: 5,
    gap: Spacing.md,
    minWidth: 280,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.xl,
    shadowColor: '#0F172A',
    shadowOffset: { height: 6, width: 0 },
    shadowOpacity: 0.14,
    shadowRadius: 14,
    transform: [{ translateY: Spacing.xxl }],
  },
  generatingOverlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    backgroundColor: 'rgba(15, 23, 42, 0.16)',
    justifyContent: 'center',
    padding: Spacing.xl,
    zIndex: 5,
  },
  generatingText: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '500',
    lineHeight: 19,
    textAlign: 'center',
  },
  headerLeft: {
    alignItems: 'center',
    flex: 1,
    flexDirection: 'row',
    gap: Spacing.sm,
    minWidth: 0,
  },
  identity: {
    flexShrink: 1,
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
    fontWeight: '500',
  },
  labelWrap: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.xs,
  },
  quoteInline: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
    flexShrink: 0,
    marginLeft: Spacing.sm,
    maxWidth: 140,
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
    fontSize: 16,
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
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '500',
  },
  remainingPrice: {
    fontSize: 14,
    fontVariant: ['tabular-nums'],
    fontWeight: '500',
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
    marginTop: Spacing.xxl,
    padding: Spacing.md,
  },
  remainingSymbol: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '500',
  },
  remainingTitle: {
    color: Colors.light.text,
    fontSize: 21,
    fontWeight: '700',
    paddingTop: Spacing.xs,
    paddingBottom: Spacing.md
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
    paddingVertical: 7,
    shadowColor: '#0F172A',
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.14,
    shadowRadius: 6,
  },
  scoreLabel: {
    fontSize: 9.5,
    fontWeight: '900',
    letterSpacing: 0,
    textTransform: 'uppercase',
  },
  scoreStack: {
    alignItems: 'center',
    flexShrink: 0,
    gap: 4,
  },
  scoreNumber: {
    fontSize: 18,
    fontVariant: ['tabular-nums'],
    fontWeight: '900',
    lineHeight: 20,
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
  statusStack: {
    gap: Spacing.sm,
  },
  symbol: {
    color: Colors.light.text,
    fontSize: 16,
    fontWeight: '700',
    lineHeight: 20,
  },
  topQuoteText: {
    fontSize: 16,
    fontVariant: ['tabular-nums'],
    fontWeight: '500',
  },
});
