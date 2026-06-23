import { useFocusEffect } from '@react-navigation/native';
import { type Href, useRouter } from 'expo-router';
import { useCallback, useMemo, useRef, useState } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { paperTradingApi } from '@/api/paper-trading';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import { PaperHeader, PaperSection, TransactionRow } from '@/components/paper-trading/paper-trading-ui';
import { Colors, Spacing } from '@/constants/theme';
import { useRefreshCooldown } from '@/hooks/use-refresh-cooldown';
import { useAuthSession } from '@/providers/auth-session-provider';
import type { PaperTradeTransactionResponse } from '@/types/paper-trading';
import { getPaperTradingApiErrorMessage } from '@/utils/paper-trading-display';
import { normalizeStockSymbol } from '@/utils/stock-display';

type SideFilter = 'ALL' | 'BUY' | 'SELL' | 'RESET';

export function PaperTradingTransactionsScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const guardedRefresh = useRefreshCooldown();
  const { credentials } = useAuthSession();
  const requestIdRef = useRef(0);
  const [transactions, setTransactions] = useState<PaperTradeTransactionResponse[]>([]);
  const [side, setSide] = useState<SideFilter>('ALL');
  const [symbol, setSymbol] = useState('');
  const [currentSessionOnly, setCurrentSessionOnly] = useState(true);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const normalizedSymbol = useMemo(() => normalizeStockSymbol(symbol), [symbol]);

  const loadTransactions = useCallback(
    async (mode: 'focus' | 'refresh' = 'focus') => {
      const requestId = requestIdRef.current + 1;
      requestIdRef.current = requestId;

      if (!credentials) {
        setErrorMessage('Sign in again to load transactions.');
        setIsLoading(false);
        setIsRefreshing(false);
        return;
      }

      if (mode === 'refresh') {
        setIsRefreshing(true);
      } else {
        setIsLoading(true);
      }
      setErrorMessage(null);

      try {
        const response = await paperTradingApi.getTransactions(credentials, {
          currentSessionOnly,
          side: side === 'ALL' ? null : side,
          size: 50,
          symbol: normalizedSymbol || null,
        });
        if (requestIdRef.current !== requestId) {
          return;
        }
        setTransactions(response ?? []);
      } catch (error) {
        if (requestIdRef.current !== requestId) {
          return;
        }
        setErrorMessage(getPaperTradingApiErrorMessage(error, 'Transactions could not be loaded.'));
      } finally {
        if (requestIdRef.current === requestId) {
          setIsLoading(false);
          setIsRefreshing(false);
        }
      }
    },
    [credentials, currentSessionOnly, normalizedSymbol, side],
  );

  useFocusEffect(
    useCallback(() => {
      void loadTransactions('focus');
      return undefined;
    }, [loadTransactions]),
  );

  const handleRefresh = () => {
    guardedRefresh(() => void loadTransactions('refresh'));
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
      keyboardShouldPersistTaps="handled"
      overScrollMode="never"
      refreshControl={<RefreshControl onRefresh={handleRefresh} refreshing={isRefreshing} />}
      style={styles.container}>
      <PaperHeader
        onBack={() => router.replace('/paper-trading' as Href)}
        onRefresh={handleRefresh}
        subtitle={currentSessionOnly ? 'Current session' : 'All sessions'}
        title="Transactions"
      />

      <PaperSection title="Filters">
        <View style={styles.sideFilters}>
          {(['ALL', 'BUY', 'SELL', 'RESET'] as SideFilter[]).map((nextSide) => (
            <Pressable
              accessibilityLabel={`Show ${nextSide.toLowerCase()} transactions`}
              accessibilityRole="button"
              accessibilityState={{ selected: side === nextSide }}
              key={nextSide}
              onPress={() => setSide(nextSide)}
              style={[styles.filterChip, side === nextSide ? styles.filterChipActive : undefined]}>
              <Text style={[styles.filterChipText, side === nextSide ? styles.filterChipTextActive : undefined]}>
                {nextSide === 'ALL' ? 'All' : nextSide.charAt(0) + nextSide.slice(1).toLowerCase()}
              </Text>
            </Pressable>
          ))}
        </View>
        <TextInput
          accessibilityLabel="Filter by symbol"
          autoCapitalize="characters"
          onChangeText={setSymbol}
          placeholder="Symbol filter"
          placeholderTextColor={Colors.light.mutedText}
          style={styles.symbolInput}
          value={symbol}
        />
        <Pressable
          accessibilityLabel="Toggle current session transactions"
          accessibilityRole="switch"
          accessibilityState={{ checked: currentSessionOnly }}
          onPress={() => setCurrentSessionOnly((current) => !current)}
          style={styles.sessionToggle}>
          <View style={[styles.checkbox, currentSessionOnly ? styles.checkboxChecked : undefined]} />
          <Text style={styles.sessionToggleText}>Current session only</Text>
        </Pressable>
      </PaperSection>

      {errorMessage ? <ErrorBanner title="Transactions need attention" message={errorMessage} /> : null}

      {isLoading ? (
        <View style={styles.loadingBlock}>
          <Text selectable style={styles.loadingText}>
            Loading transactions...
          </Text>
          <SkeletonRows count={5} />
        </View>
      ) : transactions.length === 0 ? (
        <EmptyState title="No transactions found" description="Try another filter or complete a practice trade." />
      ) : (
        <View style={styles.rows}>
          {transactions.map((transaction) => (
            <TransactionRow
              key={transaction.transactionId ?? `${transaction.side}-${transaction.executedAt}`}
              onPress={openTransaction}
              transaction={transaction}
            />
          ))}
        </View>
      )}
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
  symbolInput: {
    backgroundColor: '#F8FAFC',
    borderColor: Colors.light.border,
    borderRadius: 8,
    borderWidth: 1,
    color: Colors.light.text,
    fontSize: 15,
    minHeight: 44,
    paddingHorizontal: Spacing.md,
  },
  sessionToggle: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 44,
  },
  checkbox: {
    borderColor: Colors.light.border,
    borderRadius: 4,
    borderWidth: 1,
    height: 18,
    width: 18,
  },
  checkboxChecked: {
    backgroundColor: '#052344',
    borderColor: '#052344',
  },
  sessionToggleText: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '500',
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
  rows: {
    backgroundColor: Colors.light.surface,
  },
});
