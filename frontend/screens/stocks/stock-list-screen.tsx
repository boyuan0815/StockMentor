import { useFocusEffect } from '@react-navigation/native';
import { Image } from 'expo-image';
import { type Href, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Animated, FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Defs, LinearGradient, RadialGradient, Rect, Stop } from 'react-native-svg';

import { paperTradingApi } from '@/api/paper-trading';
import { stocksApi } from '@/api/stocks';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import { ResetCardSheet } from '@/components/paper-trading/reset-card-sheet';
import { SortIndicator } from '@/components/stocks/sort-indicator';
import { StockMarketNotice } from '@/components/stocks/stock-market-notice';
import { StockListTableRow, type StockTableItem } from '@/components/stocks/stock-table-row';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import { useMinuteBoundaryRefresh } from '@/hooks/use-minute-boundary-refresh';
import { useRefreshCooldown } from '@/hooks/use-refresh-cooldown';
import { useAuthSession } from '@/providers/auth-session-provider';
import { useToast } from '@/providers/toast-provider';
import type { PaperPortfolioResponse } from '@/types/paper-trading';
import type { ApiNumber, StockListItemResponse } from '@/types/stocks';
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
  const { showToast } = useToast();
  const guardedRefresh = useRefreshCooldown();
  const requestInFlightRef = useRef(false);
  const portfolioRequestInFlightRef = useRef(false);
  const hasLoadedRef = useRef(false);
  const listRef = useRef<FlatList<StockListItemResponse> | null>(null);
  const [stocks, setStocks] = useState<StockListItemResponse[]>([]);
  const [portfolio, setPortfolio] = useState<PaperPortfolioResponse | null>(null);
  const [sortKey, setSortKey] = useState<SortKey>('default');
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc');
  const [activeMarket, setActiveMarket] = useState<MarketTab>('US');
  const [isLoading, setIsLoading] = useState(true);
  const [isPortfolioLoading, setIsPortfolioLoading] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [resetConfirmVisible, setResetConfirmVisible] = useState(false);
  const [isResettingPortfolio, setIsResettingPortfolio] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [portfolioError, setPortfolioError] = useState<string | null>(null);

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

  const loadPortfolioCard = useCallback(async () => {
    if (portfolioRequestInFlightRef.current || !credentials) {
      return;
    }

    portfolioRequestInFlightRef.current = true;
    setIsPortfolioLoading(true);
    try {
      const response = await paperTradingApi.getPortfolio(credentials);
      setPortfolio(response);
      setPortfolioError(null);
    } catch (error) {
      setPortfolioError(getStockApiErrorMessage(error, 'Portfolio summary could not be loaded.'));
    } finally {
      portfolioRequestInFlightRef.current = false;
      setIsPortfolioLoading(false);
    }
  }, [credentials]);

  useMinuteBoundaryRefresh({
    onRefresh: async () => {
      await Promise.allSettled([loadRows('initial'), loadPortfolioCard()]);
    },
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
    guardedRefresh(() => {
      void loadRows('refresh');
      void loadPortfolioCard();
    });
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

  const openSuggestionsPlaceholder = () => {
    showToast('AI Stock Suggestions will be added in the next phase.');
  };

  const resetStocksPortfolioCard = async () => {
    if (!credentials || isResettingPortfolio) {
      return;
    }

    setIsResettingPortfolio(true);
    try {
      await paperTradingApi.resetPortfolio(credentials);
      setResetConfirmVisible(false);
      showToast('Simulated portfolio reset.', 'success');
      await loadPortfolioCard();
    } catch (error) {
      showToast(getStockApiErrorMessage(error, 'Portfolio could not be reset.'), 'error');
    } finally {
      setIsResettingPortfolio(false);
    }
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

      </View>

      <FlatList
        ref={listRef}
        ListHeaderComponent={
          !showUnsupported ? (
            <>
              {errorMessage && !isLoading && visibleRows.length > 0 ? (
                <ErrorBanner title="Stocks need attention" message={errorMessage} />
              ) : null}
              <PortfolioSummaryCard
                loading={isPortfolioLoading && !portfolio}
                onOpenPortfolio={() => router.push('/paper-trading' as Href)}
                onReset={() => setResetConfirmVisible(true)}
                onViewSuggestions={openSuggestionsPlaceholder}
                onViewWatchlist={() => router.push('/watchlist' as Href)}
                portfolio={portfolio}
              />
              <StockTableHeader
                handleSort={handleSort}
                sortDirection={sortDirection}
                sortKey={sortKey}
              />
            </>
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
      <ResetCardSheet
        nextSessionNumber={(portfolio?.currentSessionNumber ?? 1) + 1}
        onClose={() => setResetConfirmVisible(false)}
        onConfirm={() => void resetStocksPortfolioCard()}
        pending={isResettingPortfolio}
        startingCash={portfolio?.startingCash ?? portfolio?.totalPortfolioValue ?? null}
        visible={resetConfirmVisible}
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

function StockTableHeader({
  handleSort,
  sortDirection,
  sortKey,
}: {
  handleSort: (nextKey: SortKey) => void;
  sortDirection: SortDirection;
  sortKey: SortKey;
}) {
  return (
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
  );
}

function PortfolioSummaryCard({
  loading,
  onOpenPortfolio,
  onReset,
  onViewSuggestions,
  onViewWatchlist,
  portfolio,
}: {
  loading: boolean;
  onOpenPortfolio: () => void;
  onReset: () => void;
  onViewSuggestions: () => void;
  onViewWatchlist: () => void;
  portfolio: PaperPortfolioResponse | null;
}) {
  const todayValue = portfolio?.todayProfitLoss ?? null;
  const todayNumber = toNumber(todayValue);
  const tier = getPortfolioCardTier(portfolio?.totalPortfolioValue ?? null);
  const todayColor =
    todayNumber === null || todayNumber === 0
      ? tier.todayNeutral
      : todayNumber > 0
        ? tier.todayPositive
        : tier.todayNegative;

  return (
    <View
      style={[
        styles.portfolioCard,
        { backgroundColor: tier.background, borderColor: tier.border, shadowColor: tier.shadowColor },
      ]}>
      <PortfolioCardBackground tier={tier} />
      <PremiumCardShine color={tier.shine} />
      <View style={styles.portfolioTopRow}>
        <TierBadge tier={tier} />
        <Pressable
          accessibilityLabel="Open Portfolio"
          accessibilityRole="button"
          onPress={onOpenPortfolio}
          style={({ pressed }) => [styles.portfolioLink, pressed ? styles.pressed : undefined]}>
          <Text style={[styles.portfolioLinkText, { color: tier.linkText }]}>Portfolio &gt;</Text>
        </Pressable>
      </View>
      <View style={styles.portfolioLabelRow}>
        <Text selectable style={[styles.portfolioCardLabel, { color: tier.mutedText }]}>
          Net Assets {'\u00b7'} USD
        </Text>
        <Pressable
          accessibilityLabel="Reset simulated portfolio card"
          accessibilityRole="button"
          onPress={onReset}
          style={({ pressed }) => [
            styles.resetChip,
            { backgroundColor: tier.resetBackground, borderColor: tier.resetBorder },
            pressed ? { backgroundColor: tier.resetPressedBackground } : undefined,
          ]}>
          <IconSymbol color={tier.resetText} name="arrow.clockwise" size={12} />
          <Text style={[styles.resetChipText, { color: tier.resetText }]}>Reset</Text>
        </Pressable>
      </View>
      <View style={styles.portfolioCardHeader}>
        <Text selectable style={[styles.portfolioCardValue, { color: tier.valueText }]}>
          {loading ? 'Loading...' : portfolio ? formatCardAmount(portfolio.totalPortfolioValue) : 'Unavailable'}
        </Text>
        <View style={styles.portfolioToday}>
          <Text selectable style={[styles.portfolioTodayLabel, { color: tier.mutedText }]}>
            Today's P/L
          </Text>
          <Text selectable style={[styles.portfolioTodayValue, { color: todayColor }]}>
            {portfolio ? formatCardAmount(todayValue, true) : '--'}
          </Text>
        </View>
      </View>
      <View
        style={[
          styles.portfolioActions,
          { backgroundColor: tier.buttonTrackBackground, borderColor: tier.buttonTrackBorder },
        ]}>
        <Pressable
          accessibilityLabel="View AI Stock Suggestions"
          accessibilityRole="button"
          onPress={onViewSuggestions}
          style={({ pressed }) => [
            styles.suggestionsButton,
            { backgroundColor: tier.buttonPrimaryBackground, shadowColor: tier.buttonShadowColor },
            pressed ? { backgroundColor: tier.buttonPrimaryPressedBackground } : undefined,
          ]}>
          <Text style={[styles.suggestionsButtonText, { color: tier.buttonPrimaryText }]}>View AI Picks</Text>
        </Pressable>
        <Pressable
          accessibilityLabel="View Stocklist"
          accessibilityRole="button"
          onPress={onViewWatchlist}
          style={({ pressed }) => [
            styles.watchlistButton,
            { borderColor: tier.buttonSecondaryBorder },
            pressed ? { backgroundColor: tier.buttonSecondaryPressedBackground } : undefined,
          ]}>
          <Text style={[styles.watchlistButtonText, { color: tier.buttonSecondaryText }]}>View Stocklist</Text>
        </Pressable>
      </View>
    </View>
  );
}

function TierBadge({ tier }: { tier: ReturnType<typeof getPortfolioCardTier> }) {
  return (
    <View
      style={[
        styles.tierBadge,
        { backgroundColor: tier.badgeBackground, borderColor: tier.badgeBorder },
      ]}>
      <View style={[styles.tierDot, { backgroundColor: tier.badgeDot }]} />
      <Text style={[styles.tierBadgeText, { color: tier.badgeText }]}>{tier.name}</Text>
    </View>
  );
}

function PortfolioCardBackground({ tier }: { tier: ReturnType<typeof getPortfolioCardTier> }) {
  const baseId = `net-assets-${tier.name.toLowerCase()}`;
  return (
    <Svg
      pointerEvents="none"
      preserveAspectRatio="none"
      style={StyleSheet.absoluteFill}
      viewBox="0 0 360 227">
      <Defs>
        <LinearGradient id={`${baseId}-base`} x1="0%" x2="100%" y1="0%" y2="100%">
          <Stop offset="0%" stopColor={tier.gradientStart} />
          <Stop offset="52%" stopColor={tier.gradientMiddle} />
          <Stop offset="100%" stopColor={tier.gradientEnd} />
        </LinearGradient>
        <RadialGradient id={`${baseId}-radial`} cx="12%" cy="-10%" r="120%">
          <Stop offset="0%" stopColor={tier.radialColor} stopOpacity={tier.radialOpacity} />
          <Stop offset="58%" stopColor={tier.radialColor} stopOpacity="0" />
        </RadialGradient>
        <LinearGradient id={`${baseId}-sheen`} x1="0%" x2="100%" y1="0%" y2="100%">
          <Stop offset="0%" stopColor={tier.sheenStart} stopOpacity={tier.sheenStartOpacity} />
          <Stop offset="42%" stopColor={tier.sheenStart} stopOpacity="0" />
          <Stop offset="68%" stopColor={tier.sheenEnd} stopOpacity={tier.sheenEndOpacity} />
          <Stop offset="100%" stopColor={tier.sheenEnd} stopOpacity="0" />
        </LinearGradient>
      </Defs>
      <Rect fill={`url(#${baseId}-base)`} height="227" width="360" x="0" y="0" />
      <Rect fill={`url(#${baseId}-radial)`} height="227" width="360" x="0" y="0" />
      <Rect fill={`url(#${baseId}-sheen)`} height="227" width="360" x="0" y="0" />
    </Svg>
  );
}

function PremiumCardShine({ color }: { color: string }) {
  const translateX = useRef(new Animated.Value(-180)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.timing(translateX, {
        duration: 3200,
        toValue: 360,
        useNativeDriver: true,
      }),
    );
    loop.start();
    return () => loop.stop();
  }, [translateX]);

  return (
    <Animated.View
      pointerEvents="none"
      style={[
        styles.cardShine,
        {
          backgroundColor: color,
          transform: [{ translateX }, { rotate: '18deg' }],
        },
      ]}
    />
  );
}

function getPortfolioCardTier(value: ApiNumber) {
  const numericValue = toNumber(value);
  if (numericValue !== null && numericValue >= 1_600_000) {
    return {
      background: '#DFE5F1',
      badgeBackground: 'rgba(255,255,255,0.7)',
      badgeBorder: 'rgba(180,190,212,0.6)',
      badgeDot: '#C9D0E4',
      badgeText: '#4A5270',
      border: '#B9C2D8',
      buttonPrimaryBackground: '#FFFFFF',
      buttonPrimaryPressedBackground: '#EEF2FF',
      buttonPrimaryText: '#232A42',
      buttonShadowColor: '#7882AA',
      buttonSecondaryBorder: '#9CA7C1',
      buttonSecondaryPressedBackground: 'rgba(255,255,255,0.42)',
      buttonSecondaryText: '#4A5474',
      buttonTrackBackground: 'rgba(255,255,255,0.6)',
      buttonTrackBorder: 'rgba(190,198,218,0.7)',
      gradientEnd: '#D6DCEC',
      gradientMiddle: '#E9E4F2',
      gradientStart: '#FBFCFF',
      linkText: '#2A3147',
      mutedText: '#5B647E',
      name: 'PLATINUM',
      radialColor: '#FFFFFF',
      radialOpacity: '0.72',
      resetBackground: 'rgba(255,255,255,0.72)',
      resetBorder: 'rgba(180,190,212,0.72)',
      resetPressedBackground: '#FFFFFF',
      resetText: '#45506B',
      shadowColor: '#6E78A0',
      sheenEnd: '#D6CDE6',
      sheenEndOpacity: '0.28',
      sheenStart: '#FFFFFF',
      sheenStartOpacity: '0.7',
      shine: 'rgba(255,255,255,0.5)',
      todayNegative: '#D23A32',
      todayNeutral: '#5B647E',
      todayPositive: '#15803D',
      unavailableText: '#4A5270',
      valueText: '#232A42',
    };
  }
  if (numericValue !== null && numericValue >= 1_400_000) {
    return {
      background: '#F6E3AD',
      badgeBackground: 'rgba(255,255,255,0.5)',
      badgeBorder: 'rgba(200,165,80,0.55)',
      badgeDot: '#D9A93A',
      badgeText: '#836321',
      border: '#E0C074',
      buttonPrimaryBackground: '#FFFDF3',
      buttonPrimaryPressedBackground: '#FFF7D6',
      buttonPrimaryText: '#5A4413',
      buttonShadowColor: '#967828',
      buttonSecondaryBorder: '#CDA75A',
      buttonSecondaryPressedBackground: 'rgba(255,255,255,0.32)',
      buttonSecondaryText: '#7A5D22',
      buttonTrackBackground: 'rgba(255,255,255,0.45)',
      buttonTrackBorder: 'rgba(210,180,100,0.6)',
      gradientEnd: '#ECCE86',
      gradientMiddle: '#F6E3AD',
      gradientStart: '#FFF7DF',
      linkText: '#5A4413',
      mutedText: '#8A6A26',
      name: 'GOLD',
      radialColor: '#FFFFFF',
      radialOpacity: '0.62',
      resetBackground: 'rgba(255,255,255,0.55)',
      resetBorder: 'rgba(200,165,80,0.6)',
      resetPressedBackground: '#FFFDF3',
      resetText: '#7A5D22',
      shadowColor: '#967828',
      sheenEnd: '#F8D883',
      sheenEndOpacity: '0.18',
      sheenStart: '#FFFFFF',
      sheenStartOpacity: '0.58',
      shine: 'rgba(255,255,255,0.48)',
      todayNegative: '#BF3A2B',
      todayNeutral: '#8A6A26',
      todayPositive: '#15803D',
      unavailableText: '#7A5D22',
      valueText: '#43340F',
    };
  }
  if (numericValue !== null && numericValue >= 1_200_000) {
    return {
      background: '#E3E8F0',
      badgeBackground: 'rgba(255,255,255,0.65)',
      badgeBorder: 'rgba(160,170,185,0.45)',
      badgeDot: '#A9B2C2',
      badgeText: '#5A6478',
      border: '#C4CCD8',
      buttonPrimaryBackground: '#FFFFFF',
      buttonPrimaryPressedBackground: '#F1F5F9',
      buttonPrimaryText: '#2A3344',
      buttonShadowColor: '#5A6478',
      buttonSecondaryBorder: '#B4BCC8',
      buttonSecondaryPressedBackground: 'rgba(255,255,255,0.4)',
      buttonSecondaryText: '#51607A',
      buttonTrackBackground: 'rgba(255,255,255,0.55)',
      buttonTrackBorder: 'rgba(180,188,200,0.6)',
      gradientEnd: '#CDD4DF',
      gradientMiddle: '#E3E8F0',
      gradientStart: '#FBFCFE',
      linkText: '#283143',
      mutedText: '#5D6B80',
      name: 'SILVER',
      radialColor: '#FFFFFF',
      radialOpacity: '0.52',
      resetBackground: 'rgba(255,255,255,0.7)',
      resetBorder: 'rgba(160,170,185,0.5)',
      resetPressedBackground: '#FFFFFF',
      resetText: '#4A5568',
      shadowColor: '#505A6E',
      sheenEnd: '#FFFFFF',
      sheenEndOpacity: '0.06',
      sheenStart: '#FFFFFF',
      sheenStartOpacity: '0.55',
      shine: 'rgba(255,255,255,0.36)',
      todayNegative: '#D6342C',
      todayNeutral: '#6B7589',
      todayPositive: '#166534',
      unavailableText: '#334155',
      valueText: '#2A3344',
    };
  }
  return {
    background: '#15171D',
    badgeBackground: 'rgba(255,255,255,0.08)',
    badgeBorder: 'rgba(255,255,255,0.07)',
    badgeDot: '#8992A2',
    badgeText: '#CDD3DF',
    border: 'rgba(255,255,255,0.07)',
    buttonPrimaryBackground: '#FFFFFF',
    buttonPrimaryPressedBackground: '#E5E7EB',
    buttonPrimaryText: '#14161C',
    buttonShadowColor: '#000000',
    buttonSecondaryBorder: 'rgba(255,255,255,0.08)',
    buttonSecondaryPressedBackground: 'rgba(255,255,255,0.12)',
    buttonSecondaryText: '#C4CAD6',
    buttonTrackBackground: 'rgba(255,255,255,0.06)',
    buttonTrackBorder: 'rgba(255,255,255,0.08)',
    gradientEnd: '#0C0E12',
    gradientMiddle: '#15171D',
    gradientStart: '#23262E',
    linkText: '#FFFFFF',
    mutedText: '#8B93A3',
    name: 'STANDARD',
    radialColor: '#FFFFFF',
    radialOpacity: '0.10',
    resetBackground: 'rgba(255,255,255,0.10)',
    resetBorder: 'rgba(255,255,255,0.13)',
    resetPressedBackground: 'rgba(255,255,255,0.18)',
    resetText: '#D4D9E3',
    shadowColor: '#000000',
    sheenEnd: '#FFFFFF',
    sheenEndOpacity: '0.03',
    sheenStart: '#FFFFFF',
    sheenStartOpacity: '0.08',
    shine: 'rgba(255,255,255,0.12)',
    todayNegative: '#FF5B60',
    todayNeutral: '#8B93A3',
    todayPositive: '#86EFAC',
    unavailableText: '#E2E8F0',
    valueText: '#FFFFFF',
  };
}

function formatCardAmount(value: ApiNumber, signed = false) {
  const parsed = toNumber(value);
  if (parsed === null) {
    return '--';
  }

  const sign = signed && parsed > 0 ? '+' : signed && parsed < 0 ? '-' : '';
  return `${sign}${Math.abs(parsed).toLocaleString('en-US', {
    maximumFractionDigits: 2,
    minimumFractionDigits: 2,
  })}`;
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
    borderTopWidth: 0,
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
  portfolioCard: {
    alignSelf: 'center',
    aspectRatio: 1.586,
    borderRadius: 22,
    borderWidth: 1,
    gap: 0,
    marginBottom: Spacing.xl,
    marginTop: Spacing.lg,
    overflow: 'hidden',
    padding: 20,
    shadowColor: '#000000',
    shadowOffset: { height: 8, width: 0 },
    shadowOpacity: 0.16,
    shadowRadius: 18,
    width: '90%',
    elevation: 5,
  },
  cardShine: {
    bottom: -44,
    opacity: 0.82,
    position: 'absolute',
    top: -44,
    width: 92,
  },
  tierBadge: {
    alignItems: 'center',
    borderRadius: 999,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 7,
    minHeight: 22,
    paddingLeft: 9,
    paddingRight: 11,
  },
  tierDot: {
    borderRadius: 4,
    height: 8,
    width: 8,
  },
  tierBadgeText: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.1,
  },
  portfolioTopRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    zIndex: 1,
  },
  portfolioLabelRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
    marginTop: Spacing.xs,
    zIndex: 1,
  },
  portfolioLink: {
    minHeight: 30,
    justifyContent: 'center',
  },
  portfolioLinkText: {
    fontSize: 13,
    fontWeight: '700',
  },
  resetChip: {
    alignItems: 'center',
    backgroundColor: 'rgba(255,255,255,0.16)',
    borderColor: 'rgba(255,255,255,0.24)',
    borderRadius: 999,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 4,
    paddingHorizontal: Spacing.sm,
    paddingVertical: 4,
  },
  resetChipText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '700',
  },
  portfolioCardHeader: {
    marginLeft: -1,
    marginTop: 0,
    zIndex: 1,
  },
  portfolioCardLabel: {
    color: '#CBD5E1',
    fontSize: 16,
    fontWeight: '600',
    lineHeight: 21,
  },
  portfolioCardValue: {
    color: '#FFFFFF',
    fontSize: 34,
    fontVariant: ['tabular-nums'],
    fontWeight: '800',
    lineHeight: 39,
  },
  portfolioToday: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
    marginVertical: Spacing.md,
  },
  portfolioTodayLabel: {
    color: '#CBD5E1',
    fontSize: 15,
    fontWeight: '500',
    lineHeight: 19,
  },
  portfolioTodayValue: {
    fontSize: 15,
    fontVariant: ['tabular-nums'],
    fontWeight: '400',
    lineHeight: 19,
  },
  portfolioActions: {
    borderRadius: 999,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 6,
    marginTop: 'auto',
    padding: 5,
    zIndex: 1,
  },
  suggestionsButton: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 999,
    flex: 1,
    justifyContent: 'center',
    minHeight: 43,
    paddingHorizontal: Spacing.md,
    shadowColor: '#000000',
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.14,
    shadowRadius: 8,
  },
  suggestionsButtonText: {
    color: '#052344',
    fontSize: 13,
    fontWeight: '700',
  },
  watchlistButton: {
    alignItems: 'center',
    borderColor: 'rgba(255,255,255,0.36)',
    borderRadius: 999,
    borderWidth: 0,
    flex: 1,
    justifyContent: 'center',
    minHeight: 43,
    paddingHorizontal: Spacing.md,
  },
  watchlistButtonText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '500',
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
