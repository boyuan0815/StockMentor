import { useFocusEffect } from '@react-navigation/native';
import { type Href, useRouter } from 'expo-router';
import { useCallback, useMemo, useRef, useState } from 'react';
import { RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { paperTradingApi } from '@/api/paper-trading';
import { ActionButton } from '@/components/foundation/action-button';
import { ConfirmationPanel } from '@/components/foundation/confirmation-panel';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import {
  InlineNotice,
  PaperHeader,
  PaperMetric,
  PaperSection,
  PositionRow,
  TransactionRow,
} from '@/components/paper-trading/paper-trading-ui';
import { Colors, Spacing } from '@/constants/theme';
import { useRefreshCooldown } from '@/hooks/use-refresh-cooldown';
import { useAuthSession } from '@/providers/auth-session-provider';
import { useToast } from '@/providers/toast-provider';
import type {
  PaperPortfolioResponse,
  PaperTradeTransactionResponse,
  PaperTradingAccountResponse,
} from '@/types/paper-trading';
import {
  formatPaperDateTime,
  formatPaperMoney,
  formatPaperPercent,
  formatSignedPaperMoney,
  getPaperTradingApiErrorMessage,
} from '@/utils/paper-trading-display';

export function PaperTradingOverviewScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const guardedRefresh = useRefreshCooldown();
  const { credentials } = useAuthSession();
  const { showToast } = useToast();
  const requestIdRef = useRef(0);
  const inFlightRef = useRef(false);
  const [account, setAccount] = useState<PaperTradingAccountResponse | null>(null);
  const [portfolio, setPortfolio] = useState<PaperPortfolioResponse | null>(null);
  const [transactions, setTransactions] = useState<PaperTradeTransactionResponse[]>([]);
  const [accountError, setAccountError] = useState<string | null>(null);
  const [portfolioError, setPortfolioError] = useState<string | null>(null);
  const [transactionError, setTransactionError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [showResetConfirm, setShowResetConfirm] = useState(false);
  const [isResetting, setIsResetting] = useState(false);

  const loadPracticeData = useCallback(
    async (mode: 'focus' | 'refresh' = 'focus') => {
      if (inFlightRef.current) {
        return;
      }

      const requestId = requestIdRef.current + 1;
      requestIdRef.current = requestId;

      if (!credentials) {
        setAccountError('Sign in again to load practice trading.');
        setPortfolioError(null);
        setTransactionError(null);
        setIsLoading(false);
        setIsRefreshing(false);
        return;
      }

      inFlightRef.current = true;
      if (mode === 'refresh') {
        setIsRefreshing(true);
      } else {
        setIsLoading(true);
      }

      const [accountResult, portfolioResult, transactionResult] = await Promise.allSettled([
        paperTradingApi.getAccount(credentials),
        paperTradingApi.getPortfolio(credentials),
        paperTradingApi.getTransactions(credentials, { currentSessionOnly: true, size: 5 }),
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
          getPaperTradingApiErrorMessage(transactionResult.reason, 'Recent transactions could not be loaded.'),
        );
      }

      inFlightRef.current = false;
      setIsLoading(false);
      setIsRefreshing(false);
    },
    [credentials],
  );

  useFocusEffect(
    useCallback(() => {
      void loadPracticeData('focus');
      return undefined;
    }, [loadPracticeData]),
  );

  const handleRefresh = () => {
    guardedRefresh(() => void loadPracticeData('refresh'));
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

  const openBuy = () => {
    router.push({ pathname: '/paper-trading/buy', params: { from: 'practice' } } as Href);
  };

  const openSell = (symbol?: string) => {
    router.push({
      pathname: '/paper-trading/sell',
      params: symbol ? { from: 'practice', symbol } : { from: 'practice' },
    } as Href);
  };

  const openTransactions = () => {
    router.push('/paper-trading/transactions' as Href);
  };

  const resetPortfolio = async () => {
    if (!credentials || isResetting) {
      return;
    }

    setIsResetting(true);
    try {
      await paperTradingApi.resetPortfolio(credentials);
      setShowResetConfirm(false);
      showToast('Simulated portfolio reset.');
      await loadPracticeData('refresh');
    } catch (error) {
      showToast(getPaperTradingApiErrorMessage(error, 'Simulated portfolio could not be reset.'), 'error');
    } finally {
      setIsResetting(false);
    }
  };

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
      overScrollMode="never"
      refreshControl={<RefreshControl onRefresh={handleRefresh} refreshing={isRefreshing} />}
      style={styles.container}>
      <PaperHeader onRefresh={handleRefresh} subtitle="Simulated practice account" title="Practice" />

      {isLoading && !account && !portfolio ? (
        <View style={styles.loadingBlock}>
          <Text selectable style={styles.loadingText}>
            Loading practice account...
          </Text>
          <SkeletonRows count={4} />
        </View>
      ) : null}

      {accountError ? <ErrorBanner title="Account needs attention" message={accountError} /> : null}
      {portfolioError ? <ErrorBanner title="Portfolio needs attention" message={portfolioError} /> : null}

      {portfolio ? (
        <PaperSection title="Assets">
          <View style={styles.metricGrid}>
            <PaperMetric label="Net assets" value={formatPaperMoney(portfolio.totalPortfolioValue)} />
            <PaperMetric label="Cash" value={formatPaperMoney(portfolio.cashBalance)} />
            <PaperMetric label="Holdings value" value={formatPaperMoney(portfolio.estimatedMarketValue)} />
            <PaperMetric
              label="Total return"
              toneValue={portfolio.returnPercentage}
              value={formatPaperPercent(portfolio.returnPercentage)}
            />
            <PaperMetric
              label="Unrealized P/L"
              toneValue={portfolio.unrealizedProfitLoss}
              value={formatSignedPaperMoney(portfolio.unrealizedProfitLoss)}
            />
            <PaperMetric
              label="Realized P/L"
              toneValue={portfolio.realizedProfitLoss}
              value={formatSignedPaperMoney(portfolio.realizedProfitLoss)}
            />
            <PaperMetric label="Fees paid" value={formatPaperMoney(portfolio.totalFeesPaid)} />
          </View>
          <View style={styles.sessionRow}>
            <Text selectable style={styles.sessionText}>
              Session {portfolio.currentSessionNumber ?? account?.currentSessionNumber ?? 'Unavailable'}
            </Text>
            <Text selectable style={styles.sessionText}>
              Last reset {formatPaperDateTime(portfolio.lastResetAt ?? account?.lastResetAt)}
            </Text>
          </View>
          {valuationWarnings.map((warning) => (
            <InlineNotice key={warning} message={warning} tone="warn" />
          ))}
          <View style={styles.actionGrid}>
            <ActionButton label="Buy Practice Stock" onPress={openBuy} style={styles.actionButton} />
            <ActionButton
              disabled={positions.length === 0}
              label="Sell from held position"
              onPress={() => openSell()}
              style={styles.actionButton}
              variant="ghost"
            />
            <ActionButton label="View Transactions" onPress={openTransactions} style={styles.actionButton} variant="ghost" />
            <ActionButton
              label="Reset Simulated Portfolio"
              onPress={() => setShowResetConfirm(true)}
              style={styles.actionButton}
              variant="danger"
            />
          </View>
          {showResetConfirm ? (
            <ConfirmationPanel
              confirmLabel="Reset portfolio"
              message="Simulated cash returns to the starting balance, open positions are cleared, a new session starts, and this action cannot be undone."
              onCancel={() => setShowResetConfirm(false)}
              onConfirm={() => void resetPortfolio()}
              pending={isResetting}
              pendingLabel="Resetting..."
              title="Reset simulated portfolio?"
            />
          ) : null}
        </PaperSection>
      ) : null}

      <PaperSection title="Positions">
        {positions.length === 0 ? (
          <View style={styles.emptyWrap}>
            <EmptyState
              title="No open positions"
              description="Buy a practice stock to start building a simulated portfolio."
            />
            <ActionButton label="Browse Stocks" onPress={() => router.push('/stocks' as Href)} variant="ghost" />
          </View>
        ) : (
          positions.map((position) => (
            <PositionRow
              key={position.positionId ?? position.symbol}
              onOpenSell={(nextPosition) => openSell(nextPosition.symbol)}
              position={position}
            />
          ))
        )}
      </PaperSection>

      <PaperSection
        action={
          <ActionButton label="View all" onPress={openTransactions} style={styles.smallAction} variant="ghost" />
        }
        title="Recent transactions">
        {transactionError ? (
          <ErrorBanner title="Transactions need attention" message={transactionError} />
        ) : transactions.length === 0 ? (
          <EmptyState title="No current-session transactions" description="Completed practice trades will appear here." />
        ) : (
          transactions.map((transaction) => (
            <TransactionRow key={transaction.transactionId ?? `${transaction.side}-${transaction.executedAt}`} transaction={transaction} />
          ))
        )}
      </PaperSection>
    </ScrollView>
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
  metricGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.lg,
  },
  sessionRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.md,
  },
  sessionText: {
    color: Colors.light.mutedText,
    fontSize: 12,
  },
  actionGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  actionButton: {
    flexGrow: 1,
    minWidth: 150,
  },
  smallAction: {
    minHeight: 32,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.xs,
  },
  emptyWrap: {
    gap: Spacing.md,
  },
});
