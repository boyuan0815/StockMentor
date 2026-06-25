import { useFocusEffect } from '@react-navigation/native';
import { Image } from 'expo-image';
import { type Href, useRouter } from 'expo-router';
import { useCallback, useMemo, useRef, useState } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { watchlistApi } from '@/api/watchlist';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import { SortIndicator } from '@/components/stocks/sort-indicator';
import { StockMarketNotice } from '@/components/stocks/stock-market-notice';
import { WatchlistTableRow } from '@/components/stocks/stock-table-row';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import { useMinuteBoundaryRefresh } from '@/hooks/use-minute-boundary-refresh';
import { useRefreshCooldown } from '@/hooks/use-refresh-cooldown';
import { useAuthSession } from '@/providers/auth-session-provider';
import type { WatchlistStockResponse } from '@/types/stocks';
import {
  getPreferredPercentChange,
  getPreferredPrice,
  getStockApiErrorMessage,
  toNumber,
} from '@/utils/stock-display';

type WatchlistTab = 'All' | 'US' | 'HK' | 'MY';
type SortKey = 'default' | 'symbol' | 'price' | 'change';
type SortDirection = 'asc' | 'desc';

export function DashboardScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { credentials } = useAuthSession();
  const guardedRefresh = useRefreshCooldown();
  const requestInFlightRef = useRef(false);
  const hasLoadedRef = useRef(false);
  const scrollRef = useRef<ScrollView | null>(null);
  const [watchlistStocks, setWatchlistStocks] = useState<WatchlistStockResponse[]>([]);
  const [activeTab, setActiveTab] = useState<WatchlistTab>('All');
  const [sortKey, setSortKey] = useState<SortKey>('default');
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc');
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadWatchlist = useCallback(
    async (mode: 'focus' | 'refresh' = 'focus') => {
      if (requestInFlightRef.current) {
        return;
      }

      if (!credentials) {
        setErrorMessage('Sign in again to load your watchlist.');
        setIsLoading(false);
        setIsRefreshing(false);
        hasLoadedRef.current = true;
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
        const response = await watchlistApi.getWatchlist(credentials);
        setWatchlistStocks(response.watchlistedStocks ?? []);
      } catch (error) {
        setErrorMessage(getStockApiErrorMessage(error, 'Watchlist could not be loaded.'));
      } finally {
        hasLoadedRef.current = true;
        requestInFlightRef.current = false;
        setIsLoading(false);
        setIsRefreshing(false);
      }
    },
    [credentials],
  );

  useMinuteBoundaryRefresh({
    onRefresh: () => loadWatchlist('focus'),
  });

  useFocusEffect(
    useCallback(() => {
      scrollRef.current?.scrollTo({ animated: false, y: 0 });
      return undefined;
    }, []),
  );

  const handleRefresh = () => {
    guardedRefresh(() => void loadWatchlist('refresh'));
  };

  const visibleWatchlistRows = useMemo(() => {
    if (sortKey === 'default') {
      return watchlistStocks;
    }

    return [...watchlistStocks].sort((first, second) => {
      const direction = sortDirection === 'asc' ? 1 : -1;
      if (sortKey === 'symbol') {
        return first.symbol.localeCompare(second.symbol) * direction;
      }

      const firstValue =
        sortKey === 'price'
          ? toNumber(getPreferredPrice(first))
          : toNumber(getPreferredPercentChange(first));
      const secondValue =
        sortKey === 'price'
          ? toNumber(getPreferredPrice(second))
          : toNumber(getPreferredPercentChange(second));

      if (firstValue === null && secondValue === null) {
        return 0;
      }
      if (firstValue === null) {
        return 1;
      }
      if (secondValue === null) {
        return -1;
      }
      return (firstValue - secondValue) * direction;
    });
  }, [sortDirection, sortKey, watchlistStocks]);

  const handleSort = (nextKey: Exclude<SortKey, 'default'>) => {
    if (nextKey === sortKey) {
      if (sortDirection === 'asc') {
        setSortDirection('desc');
      } else {
        setSortKey('default');
        setSortDirection('asc');
      }
      return;
    }

    setSortKey(nextKey);
    setSortDirection('asc');
  };

  const openSearch = () => {
    router.push({
      pathname: '/stocks/search-context',
      params: { from: 'watchlist' },
    } as Href);
  };

  const showUnsupported = activeTab === 'HK' || activeTab === 'MY';

  return (
    <View style={styles.container}>
      <View style={[styles.fixedArea, { paddingTop: insets.top + 2 }]}>
        <View style={styles.topActions}>
          <Image
            accessibilityLabel="StockMentor"
            contentFit="contain"
            source={require('../assets/images/stockmentor-icon-transparent-1024.png')}
            style={styles.logo}
          />
          <Text selectable style={styles.pageTitle}>
            Watchlists
          </Text>
          <Pressable
            accessibilityHint="Searches supported stocks."
            accessibilityLabel="Search stocks"
            accessibilityRole="button"
            onPress={openSearch}
            style={({ pressed }) => [styles.iconButton, pressed ? styles.pressed : undefined]}>
            <IconSymbol color={Colors.light.text} name="magnifyingglass" size={22} />
          </Pressable>
          <Pressable
            accessibilityHint="Refreshes your watchlist."
            accessibilityLabel="Refresh watchlist"
            accessibilityRole="button"
            onPress={handleRefresh}
            style={({ pressed }) => [styles.iconButton, pressed ? styles.pressed : undefined]}>
            <IconSymbol color={Colors.light.text} name="arrow.clockwise" size={22} />
          </Pressable>
        </View>

        <View style={styles.marketTabs}>
          {(['All', 'US', 'HK', 'MY'] as WatchlistTab[]).map((tab) => (
            <Pressable
              accessibilityHint={
                tab === 'All' || tab === 'US'
                  ? 'Shows saved US stocks.'
                  : `${tab} market support is planned for a later release.`
              }
              accessibilityLabel={`${tab} watchlist tab`}
              accessibilityRole="button"
              accessibilityState={{ selected: activeTab === tab }}
              key={tab}
              onPress={() => setActiveTab(tab)}
              style={styles.marketTab}>
              <Text style={[styles.marketTabText, activeTab === tab ? styles.marketTabTextActive : undefined]}>
                {tab}
              </Text>
            </Pressable>
          ))}
        </View>

        <StockMarketNotice stocks={watchlistStocks} />

        {!showUnsupported ? (
          <View style={styles.tableHeader}>
            <Text style={[styles.tableHeaderText, styles.tableHeaderNo]}>No.</Text>
            <HeaderCell
              label="Symbol"
              onPress={() => handleSort('symbol')}
              selected={sortKey === 'symbol'}
              sortDirection={sortDirection}
              style={styles.tableHeaderIdentity}
            />
            <HeaderCell
              align="right"
              label="Price"
              onPress={() => handleSort('price')}
              selected={sortKey === 'price'}
              sortDirection={sortDirection}
              style={styles.tableHeaderPrice}
            />
            <HeaderCell
              align="right"
              label="Chg %"
              onPress={() => handleSort('change')}
              selected={sortKey === 'change'}
              sortDirection={sortDirection}
              style={styles.tableHeaderChange}
            />
          </View>
        ) : null}
      </View>

      <ScrollView
        ref={scrollRef}
        alwaysBounceVertical
        bounces
        contentContainerStyle={[
          styles.content,
          {
            paddingBottom: Math.max(Spacing.xxl, insets.bottom + Spacing.xl),
          },
        ]}
        contentInsetAdjustmentBehavior="never"
        overScrollMode="never"
        refreshControl={<RefreshControl onRefresh={handleRefresh} refreshing={isRefreshing} />}
        style={styles.scroller}>
        {errorMessage ? <ErrorBanner title="Watchlist needs attention" message={errorMessage} /> : null}
        {isLoading ? (
          <SkeletonRows count={4} />
        ) : showUnsupported ? (
          <UnsupportedWatchlistState market={activeTab} />
        ) : visibleWatchlistRows.length === 0 ? (
          <EmptyState
            title="No watchlist stocks yet"
            description="Search stocks and tap the heart to add one."
          />
        ) : (
          <View style={styles.rows}>
            {visibleWatchlistRows.map((stock, index) => (
              <WatchlistTableRow
                detailReturnContext={{ returnTo: 'watchlist' }}
                key={stock.symbol}
                rowNumber={index + 1}
                stock={stock}
              />
            ))}
          </View>
        )}
      </ScrollView>
    </View>
  );
}

