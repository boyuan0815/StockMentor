import { useFocusEffect } from '@react-navigation/native';
import { type Href, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Animated, Modal, Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { paperTradingApi } from '@/api/paper-trading';
import { stocksApi } from '@/api/stocks';
import { ActionButton } from '@/components/foundation/action-button';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
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
  formatSignedPaperMoney,
  getPaperTradingApiErrorMessage,
  toPaperNumber,
} from '@/utils/paper-trading-display';
import { normalizeStockSymbol } from '@/utils/stock-display';

type PortfolioTab = 'assets' | 'history';
type SideFilter = 'ALL' | 'BUY' | 'SELL' | 'RESET';

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

      const [accountResult, portfolioResult, transactionResult, stockResult] = await Promise.allSettled([
        paperTradingApi.getAccount(credentials),
        paperTradingApi.getPortfolio(credentials),
        paperTradingApi.getTransactions(credentials, { currentSessionOnly, size: 50 }),
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

      if (transactionResult.status === 'fulfilled') {
        setTransactions(transactionResult.value ?? []);
        setTransactionError(null);
      } else {
        setTransactionError(
          getPaperTradingApiErrorMessage(transactionResult.reason, 'Transactions could not be loaded.'),
        );
      }

      if (stockResult.status === 'fulfilled') {
        setStocks(stockResult.value.stocks ?? []);
      }

      inFlightRef.current = false;
      hasLoadedRef.current = true;
      setIsLoading(false);
      setIsRefreshing(false);
    },
    [credentials, currentSessionOnly],
  );

  useMinuteBoundaryRefresh({
    enabled: !showResetSheet && !isResetting,
    onRefresh: () => loadPortfolioData('soft'),
  });

  useEffect(() => {
    void loadPortfolioData('soft');
  }, [currentSessionOnly, loadPortfolioData]);

  const handleRefresh = () => {
    guardedRefresh(() => void loadPortfolioData('refresh'));
  };

  const positions = portfolio?.positions ?? [];
  const stockCompanyMap = useMemo(
    () => new Map(stocks.map((stock) => [stock.symbol, stock.companyName])),
    [stocks],
  );
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
            errorMessage={transactionError}
            filteredTransactions={filteredTransactions}
            historySearch={historySearch}
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
        netAssets={portfolio?.totalPortfolioValue ?? null}
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
              <PaperMetric label="Holdings Value" value={formatPaperMoney(portfolio.estimatedMarketValue)} />
              <PaperMetric
                label="Unrealized P/L"
                toneValue={portfolio.unrealizedProfitLoss}
                value={formatSignedPaperMoney(portfolio.unrealizedProfitLoss)}
              />
            </View>
            {assetsExpanded ? (
              <>
                <View style={styles.metricRow}>
                  <PaperMetric label="Cash" value={formatPaperMoney(portfolio.cashBalance)} />
                  <PaperMetric label="Fees Paid" value={formatPaperMoney(portfolio.totalFeesPaid)} />
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
  currentSessionOnly,
  errorMessage,
  filteredTransactions,
  historySearch,
  onOpenTransaction,
  onSearchChange,
  onSideChange,
  onToggleCurrentSession,
  sideFilter,
  stockCompanyMap,
}: {
  currentSessionOnly: boolean;
  errorMessage: string | null;
  filteredTransactions: PaperTradeTransactionResponse[];
  historySearch: string;
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

      {filteredTransactions.length === 0 ? (
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

function ResetCardSheet({
  netAssets,
  onClose,
  onConfirm,
  pending,
  startingCash,
  visible,
}: {
  netAssets: PaperPortfolioResponse['totalPortfolioValue'];
  onClose: () => void;
  onConfirm: () => void;
  pending: boolean;
  startingCash: PaperTradingAccountResponse['startingCash'];
  visible: boolean;
}) {
  const tier = getResetCardTier(netAssets);
  const startingCashText = formatPaperMoney(startingCash);
  const [mounted, setMounted] = useState(visible);
  const slideAnim = useRef(new Animated.Value(visible ? 1 : 0)).current;

  useEffect(() => {
    if (visible) {
      setMounted(true);
      slideAnim.setValue(0);
      Animated.timing(slideAnim, {
        duration: 230,
        toValue: 1,
        useNativeDriver: true,
      }).start();
      return;
    }

    Animated.timing(slideAnim, {
      duration: 190,
      toValue: 0,
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (finished) {
        setMounted(false);
      }
    });
  }, [slideAnim, visible]);

  if (!mounted) {
    return null;
  }

  const translateY = slideAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [420, 0],
  });

  return (
    <Modal
      animationType="none"
      onRequestClose={() => {
        if (!pending) {
          onClose();
        }
      }}
      transparent
      visible={mounted}>
      <View style={styles.sheetBackdrop}>
        <Pressable
          accessibilityLabel="Close reset card"
          disabled={pending}
          onPress={onClose}
          style={StyleSheet.absoluteFill}
        />
      </View>
      <View pointerEvents="box-none" style={styles.sheetLayer}>
        <Animated.View style={[styles.sheet, { transform: [{ translateY }] }]}>
          <View style={styles.dragHandle} />
          <View style={styles.sheetTitleRow}>
            <Text selectable style={styles.sheetTitle}>Reset Portfolio</Text>
            <Pressable
              accessibilityLabel="Close reset sheet"
              accessibilityRole="button"
              disabled={pending}
              onPress={onClose}
              style={styles.sheetClose}>
              <IconSymbol color={Colors.light.text} name="xmark" size={22} />
            </Pressable>
          </View>
          <View style={[styles.resetCard, { borderColor: tier.border, backgroundColor: tier.background }]}>
            <Text selectable style={[styles.resetCardKicker, { color: tier.subText }]}>{tier.label} reset</Text>
            <Text selectable style={[styles.resetCardTitle, { color: tier.text }]}>Portfolio Reset</Text>
            <Text selectable style={[styles.resetCardSub, { color: tier.subText }]}>Practice account reset</Text>
            <Text selectable style={[styles.resetCardFine, { color: tier.subText }]}>
              Restores simulated Net Assets · USD to {startingCashText}.
            </Text>
          </View>
          <View style={styles.resetInfo}>
            <Text selectable style={styles.resetInfoTitle}>Attributes</Text>
            <Text selectable style={styles.resetInfoText}>
              Restores simulated Net Assets · USD to {startingCashText}.
            </Text>
            <View style={styles.resetDivider} />
            <Text selectable style={styles.resetInfoTitle}>Effect</Text>
            <Text selectable style={styles.resetInfoText}>
              Open positions are cleared, a new session starts, and the action cannot be undone.
            </Text>
          </View>
          <ActionButton
            disabled={pending}
            label={pending ? 'Resetting...' : 'Reset Portfolio'}
            onPress={onConfirm}
            style={styles.redeemButton}
          />
        </Animated.View>
      </View>
    </Modal>
  );
}

function getResetCardTier(value: PaperPortfolioResponse['totalPortfolioValue']) {
  const numericValue = toPaperNumber(value);
  if (numericValue !== null && numericValue >= 2_500_000) {
    return { background: '#E5E7EB', border: '#94A3B8', label: 'Platinum', text: '#111827', subText: '#475569' };
  }
  if (numericValue !== null && numericValue >= 1_800_000) {
    return { background: '#FEF3C7', border: '#D97706', label: 'Gold', text: '#111827', subText: '#92400E' };
  }
  if (numericValue !== null && numericValue >= 1_300_000) {
    return { background: '#F8FAFC', border: '#94A3B8', label: 'Silver', text: '#111827', subText: '#475569' };
  }
  return { background: '#111827', border: '#334155', label: 'Standard', text: '#FFFFFF', subText: '#CBD5E1' };
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
    gap: Spacing.sm,
  },
  metricRow: {
    flexDirection: 'row',
    gap: Spacing.lg,
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
    marginTop: Spacing.lg,
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
  sheetBackdrop: {
    backgroundColor: 'rgba(15, 23, 42, 0.42)',
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  sheetLayer: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  sheet: {
    backgroundColor: Colors.light.surface,
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    gap: Spacing.lg,
    paddingBottom: Spacing.lg,
    paddingHorizontal: Spacing.md,
    paddingTop: Spacing.md,
  },
  dragHandle: {
    alignSelf: 'center',
    backgroundColor: '#D1D5DB',
    borderRadius: 999,
    height: 5,
    width: 54,
  },
  sheetTitle: {
    color: Colors.light.text,
    flex: 1,
    fontSize: 22,
    fontWeight: '800',
  },
  sheetTitleRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
  },
  sheetClose: {
    alignItems: 'center',
    height: 38,
    justifyContent: 'center',
    width: 38,
  },
  resetCard: {
    alignSelf: 'center',
    borderRadius: 18,
    borderWidth: 1,
    gap: Spacing.sm,
    minHeight: 210,
    padding: Spacing.lg,
    shadowColor: '#000000',
    shadowOffset: { height: 12, width: 0 },
    shadowOpacity: 0.2,
    shadowRadius: 24,
    width: '88%',
    elevation: 8,
  },
  resetCardTitle: {
    color: '#FFFFFF',
    fontSize: 26,
    fontWeight: '800',
  },
  resetCardKicker: {
    color: '#CBD5E1',
    fontSize: 13,
    fontWeight: '800',
    letterSpacing: 0,
    textTransform: 'uppercase',
  },
  resetCardSub: {
    color: '#CBD5E1',
    fontSize: 16,
  },
  resetCardFine: {
    fontSize: 13,
    lineHeight: 18,
    marginTop: 'auto',
  },
  resetInfo: {
    gap: Spacing.sm,
  },
  resetDivider: {
    backgroundColor: Colors.light.border,
    height: 1,
    marginVertical: Spacing.xs,
  },
  resetInfoTitle: {
    color: Colors.light.text,
    fontSize: 16,
    fontWeight: '800',
  },
  resetInfoText: {
    color: Colors.light.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
  redeemButton: {
    backgroundColor: '#111827',
    borderColor: '#111827',
  },
});
