import { useFocusEffect } from '@react-navigation/native';
import { Image } from 'expo-image';
import { type Href, useRouter } from 'expo-router';
import { useCallback, useMemo, useRef, useState } from 'react';
import { FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { stocksApi } from '@/api/stocks';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import { SortIndicator } from '@/components/stocks/sort-indicator';
import { StockMarketNotice } from '@/components/stocks/stock-market-notice';
import { StockListTableRow, type StockTableItem } from '@/components/stocks/stock-table-row';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import { useMinuteBoundaryRefresh } from '@/hooks/use-minute-boundary-refresh';
import { useRefreshCooldown } from '@/hooks/use-refresh-cooldown';
import { useAuthSession } from '@/providers/auth-session-provider';
import type { StockListItemResponse } from '@/types/stocks';
import {
  getPreferredPercentChange,
  getPreferredPrice,
  getStockApiErrorMessage,
  toNumber,
} from '@/utils/stock-display';

type SortKey = 'default' | 'symbol' | 'price' | 'change';
type SortDirection = 'asc' | 'desc';
type MarketTab = 'US' | 'MY' | 'HK';

export function StockListScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { credentials } = useAuthSession();
  const guardedRefresh = useRefreshCooldown();
  const requestInFlightRef = useRef(false);
  const hasLoadedRef = useRef(false);
  const listRef = useRef<FlatList<StockListItemResponse> | null>(null);
  const [stocks, setStocks] = useState<StockListItemResponse[]>([]);
  const [sortKey, setSortKey] = useState<SortKey>('default');
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc');
  const [activeMarket, setActiveMarket] = useState<MarketTab>('US');
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadRows = useCallback(
    async (mode: 'initial' | 'refresh' = 'initial') => {
      if (requestInFlightRef.current) {
        return;
      }

      if (!credentials) {
        setErrorMessage('Sign in again to load stocks.');
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
        const stockResponse = await stocksApi.getStocks(credentials);
        setStocks(stockResponse.stocks ?? []);
      } catch (error) {
        setErrorMessage(getStockApiErrorMessage(error, 'Stock list could not be loaded.'));
      } finally {
        requestInFlightRef.current = false;
        hasLoadedRef.current = true;
        setIsLoading(false);
        setIsRefreshing(false);
      }
    },
    [credentials],
  );

  useMinuteBoundaryRefresh({
    onRefresh: () => loadRows('initial'),
  });

  useFocusEffect(
    useCallback(() => {
      listRef.current?.scrollToOffset({ animated: false, offset: 0 });
      return undefined;
    }, []),
  );

  const visibleRows = useMemo(() => {
    if (sortKey === 'default') {
      return stocks;
    }

    return [...stocks].sort((first, second) => {
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
  }, [sortDirection, sortKey, stocks]);

  const handleRefresh = () => {
    guardedRefresh(() => void loadRows('refresh'));
  };

  const handleSort = (nextKey: SortKey) => {
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

  const handlePaperTrade = (stock: StockTableItem) => {
    router.push({
      pathname: '/paper-trading/buy',
      params: { from: 'stocks', symbol: stock.symbol },
    } as Href);
  };

  const openSearch = () => {
    router.push({
      pathname: '/stocks/search-context',
      params: { from: 'stocks' },
    } as Href);
  };

  const showUnsupported = activeMarket !== 'US';

  return (
    <View style={styles.container}>
      <View style={[styles.fixedArea, { paddingTop: insets.top + 2 }]}>
        <View style={styles.topActions}>
          <Image
            accessibilityLabel="StockMentor"
            contentFit="contain"
            source={require('../../assets/images/stockmentor-icon-transparent-1024.png')}
            style={styles.logo}
          />
          <Text selectable style={styles.pageTitle}>
            Stocks
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
            accessibilityHint="Refreshes the stock table."
            accessibilityLabel="Refresh stocks"
            accessibilityRole="button"
            onPress={handleRefresh}
            style={({ pressed }) => [styles.iconButton, pressed ? styles.pressed : undefined]}>
            <IconSymbol color={Colors.light.text} name="arrow.clockwise" size={22} />
          </Pressable>
        </View>

        <View style={styles.marketTabs}>
          {(['US', 'MY', 'HK'] as MarketTab[]).map((tab) => (
            <Pressable
              accessibilityHint={
                tab === 'US'
                  ? 'Shows supported US stocks.'
                  : `${tab} market support is planned for a later release.`
              }
              accessibilityLabel={`${tab} market tab`}
              accessibilityRole="button"
              accessibilityState={{ selected: activeMarket === tab }}
              key={tab}
              onPress={() => setActiveMarket(tab)}
              style={styles.marketTab}>
              <Text style={[styles.marketTabText, activeMarket === tab ? styles.marketTabTextActive : undefined]}>
                {tab}
              </Text>
            </Pressable>
          ))}
        </View>

        <StockMarketNotice stocks={stocks} />

        {activeMarket === 'US' ? (
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
            <Text style={[styles.tableHeaderText, styles.tableHeaderAction]}>Action</Text>
          </View>
        ) : null}
      </View>

      <FlatList
        ref={listRef}
        ListHeaderComponent={
          errorMessage && !isLoading && !showUnsupported && visibleRows.length > 0 ? (
            <ErrorBanner title="Stocks need attention" message={errorMessage} />
          ) : null
        }
        ListEmptyComponent={
          isLoading ? (
            <SkeletonRows count={5} />
          ) : errorMessage ? (
            <ErrorBanner title="Stocks need attention" message={errorMessage} />
          ) : showUnsupported ? (
            <UnsupportedMarketState market={activeMarket} />
          ) : (
            <View style={styles.emptyState}>
              <Text selectable style={styles.emptyTitle}>
                No stored stock rows
              </Text>
              <Text selectable style={styles.emptyDescription}>
                The backend returned no supported stocks yet.
              </Text>
            </View>
          )
        }
        contentContainerStyle={[
          styles.listContent,
          {
            paddingBottom: Math.max(Spacing.xxl, insets.bottom + Spacing.xl),
          },
        ]}
        contentInsetAdjustmentBehavior="never"
        data={isLoading || showUnsupported ? [] : visibleRows}
        keyExtractor={(item) => item.symbol}
        overScrollMode="never"
        refreshControl={<RefreshControl onRefresh={handleRefresh} refreshing={isRefreshing} />}
        renderItem={({ index, item }) => (
          <StockListTableRow
            detailReturnContext={{ returnTo: 'stocks' }}
            onPaperTradePress={handlePaperTrade}
            rowNumber={index + 1}
            stock={item}
          />
        )}
        style={styles.scroller}
      />
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

function UnsupportedMarketState({ market }: { market: Exclude<MarketTab, 'US'> }) {
  return (
    <View style={styles.unsupported}>
      <View style={styles.unsupportedIcon}>
        <IconSymbol color={Colors.light.caution} name="hammer.fill" size={28} />
      </View>
      <Text selectable style={styles.unsupportedTitle}>
        {market} market is in progress
      </Text>
      <Text selectable style={styles.unsupportedBody}>
        This section will be completed in a later release.
      </Text>
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
    textDecorationStyle: 'solid',
  },
  tableHeader: {
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 38,
    paddingHorizontal: Spacing.md,
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
  tableHeaderIdentity: {
    flex: 1,
  },
  tableHeaderPrice: {
    width: 80,
  },
  tableHeaderChange: {
    width: 64,
  },
  tableHeaderAction: {
    width: 94,
    textAlign: 'center',
  },
  pressed: {
    opacity: 0.82,
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