function UnsupportedWatchlistState({ market }: { market: 'HK' | 'MY' }) {
  return (
    <View style={styles.unsupported}>
      <View style={styles.unsupportedIcon}>
        <IconSymbol color={Colors.light.caution} name="hammer.fill" size={28} />
      </View>
      <Text selectable style={styles.unsupportedTitle}>
        {market} watchlists are in progress
      </Text>
      <Text selectable style={styles.unsupportedBody}>
        This section will be completed in a later release.
      </Text>
    </View>
  );
}

function HeaderCell({
  align = 'left',
  label,
  onPress,
  selected,
  sortDirection,
  style,
}: {
  align?: 'left' | 'right';
  label: string;
  onPress: () => void;
  selected: boolean;
  sortDirection: SortDirection;
  style: object;
}) {
  return (
    <Pressable
      accessibilityLabel={`Sort by ${label.toLowerCase()}`}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={[styles.headerCell, style]}>
      <View style={[styles.headerCellContent, align === 'right' ? styles.headerCellContentRight : undefined]}>
        <Text
          style={[
            styles.tableHeaderText,
            selected ? styles.tableHeaderTextSelected : undefined,
            align === 'right' ? styles.tableHeaderTextRight : undefined,
          ]}>
          {label}
        </Text>
        <SortIndicator direction={sortDirection} selected={selected} />
      </View>
    </Pressable>
  );
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
  fixedArea: {
    backgroundColor: Colors.light.background,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
  },
  scroller: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  topActions: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
    justifyContent: 'flex-end',
    minHeight: 48,
    paddingHorizontal: Spacing.md,
  },
  logo: {
    height: 30,
    width: 30,
  },
  pageTitle: {
    color: Colors.light.text,
    flex: 1,
    fontSize: 22,
    fontWeight: '700',
    lineHeight: 28,
  },
  iconButton: {
    alignItems: 'center',
    height: 44,
    justifyContent: 'center',
    width: 38,
  },
  marketTabs: {
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    flexDirection: 'row',
    gap: Spacing.xl,
    minHeight: 48,
    paddingHorizontal: Spacing.md,
  },
  marketTab: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.xs,
    justifyContent: 'center',
    minHeight: 44,
  },
  marketTabText: {
    color: Colors.light.mutedText,
    fontSize: 18,
    fontWeight: '500',
  },
  marketTabTextActive: {
    color: Colors.light.text,
    fontWeight: '800',
    textDecorationColor: '#F97316',
    textDecorationLine: 'underline',
  },
  tableHeader: {
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderBottomWidth: 1,
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
  tableHeaderTextSelected: {
    color: Colors.light.text,
  },
  tableHeaderTextRight: {
    textAlign: 'right',
  },
  tableHeaderNo: {
    width: 28,
  },
  headerCell: {
    justifyContent: 'center',
    minHeight: 38,
  },
  headerCellContent: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 2,
  },
  headerCellContentRight: {
    justifyContent: 'flex-end',
  },
  tableHeaderIdentity: {
    flex: 1,
  },
  tableHeaderPrice: {
    width: 92,
  },
  tableHeaderChange: {
    width: 72,
  },
  rows: {
    gap: 0,
  },
  pressed: {
    opacity: 0.82,
  },
  unsupported: {
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    gap: Spacing.sm,
    paddingHorizontal: Spacing.xl,
    paddingVertical: 56,
  },
  unsupportedIcon: {
    alignItems: 'center',
    backgroundColor: '#FFF7ED',
    borderColor: '#FED7AA',
    borderRadius: 24,
    borderWidth: 1,
    height: 48,
    justifyContent: 'center',
    width: 48,
  },
  unsupportedTitle: {
    color: Colors.light.text,
    fontSize: 16,
    fontWeight: '700',
  },
  unsupportedBody: {
    color: Colors.light.mutedText,
    fontSize: 13,
    lineHeight: 18,
    textAlign: 'center',
  },
});
