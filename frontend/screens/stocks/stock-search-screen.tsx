import { useFocusEffect } from '@react-navigation/native';
import { type Href, useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useMemo, useRef, useState } from 'react';
import {
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { stocksApi } from '@/api/stocks';
import { watchlistApi } from '@/api/watchlist';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import { SearchFallbackTableRow, SearchQuoteRow } from '@/components/stocks/stock-table-row';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import { useMinuteBoundaryRefresh } from '@/hooks/use-minute-boundary-refresh';
import { useAuthSession } from '@/providers/auth-session-provider';
import { useToast } from '@/providers/toast-provider';
import type { StockListItemResponse } from '@/types/stocks';
import { loadRecentViewedStockSymbols } from '@/utils/recent-stocks';
import { safeGetItem, safeSetItem } from '@/utils/safe-storage';
import { getStockApiErrorMessage } from '@/utils/stock-display';

const SEARCH_HISTORY_KEY = 'stockmentor.searchHistory.v1';
const MAX_SEARCH_HISTORY = 10;

export function StockSearchScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { from, symbol } = useLocalSearchParams<{ from?: string; symbol?: string }>();
  const { credentials } = useAuthSession();
  const { showToast } = useToast();
  const listRef = useRef<FlatList<StockListItemResponse> | null>(null);
  const requestInFlightRef = useRef(false);
  const hasLoadedRef = useRef(false);
  const [stocks, setStocks] = useState<StockListItemResponse[]>([]);
  const [history, setHistory] = useState<string[]>([]);
  const [recentSymbols, setRecentSymbols] = useState<string[]>([]);
  const [query, setQuery] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [pendingWatchlistSymbols, setPendingWatchlistSymbols] = useState<Set<string>>(new Set());

  const loadStocks = useCallback(async () => {
    if (requestInFlightRef.current) {
      return;
    }

    if (!credentials) {
      setErrorMessage('Sign in again to search stocks.');
      setIsLoading(false);
      hasLoadedRef.current = true;
      return;
    }

    requestInFlightRef.current = true;
    if (!hasLoadedRef.current) {
      setIsLoading(true);
    }
    setErrorMessage(null);

    try {
      const response = await stocksApi.getStocks(credentials);
      setStocks(response.stocks ?? []);
    } catch (error) {
      setErrorMessage(getStockApiErrorMessage(error, 'Stock search could not be loaded.'));
    } finally {
      requestInFlightRef.current = false;
      hasLoadedRef.current = true;
      setIsLoading(false);
    }
  }, [credentials]);

  const loadHistory = useCallback(async () => {
    try {
      const stored = await safeGetItem(SEARCH_HISTORY_KEY);
      const parsed = stored ? JSON.parse(stored) : [];
      setHistory(Array.isArray(parsed) ? parsed.filter((item) => typeof item === 'string') : []);
    } catch {
      setHistory([]);
    }
  }, []);

  const loadRecentSymbols = useCallback(async () => {
    setRecentSymbols(await loadRecentViewedStockSymbols());
  }, []);

  const saveHistory = useCallback(async (items: string[]) => {
    setHistory(items);
    await safeSetItem(SEARCH_HISTORY_KEY, JSON.stringify(items));
  }, []);

  useFocusEffect(
    useCallback(() => {
      listRef.current?.scrollToOffset({ animated: false, offset: 0 });
      setQuery('');
      void loadHistory();
      void loadRecentSymbols();
      void loadStocks();
      return undefined;
    }, [loadHistory, loadRecentSymbols, loadStocks]),
  );

  useMinuteBoundaryRefresh({
    onRefresh: loadStocks,
  });

  const recentRows = useMemo(() => {
    if (recentSymbols.length === 0) {
      return [];
    }

    const stockBySymbol = new Map(stocks.map((stock) => [stock.symbol, stock]));
    return recentSymbols
      .map((recentSymbol) => stockBySymbol.get(recentSymbol))
      .filter((stock): stock is StockListItemResponse => Boolean(stock));
  }, [recentSymbols, stocks]);

  const rows = useMemo(() => {
    const trimmedQuery = query.trim().toLowerCase();
    if (!trimmedQuery) {
      return recentRows;
    }

    return stocks.filter((stock) => {
      return (
        stock.symbol.toLowerCase().includes(trimmedQuery) ||
        stock.companyName.toLowerCase().includes(trimmedQuery)
      );
    });
  }, [query, recentRows, stocks]);

  const detailReturnContext = useMemo(() => {
    if (from === 'stocks' || from === 'watchlist' || from === 'detail') {
      return {
        returnTo: 'search-context' as const,
        searchFrom: from,
        searchSymbol: symbol,
      };
    }

    return { returnTo: 'search-tab' as const };
  }, [from, symbol]);

  const handleBack = () => {
    if (from === 'stocks') {
      router.replace('/stocks' as Href);
      return;
    }

    if (from === 'detail' && symbol) {
      router.replace({
        pathname: '/stocks/[symbol]',
        params: { symbol },
      } as Href);
      return;
    }

    if (from === 'watchlist') {
      router.replace('/dashboard' as Href);
      return;
    }

    router.replace('/stocks' as Href);
  };

  const commitSearch = async () => {
    const trimmed = query.trim();
    if (!trimmed) {
      return;
    }

    const nextHistory = [trimmed, ...history.filter((item) => item.toLowerCase() !== trimmed.toLowerCase())].slice(
      0,
      MAX_SEARCH_HISTORY,
    );
    await saveHistory(nextHistory);
  };

  const clearHistory = async () => {
    await saveHistory([]);
  };

  const handleToggleWatchlist = async (stock: StockListItemResponse) => {
    if (!credentials || pendingWatchlistSymbols.has(stock.symbol)) {
      return;
    }

    setPendingWatchlistSymbols((current) => new Set(current).add(stock.symbol));
    const isWatchlisted = Boolean(stock.isWatchlisted);

    try {
      const response = isWatchlisted
        ? await watchlistApi.removeSymbol(credentials, stock.symbol)
        : await watchlistApi.addSymbol(credentials, stock.symbol);
      const nextWatchlisted = response.stock?.isWatchlisted ?? !isWatchlisted;
      setStocks((current) =>
        current.map((item) =>
          item.symbol === stock.symbol ? { ...item, isWatchlisted: nextWatchlisted } : item,
        ),
      );
      showToast(`${stock.symbol} ${nextWatchlisted ? 'added to' : 'removed from'} watchlist.`, 'success');
    } catch (error) {
      showToast(getStockApiErrorMessage(error, 'Watchlist could not be updated.'), 'error');
    } finally {
      setPendingWatchlistSymbols((current) => {
        const next = new Set(current);
        next.delete(stock.symbol);
        return next;
      });
    }
  };

  const queryIsActive = query.trim().length > 0;
  const hasRecentRows = recentRows.length > 0;

  const listHeader = (
    <View>
      {errorMessage ? <ErrorBanner title="Search needs attention" message={errorMessage} /> : null}

      {!queryIsActive && history.length > 0 ? (
        <View style={styles.historySection}>
          <View style={styles.historyHeader}>
            <Text selectable style={styles.sectionTitle}>
              Search History
            </Text>
            <Pressable
              accessibilityHint="Clears stored search history from this device."
              accessibilityLabel="Clear search history"
              accessibilityRole="button"
              onPress={() => void clearHistory()}
              style={styles.historyClear}>
              <IconSymbol color={Colors.light.mutedText} name="trash" size={20} />
            </Pressable>
          </View>
          <View style={styles.historyChips}>
            {history.map((term) => (
              <Pressable
                accessibilityLabel={`Search for ${term}`}
                accessibilityRole="button"
                key={term}
                onPress={() => setQuery(term)}
                style={({ pressed }) => [styles.historyChip, pressed ? styles.pressed : undefined]}>
                <Text style={styles.historyChipText}>{term}</Text>
              </Pressable>
            ))}
          </View>
        </View>
      ) : null}

      {!queryIsActive ? (
        hasRecentRows ? (
          <View style={styles.fallbackHeader}>
            <Text selectable style={styles.latestViewedTitle}>
              Latest Viewed Stocks
            </Text>
          </View>
        ) : null
      ) : null}

      {isLoading ? <SkeletonRows count={5} /> : null}
      {!isLoading && !queryIsActive && hasRecentRows ? (
        <View style={styles.tableHeader}>
          <Text style={[styles.tableHeaderText, styles.tableHeaderNo]}>No.</Text>
          <Text style={[styles.tableHeaderText, styles.tableHeaderIdentity]}>Symbol</Text>
          <Text style={[styles.tableHeaderText, styles.tableHeaderPrice]}>Price</Text>
          <Text style={[styles.tableHeaderText, styles.tableHeaderChange]}>Chg %</Text>
        </View>
      ) : null}
    </View>
  );

  return (
    <View style={styles.container}>
      <View style={[styles.fixedArea, { paddingTop: insets.top + 2 }]}>
        <View style={styles.searchRow}>
          <Pressable
            accessibilityHint="Returns to the previous page."
            accessibilityLabel="Back"
            accessibilityRole="button"
            onPress={handleBack}
            style={({ pressed }) => [styles.backButton, pressed ? styles.pressed : undefined]}>
            <IconSymbol color={Colors.light.text} name="chevron.left" size={24} />
          </Pressable>
          <View style={styles.inputWrap}>
            <TextInput
              accessibilityHint="Searches supported stock symbols and company names."
              accessibilityLabel="Search supported stocks"
              autoCapitalize="characters"
              onChangeText={setQuery}
              placeholder="Search symbol or company"
              placeholderTextColor={Colors.light.mutedText}
              style={styles.searchInput}
              value={query}
            />
            {query ? (
              <Pressable
                accessibilityLabel="Clear search input"
                accessibilityRole="button"
                onPress={() => setQuery('')}
                style={styles.clearInput}>
                <IconSymbol color={Colors.light.mutedText} name="xmark.circle.fill" size={18} />
              </Pressable>
            ) : null}
          </View>
          <Pressable
            accessibilityHint="Saves this search term to search history."
            accessibilityLabel="Search"
            accessibilityRole="button"
            onPress={() => void commitSearch()}
            style={styles.searchAction}>
            <Text style={styles.searchActionText}>Search</Text>
          </Pressable>
        </View>
        {!isLoading && queryIsActive ? (
          <View style={styles.quoteHeader}>
            <Text selectable style={styles.sectionTitle}>
              Quotes
            </Text>
          </View>
        ) : null}
      </View>

      <FlatList
        ref={listRef}
        ListEmptyComponent={
          isLoading ? null : (
            <View style={styles.emptyState}>
              <Text selectable style={styles.emptyTitle}>
                {queryIsActive ? `No results for "${query.trim()}"` : 'No recently viewed stocks yet'}
              </Text>
              <Text selectable style={styles.emptyDescription}>
                {queryIsActive
                  ? 'Try another supported symbol or company.'
                  : 'Open a stock detail page and it will appear here.'}
              </Text>
            </View>
          )
        }
        ListHeaderComponent={listHeader}
        contentContainerStyle={[
          styles.listContent,
          {
            paddingBottom: Math.max(Spacing.xxl, insets.bottom + Spacing.xl),
          },
        ]}
        contentInsetAdjustmentBehavior="never"
        data={isLoading ? [] : rows}
        keyExtractor={(item) => item.symbol}
        keyboardShouldPersistTaps="handled"
        overScrollMode="never"
        renderItem={({ index, item }) =>
          queryIsActive ? (
            <SearchQuoteRow
              detailReturnContext={detailReturnContext}
              onToggleWatchlist={handleToggleWatchlist}
              pending={pendingWatchlistSymbols.has(item.symbol)}
              stock={item}
            />
          ) : (
            <SearchFallbackTableRow
              detailReturnContext={detailReturnContext}
              rowNumber={index + 1}
              stock={item}
            />
          )
        }
        style={styles.scroller}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  listContent: {
    gap: 0,
    paddingHorizontal: 0,
    width: '100%',
  },
  fixedArea: {
    backgroundColor: Colors.light.background,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
  },
  scroller: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  searchRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 56,
    paddingHorizontal: Spacing.md,
  },
  backButton: {
    alignItems: 'center',
    height: 44,
    justifyContent: 'center',
    width: 32,
  },
  inputWrap: {
    flex: 1,
    justifyContent: 'center',
  },
  searchInput: {
    backgroundColor: '#F1F5F9',
    borderRadius: 8,
    color: Colors.light.text,
    fontSize: 16,
    minHeight: 44,
    paddingHorizontal: Spacing.lg,
    paddingRight: 40,
  },
  clearInput: {
    alignItems: 'center',
    height: 36,
    justifyContent: 'center',
    position: 'absolute',
    right: 2,
    width: 36,
  },
  searchAction: {
    alignItems: 'center',
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: Spacing.xs,
  },
  searchActionText: {
    color: Colors.light.text,
    fontSize: 16,
    fontWeight: '600',
  },
  historySection: {
    backgroundColor: Colors.light.background,
    gap: Spacing.md,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.lg,
  },
  historyHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  sectionTitle: {
    color: Colors.light.text,
    fontSize: 18,
    fontWeight: '800',
  },
  historyClear: {
    alignItems: 'center',
    height: 36,
    justifyContent: 'center',
    width: 36,
  },
  historyChips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  historyChip: {
    backgroundColor: '#F1F5F9',
    borderRadius: 8,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.sm,
  },
  historyChipText: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '500',
  },
  fallbackHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.xs,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
  },
  latestViewedTitle: {
    color: Colors.light.text,
    fontSize: 18,
    fontWeight: '700',
  },
  quoteHeader: {
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  tableHeader: {
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    borderTopColor: Colors.light.border,
    borderTopWidth: 1,
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 38,
    paddingHorizontal: Spacing.md,
  },
  tableHeaderText: {
    color: Colors.light.mutedText,
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  tableHeaderNo: {
    width: 28,
  },
  tableHeaderIdentity: {
    flex: 1,
  },
  tableHeaderPrice: {
    width: 92,
    textAlign: 'right',
  },
  tableHeaderChange: {
    width: 72,
    textAlign: 'right',
  },
  emptyState: {
    backgroundColor: Colors.light.surface,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    gap: Spacing.xs,
    padding: Spacing.lg,
  },
  emptyTitle: {
    color: Colors.light.text,
    fontSize: 15,
    fontWeight: '700',
  },
  emptyDescription: {
    color: Colors.light.mutedText,
    fontSize: 13,
    lineHeight: 18,
  },
  pressed: {
    opacity: 0.82,
  },
});
