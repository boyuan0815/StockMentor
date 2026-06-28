import { useFocusEffect } from '@react-navigation/native';
import { type Href, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { paperTradingApi } from '@/api/paper-trading';
import { stocksApi } from '@/api/stocks';
import { ActionButton } from '@/components/foundation/action-button';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import { ResetCardSheet } from '@/components/paper-trading/reset-card-sheet';
import {
  InlineNotice,
  PaperHeader,
  PaperMetric,
  PaperSection,
  PortfolioTabs,
  PositionsTable,
  TransactionRow,
  TransactionTableHeader,
} from '@/components/paper-trading/paper-trading-ui';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import { useMinuteBoundaryRefresh } from '@/hooks/use-minute-boundary-refresh';
import { useRefreshCooldown } from '@/hooks/use-refresh-cooldown';
import { useAuthSession } from '@/providers/auth-session-provider';
import { useToast } from '@/providers/toast-provider';
import type {
  PaperPortfolioResponse,
  PaperPositionResponse,
  PaperTradeTransactionResponse,
  PaperTradingAccountResponse,
} from '@/types/paper-trading';
import type { StockListItemResponse } from '@/types/stocks';
import {
  formatPaperDateTime,
  formatPaperMoney,
  formatPaperPercent,
  formatSignedPaperMoney,
  getPaperTradingApiErrorMessage,
  toPaperNumber,
} from '@/utils/paper-trading-display';
import { normalizeStockSymbol } from '@/utils/stock-display';

type PortfolioTab = 'assets' | 'history';
type SideFilter = 'ALL' | 'BUY' | 'SELL' | 'RESET';
const HISTORY_PAGE_SIZE = 20;

export function PaperTradingOverviewScreen({
  historyFocusBehavior = 'once',
  initialTab = 'assets',
}: {
  historyFocusBehavior?: 'always' | 'once';
  initialTab?: PortfolioTab;
}) {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const guardedRefresh = useRefreshCooldown();
  const { credentials } = useAuthSession();
  const { showToast } = useToast();
  const requestIdRef = useRef(0);
  const inFlightRef = useRef(false);
  const transactionRequestIdRef = useRef(0);
  const transactionInFlightRef = useRef(false);
  const hasLoadedRef = useRef(false);
  const historyTabConsumedRef = useRef(initialTab !== 'history');
  const [activeTab, setActiveTab] = useState<PortfolioTab>(initialTab);
  const [account, setAccount] = useState<PaperTradingAccountResponse | null>(null);
  const [portfolio, setPortfolio] = useState<PaperPortfolioResponse | null>(null);
  const [transactions, setTransactions] = useState<PaperTradeTransactionResponse[]>([]);
  const [stocks, setStocks] = useState<StockListItemResponse[]>([]);
  const [accountError, setAccountError] = useState<string | null>(null);
  const [portfolioError, setPortfolioError] = useState<string | null>(null);
  const [transactionError, setTransactionError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [assetsExpanded, setAssetsExpanded] = useState(false);
  const [showResetSheet, setShowResetSheet] = useState(false);
  const [isResetting, setIsResetting] = useState(false);
  const [sideFilter, setSideFilter] = useState<SideFilter>('ALL');
  const [historySearch, setHistorySearch] = useState('');
  const [currentSessionOnly, setCurrentSessionOnly] = useState(true);
  const [transactionPage, setTransactionPage] = useState(0);
  const [transactionTotalPages, setTransactionTotalPages] = useState(0);
  const [isLoadingTransactions, setIsLoadingTransactions] = useState(false);
  const [isLoadingMoreTransactions, setIsLoadingMoreTransactions] = useState(false);
  const scrollRef = useRef<ScrollView | null>(null);

  useEffect(() => {
    historyTabConsumedRef.current = initialTab !== 'history';
    setActiveTab(initialTab);
  }, [initialTab]);

  useFocusEffect(
    useCallback(() => {
      scrollRef.current?.scrollTo({ animated: false, y: 0 });
      if (
        initialTab === 'history' &&
        (historyFocusBehavior === 'always' || !historyTabConsumedRef.current)
      ) {
        setActiveTab('history');
        historyTabConsumedRef.current = true;
      } else {
        setActiveTab('assets');
      }
      return undefined;
    }, [historyFocusBehavior, initialTab]),
  );

  const stockCompanyMap = useMemo(
    () => new Map(stocks.map((stock) => [stock.symbol, stock.companyName])),
    [stocks],
  );
  const exactSearchSymbol = useMemo(() => {
    const candidate = normalizeStockSymbol(historySearch);
    return candidate && stockCompanyMap.has(candidate) ? candidate : null;
  }, [historySearch, stockCompanyMap]);

  const loadTransactionsPage = useCallback(
    async (page = 0, append = false) => {
      if (transactionInFlightRef.current) {
        return;
      }

      if (!credentials) {
        setTransactionError('Sign in again to load transactions.');
        setIsLoadingTransactions(false);
        setIsLoadingMoreTransactions(false);
        return;
      }

      const requestId = transactionRequestIdRef.current + 1;
      transactionRequestIdRef.current = requestId;
      transactionInFlightRef.current = true;
      if (append) {
        setIsLoadingMoreTransactions(true);
      } else {
        setIsLoadingTransactions(true);
      }

      try {
        const response = await paperTradingApi.getTransactionsPage(credentials, {
          currentSessionOnly,
          page,
          side: sideFilter === 'ALL' ? null : sideFilter,
          size: HISTORY_PAGE_SIZE,
          symbol: exactSearchSymbol,
        });
        if (transactionRequestIdRef.current !== requestId) {
          return;
        }
        setTransactions((current) =>
          append ? [...current, ...(response.transactions ?? [])] : response.transactions ?? [],
        );
        setTransactionPage(response.page ?? page);
        setTransactionTotalPages(response.totalPages ?? 0);
        setTransactionError(null);
      } catch (error) {
        if (transactionRequestIdRef.current !== requestId) {
          return;
        }
        setTransactionError(getPaperTradingApiErrorMessage(error, 'Transactions could not be loaded.'));
        if (!append) {
          setTransactions([]);
          setTransactionPage(0);
          setTransactionTotalPages(0);
        }
      } finally {
        if (transactionRequestIdRef.current === requestId) {
          transactionInFlightRef.current = false;
          setIsLoadingTransactions(false);
          setIsLoadingMoreTransactions(false);
        }
      }
    },
    [credentials, currentSessionOnly, exactSearchSymbol, sideFilter],
  );

  const loadPortfolioData = useCallback(
    async (mode: 'soft' | 'refresh' = 'soft') => {
      if (inFlightRef.current) {
        return;
      }

      const requestId = requestIdRef.current + 1;
      requestIdRef.current = requestId;

      if (!credentials) {
        setAccountError('Sign in again to load portfolio.');
        setPortfolioError(null);
        setTransactionError(null);
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

      const [accountResult, portfolioResult, stockResult] = await Promise.allSettled([
        paperTradingApi.getAccount(credentials),
        paperTradingApi.getPortfolio(credentials),
        stocksApi.getStocks(credentials),
      ]);

      if (requestIdRef.current !== requestId) {
        inFlightRef.current = false;
        return;
      }

      if (accountResult.status === 'fulfilled') {
        setAccount(accountResult.value);
        setAccountError(null);
      } else {
        setAccountError(getPaperTradingApiErrorMessage(accountResult.reason, 'Account could not be loaded.'));
      }

      if (portfolioResult.status === 'fulfilled') {
        setPortfolio(portfolioResult.value);
        setPortfolioError(null);
      } else {
        setPortfolioError(getPaperTradingApiErrorMessage(portfolioResult.reason, 'Portfolio could not be loaded.'));
      }

      if (stockResult.status === 'fulfilled') {
        setStocks(stockResult.value.stocks ?? []);
      }

      inFlightRef.current = false;
      hasLoadedRef.current = true;
      setIsLoading(false);
      setIsRefreshing(false);
    },
    [credentials],
  );

  useMinuteBoundaryRefresh({
    enabled: !showResetSheet && !isResetting,
    onRefresh: async () => {
      await Promise.allSettled([loadPortfolioData('soft'), loadTransactionsPage(0, false)]);
    },
  });

  useEffect(() => {
    void loadPortfolioData('soft');
  }, [loadPortfolioData]);

  useEffect(() => {
    setTransactionPage(0);
    setTransactions([]);
    void loadTransactionsPage(0, false);
  }, [loadTransactionsPage]);

  const handleRefresh = () => {
    guardedRefresh(() => {
      void loadPortfolioData('refresh');
      void loadTransactionsPage(0, false);
    });
  };

  const positions = portfolio?.positions ?? [];
  const valuationWarnings = useMemo(() => {
    const warnings: string[] = [];
    if (portfolio?.portfolioValuationComplete === false) {
      warnings.push('Some positions could not be valued with stored delayed prices.');
    }
    if ((portfolio?.unpricedPositionCount ?? 0) > 0) {
      warnings.push(`${portfolio?.unpricedPositionCount} position(s) are unpriced.`);
    }
    if (portfolio?.portfolioDataNote) {
      warnings.push(portfolio.portfolioDataNote);
    }
    return warnings;
  }, [portfolio]);

  const filteredTransactions = useMemo(() => {
    const query = historySearch.trim().toLowerCase();
    return transactions.filter((transaction) => {
      if (sideFilter !== 'ALL' && transaction.side !== sideFilter) {
        return false;
      }
      if (!query) {
        return true;
      }
      const symbol = normalizeStockSymbol(transaction.symbol);
      const companyName = symbol ? stockCompanyMap.get(symbol) ?? '' : '';
      return symbol.toLowerCase().includes(query) || companyName.toLowerCase().includes(query);
    });
  }, [historySearch, sideFilter, stockCompanyMap, transactions]);

  const openSell = (position: PaperPositionResponse) => {
    router.push({
      pathname: '/paper-trading/sell',
      params: { from: 'portfolio', symbol: position.symbol },
    } as Href);
  };

  const openTransaction = (transaction: PaperTradeTransactionResponse) => {
    if (!transaction.transactionId) {
      return;
    }
    router.push({
      pathname: '/paper-trading/transactions/[transactionId]',
      params: { transactionId: String(transaction.transactionId) },
    } as Href);
  };

  const resetPortfolio = async () => {
    if (!credentials || isResetting) {
      return;
    }

    setIsResetting(true);
    try {
      await paperTradingApi.resetPortfolio(credentials);
      setShowResetSheet(false);
      showToast('Simulated portfolio reset.');
      await loadPortfolioData('refresh');
      await loadTransactionsPage(0, false);
    } catch (error) {
      showToast(getPaperTradingApiErrorMessage(error, 'Simulated portfolio could not be reset.'), 'error');
    } finally {
      setIsResetting(false);
    }
  };

  return (
    <View style={styles.container}>
      <View style={[styles.fixedHeader, { paddingTop: insets.top + 2 }]}>
        <PaperHeader brandIcon onRefresh={handleRefresh} title="Portfolio" />
        <PortfolioTabs activeTab={activeTab} onSelect={setActiveTab} />
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
        keyboardShouldPersistTaps="handled"
        overScrollMode="never"
        refreshControl={<RefreshControl onRefresh={handleRefresh} refreshing={isRefreshing} />}
        style={styles.scroller}>
        {isLoading && !account && !portfolio ? (
          <View style={styles.loadingBlock}>
            <Text selectable style={styles.loadingText}>Loading portfolio...</Text>
            <SkeletonRows count={4} />
            <PaperSection title="Positions">
              <SkeletonRows count={3} />
            </PaperSection>
          </View>
        ) : activeTab === 'assets' ? (
          <AssetsTab
            account={account}
            accountError={accountError}
            assetsExpanded={assetsExpanded}
            onOpenSell={openSell}
            onReset={() => setShowResetSheet(true)}
            onToggleExpanded={() => setAssetsExpanded((current) => !current)}
            portfolio={portfolio}
            portfolioError={portfolioError}
            positions={positions}
            router={router}
            valuationWarnings={valuationWarnings}
          />
        ) : (
          <HistoryTab
            currentSessionOnly={currentSessionOnly}
            canLoadMore={transactionPage + 1 < transactionTotalPages}
            errorMessage={transactionError}
            filteredTransactions={filteredTransactions}
            historySearch={historySearch}
            isLoading={isLoadingTransactions}
            isLoadingMore={isLoadingMoreTransactions}
            onLoadMore={() => void loadTransactionsPage(transactionPage + 1, true)}
            onOpenTransaction={openTransaction}
            onSearchChange={setHistorySearch}
            onSideChange={setSideFilter}
            onToggleCurrentSession={() => setCurrentSessionOnly((current) => !current)}
            sideFilter={sideFilter}
            stockCompanyMap={stockCompanyMap}
          />
        )}
      </ScrollView>

      <ResetCardSheet
        nextSessionNumber={(portfolio?.currentSessionNumber ?? account?.currentSessionNumber ?? 1) + 1}
        onConfirm={() => void resetPortfolio()}
        onClose={() => setShowResetSheet(false)}
        pending={isResetting}
        startingCash={account?.startingCash ?? portfolio?.startingCash ?? null}
        visible={showResetSheet}
      />
    </View>
  );
}

function AssetsTab({
  account,
  accountError,
  assetsExpanded,
  onOpenSell,
  onReset,
  onToggleExpanded,
  portfolio,
  portfolioError,
  positions,
  router,
  valuationWarnings,
}: {
  account: PaperTradingAccountResponse | null;
  accountError: string | null;
  assetsExpanded: boolean;
  onOpenSell: (position: PaperPositionResponse) => void;
  onReset: () => void;
  onToggleExpanded: () => void;
  portfolio: PaperPortfolioResponse | null;
  portfolioError: string | null;
  positions: PaperPositionResponse[];
  router: ReturnType<typeof useRouter>;
  valuationWarnings: string[];
}) {
  return (
    <>
      {accountError ? <ErrorBanner title="Account needs attention" message={accountError} /> : null}
      {portfolioError ? <ErrorBanner title="Portfolio needs attention" message={portfolioError} /> : null}

      {portfolio ? (
        <PaperSection>
          <View style={styles.assetSummary}>
            <PaperMetric
              label="Net Assets · USD"
              prominent
              value={formatPaperMoney(portfolio.totalPortfolioValue)}
            />
            <View style={styles.metricRow}>
              <View style={styles.metricWide}>
                <PaperMetric label="Holdings Value" value={formatPaperMoney(portfolio.estimatedMarketValue)} />
              </View>
              <View style={styles.metricMedium}>
                <PaperMetric
                  label="Today's P/L"
                  toneValue={portfolio.todayProfitLoss}
                  value={formatSignedPaperMoney(portfolio.todayProfitLoss ?? null)}
                />
              </View>
              <View style={styles.metricNarrow}>
                <PaperMetric
                  label="Today's P/L %"
                  toneValue={portfolio.todayProfitLossPercent}
                  value={formatPaperPercent(portfolio.todayProfitLossPercent ?? null)}
                />
              </View>
            </View>
            {assetsExpanded ? (
              <>
                <View style={styles.metricRow}>
                  <View style={styles.metricWide}>
                    <PaperMetric label="Remaining Cash" value={formatPaperMoney(portfolio.cashBalance)} />
                  </View>
                  <View style={styles.metricNarrow}>
                    <PaperMetric label="Fees Paid" value={formatPaperMoney(portfolio.totalFeesPaid)} />
                  </View>
                  <View style={styles.metricNarrow}>
                    <PaperMetric
                      label="P/L"
                      toneValue={portfolio.totalProfitLoss ?? portfolio.unrealizedProfitLoss}
                      value={formatSignedPaperMoney(portfolio.totalProfitLoss ?? portfolio.unrealizedProfitLoss)}
                    />
                  </View>
                </View>
                <View style={styles.sessionRow}>
                  <Text selectable style={styles.sessionText}>
                    Session {portfolio.currentSessionNumber ?? account?.currentSessionNumber ?? 'Unavailable'}
                  </Text>
                  <Text selectable style={styles.sessionText}>
                    Last reset {formatPaperDateTime(portfolio.lastResetAt ?? account?.lastResetAt)}
                  </Text>
                </View>
              </>
            ) : null}
            <Pressable
              accessibilityLabel={assetsExpanded ? 'Collapse assets' : 'Expand assets'}
              accessibilityRole="button"
              onPress={onToggleExpanded}
              style={styles.expandButton}>
              <IconSymbol
                color={Colors.light.mutedText}
                name={assetsExpanded ? 'chevron.up' : 'chevron.down'}
                size={20}
              />
            </Pressable>
          </View>
          {portfolio.todayProfitLossComplete === false ? (
            <Text selectable style={styles.todayNote}>
              Today's P/L is based on available delayed stored price data.
            </Text>
          ) : null}
          {valuationWarnings.map((warning) => (
            <InlineNotice key={warning} message={warning} tone="warn" />
          ))}
          <View style={styles.assetsActions}>
            <ActionButton
              label="View Stocks"
              onPress={() => router.push('/stocks' as Href)}
              style={styles.viewStocksButton}
            />
            <ActionButton
              label="Reset Portfolio"
              onPress={onReset}
              style={styles.resetButton}
              variant="danger"
            />
          </View>
        </PaperSection>
      ) : null}

      <View style={styles.positionsWrap}>
        <PaperSection title={`Positions(${positions.length})`}>
        {positions.length === 0 ? (
          <View style={styles.emptyWrap}>
            <View style={styles.emptyIcon}>
              <IconSymbol color="#B45309" name="hammer.fill" size={24} />
            </View>
            <Text selectable style={styles.emptyTitle}>
              No open positions
            </Text>
            <Text selectable style={styles.emptyDescription}>
              Open a stock and use Paper Trade to build a simulated portfolio.
            </Text>
            <ActionButton
              label="View Stock Page"
              onPress={() => router.push('/stocks' as Href)}
              style={styles.emptyStocksButton}
            />
          </View>
        ) : (
          <PositionsTable onOpenSell={onOpenSell} positions={positions} />
        )}
        </PaperSection>
      </View>
    </>
  );
}

function HistoryTab({
  canLoadMore,
  currentSessionOnly,
  errorMessage,
  filteredTransactions,
  historySearch,
  isLoading,
  isLoadingMore,
  onLoadMore,
  onOpenTransaction,
  onSearchChange,
  onSideChange,
  onToggleCurrentSession,
  sideFilter,
  stockCompanyMap,
}: {
  canLoadMore: boolean;
  currentSessionOnly: boolean;
  errorMessage: string | null;
  filteredTransactions: PaperTradeTransactionResponse[];
  historySearch: string;
  isLoading: boolean;
  isLoadingMore: boolean;
  onLoadMore: () => void;
  onOpenTransaction: (transaction: PaperTradeTransactionResponse) => void;
  onSearchChange: (value: string) => void;
  onSideChange: (side: SideFilter) => void;
  onToggleCurrentSession: () => void;
  sideFilter: SideFilter;
  stockCompanyMap: Map<string, string>;
}) {
  return (
    <>
      <PaperSection>
        <View style={styles.sideFilters}>
          {(['ALL', 'BUY', 'SELL', 'RESET'] as SideFilter[]).map((nextSide) => (
            <Pressable
              accessibilityLabel={`Show ${nextSide.toLowerCase()} transactions`}
              accessibilityRole="button"
              accessibilityState={{ selected: sideFilter === nextSide }}
              key={nextSide}
              onPress={() => onSideChange(nextSide)}
              style={[styles.filterChip, sideFilter === nextSide ? styles.filterChipActive : undefined]}>
              <Text style={[styles.filterChipText, sideFilter === nextSide ? styles.filterChipTextActive : undefined]}>
                {nextSide === 'ALL' ? 'All' : nextSide.charAt(0) + nextSide.slice(1).toLowerCase()}
              </Text>
            </Pressable>
          ))}
        </View>
        <View style={styles.searchBox}>
          <Text
            accessibilityLabel="Transaction search"
            numberOfLines={1}
            style={styles.hiddenSearchLabel}>
            Search
          </Text>
          <TextInputShim onChangeText={onSearchChange} value={historySearch} />
          <IconSymbol color={Colors.light.mutedText} name="magnifyingglass" size={19} />
        </View>
        <Pressable
          accessibilityLabel="Show current session only"
          accessibilityRole="switch"
          accessibilityState={{ checked: currentSessionOnly }}
          onPress={onToggleCurrentSession}
          style={styles.sessionToggle}>
          <View style={[styles.checkbox, currentSessionOnly ? styles.checkboxChecked : undefined]}>
            {currentSessionOnly ? <IconSymbol color="#FFFFFF" name="checkmark" size={14} /> : null}
          </View>
          <View style={styles.toggleCopy}>
            <Text style={styles.sessionToggleText}>Show current session only</Text>
          </View>
        </Pressable>
      </PaperSection>

      {errorMessage ? <ErrorBanner title="Transactions need attention" message={errorMessage} /> : null}

      {isLoading && filteredTransactions.length === 0 ? (
        <View style={styles.loadingBlock}>
          <Text selectable style={styles.loadingText}>Loading transactions...</Text>
          <SkeletonRows count={4} />
        </View>
      ) : filteredTransactions.length === 0 ? (
        <EmptyState title="No transactions found" description="Try another filter or complete a paper trade." />
      ) : (
        <View style={styles.rows}>
          <TransactionTableHeader />
          {filteredTransactions.map((transaction) => {
            const symbol = normalizeStockSymbol(transaction.symbol);
            return (
              <TransactionRow
                companyName={symbol ? stockCompanyMap.get(symbol) : null}
                hideCurrentSessionMeta={currentSessionOnly}
                key={transaction.transactionId ?? `${transaction.side}-${transaction.executedAt}`}
                onPress={onOpenTransaction}
                transaction={transaction}
              />
            );
          })}
          {canLoadMore ? (
            <View style={styles.loadMoreWrap}>
              <ActionButton
                disabled={isLoadingMore}
                label={isLoadingMore ? 'Loading more...' : 'Load more'}
                onPress={onLoadMore}
                style={styles.loadMoreButton}
              />
            </View>
          ) : null}
        </View>
      )}
    </>
  );
}

function TextInputShim({
  onChangeText,
  value,
}: {
  onChangeText: (value: string) => void;
  value: string;
}) {
  return (
    <TextInput
      accessibilityLabel="Search symbol or company"
      autoCapitalize="characters"
      onChangeText={onChangeText}
      placeholder="Search symbol or company"
      placeholderTextColor={Colors.light.mutedText}
      style={styles.historySearchInput}
      value={value}
    />
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
  fixedHeader: {
    backgroundColor: Colors.light.background,
    borderBottomColor: '#E5E7EB',
    borderBottomWidth: 1,
  },
  scroller: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  loadingBlock: {
    gap: Spacing.md,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
  },
  loadingText: {
    color: Colors.light.mutedText,
    fontSize: 13,
    fontWeight: '500',
  },
  assetSummary: {
    gap: Spacing.md,
  },
  metricRow: {
    flexDirection: 'row',
    gap: Spacing.md,
    marginTop: Spacing.xs,
  },
  metricWide: {
    flex: 0.45,
  },
  metricMedium: {
    flex: 0.275,
  },
  metricNarrow: {
    flex: 0.275,
  },
  todayNote: {
    color: Colors.light.mutedText,
    fontSize: 12,
    lineHeight: 17,
  },
  expandButton: {
    alignItems: 'center',
    alignSelf: 'center',
    height: 28,
    justifyContent: 'center',
    width: 44,
  },
  sessionRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    width: '100%',
  },
  sessionText: {
    color: Colors.light.mutedText,
    fontSize: 12,
    flexShrink: 1,
  },
  assetsActions: {
    flexDirection: 'row',
    gap: Spacing.sm,
    marginTop: Spacing.xs,
  },
  viewStocksButton: {
    backgroundColor: '#052344',
    borderColor: '#052344',
    borderRadius: 999,
    flex: 1,
    minHeight: 46,
  },
  resetButton: {
    borderColor: Colors.light.destructive,
    borderRadius: 999,
    flex: 1,
    minHeight: 46,
  },
  positionsWrap: {
    marginTop: Spacing.xl,
  },
  emptyWrap: {
    alignItems: 'center',
    backgroundColor: '#FFF7ED',
    borderColor: '#FED7AA',
    borderRadius: 12,
    borderWidth: 1,
    gap: Spacing.md,
    padding: Spacing.lg,
  },
  emptyTitle: {
    color: Colors.light.text,
    fontSize: 16,
    fontWeight: '700',
    textAlign: 'center',
  },
  emptyDescription: {
    color: Colors.light.mutedText,
    fontSize: 13,
    lineHeight: 18,
    textAlign: 'center',
  },
  emptyIcon: {
    alignItems: 'center',
    alignSelf: 'center',
    backgroundColor: '#FFFFFF',
    borderColor: '#FED7AA',
    borderRadius: 28,
    borderWidth: 1,
    height: 56,
    justifyContent: 'center',
    width: 56,
  },
  emptyStocksButton: {
    alignSelf: 'stretch',
    backgroundColor: '#052344',
    borderColor: '#052344',
    borderRadius: 999,
    minHeight: 46,
  },
  sideFilters: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  filterChip: {
    borderColor: Colors.light.border,
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  filterChipActive: {
    backgroundColor: '#052344',
    borderColor: '#052344',
  },
  filterChipText: {
    color: Colors.light.text,
    fontSize: 13,
    fontWeight: '600',
  },
  filterChipTextActive: {
    color: '#FFFFFF',
  },
  searchBox: {
    alignItems: 'center',
    backgroundColor: '#F8FAFC',
    borderColor: Colors.light.border,
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 44,
    paddingHorizontal: Spacing.md,
  },
  hiddenSearchLabel: {
    height: 0,
    opacity: 0,
    position: 'absolute',
    width: 0,
  },
  historySearchInput: {
    color: Colors.light.text,
    flex: 1,
    fontSize: 15,
    minHeight: 42,
  },
  sessionToggle: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.xs,
    marginTop: -Spacing.sm,
    minHeight: 38,
  },
  checkbox: {
    alignItems: 'center',
    borderColor: Colors.light.border,
    borderRadius: 4,
    borderWidth: 1,
    height: 18,
    justifyContent: 'center',
    width: 18,
  },
  checkboxChecked: {
    backgroundColor: '#052344',
    borderColor: '#052344',
  },
  toggleCopy: {
    flex: 1,
  },
  sessionToggleText: {
    color: Colors.light.text,
    fontSize: 12,
    fontWeight: '600',
  },
  rows: {
    backgroundColor: Colors.light.surface,
  },
  loadMoreWrap: {
    padding: Spacing.md,
  },
  loadMoreButton: {
    backgroundColor: '#052344',
    borderColor: '#052344',
    borderRadius: 999,
    minHeight: 42,
  },
});
