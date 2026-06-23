import { useFocusEffect } from '@react-navigation/native';
import { type Href, useRouter } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { paperTradingApi } from '@/api/paper-trading';
import { ActionButton } from '@/components/foundation/action-button';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import { FieldRow, InlineNotice, PaperHeader, PaperSection } from '@/components/paper-trading/paper-trading-ui';
import { Colors, Spacing } from '@/constants/theme';
import { useAuthSession } from '@/providers/auth-session-provider';
import type { PaperTradeTransactionResponse } from '@/types/paper-trading';
import {
  formatPaperDateTime,
  formatPaperMoney,
  formatQuantity,
  getPaperTradingApiErrorMessage,
  getTransactionDisplayTitle,
  getTransactionSideLabel,
  isResetTransaction,
} from '@/utils/paper-trading-display';

type PaperTradingTransactionDetailScreenProps = {
  transactionId: string;
};

export function PaperTradingTransactionDetailScreen({
  transactionId,
}: PaperTradingTransactionDetailScreenProps) {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { credentials } = useAuthSession();
  const requestIdRef = useRef(0);
  const [transaction, setTransaction] = useState<PaperTradeTransactionResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadTransaction = useCallback(
    async (mode: 'focus' | 'refresh' = 'focus') => {
      const requestId = requestIdRef.current + 1;
      requestIdRef.current = requestId;

      if (!credentials) {
        setErrorMessage('Sign in again to load this transaction.');
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
        const response = await paperTradingApi.getTransaction(credentials, transactionId);
        if (requestIdRef.current !== requestId) {
          return;
        }
        setTransaction(response);
      } catch (error) {
        if (requestIdRef.current !== requestId) {
          return;
        }
        setErrorMessage(getPaperTradingApiErrorMessage(error, 'Transaction detail could not be loaded.'));
      } finally {
        if (requestIdRef.current === requestId) {
          setIsLoading(false);
          setIsRefreshing(false);
        }
      }
    },
    [credentials, transactionId],
  );

  useFocusEffect(
    useCallback(() => {
      void loadTransaction('focus');
      return undefined;
    }, [loadTransaction]),
  );

  const reset = transaction ? isResetTransaction(transaction) : false;

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
      refreshControl={<RefreshControl onRefresh={() => void loadTransaction('refresh')} refreshing={isRefreshing} />}
      style={styles.container}>
      <PaperHeader
        onBack={() => router.replace('/paper-trading/transactions' as Href)}
        onRefresh={() => void loadTransaction('refresh')}
        subtitle={`Transaction ${transactionId}`}
        title="Transaction Detail"
      />

      {errorMessage ? <ErrorBanner title="Transaction needs attention" message={errorMessage} /> : null}

      {isLoading && !transaction ? (
        <View style={styles.loadingBlock}>
          <Text selectable style={styles.loadingText}>
            Loading transaction...
          </Text>
          <SkeletonRows count={3} />
        </View>
      ) : transaction ? (
        <PaperSection title={getTransactionDisplayTitle(transaction)}>
          {reset ? (
            <InlineNotice
              message="This row records a simulated portfolio reset. It is not linked to a stock and has no execution price."
            />
          ) : null}
          <FieldRow label="Side" value={getTransactionSideLabel(transaction.side)} />
          <FieldRow label="Symbol" value={reset ? 'Not applicable' : transaction.symbol ?? 'Unavailable'} />
          <FieldRow label="Quantity" value={reset ? 'Not applicable' : formatQuantity(transaction.quantity)} />
          <FieldRow label="Execution price" value={reset ? 'Not applicable' : formatPaperMoney(transaction.executionPrice ?? transaction.price)} />
          <FieldRow label="Gross amount" value={reset ? 'Not applicable' : formatPaperMoney(transaction.grossAmount)} />
          <FieldRow label="Fee" value={reset ? 'Not applicable' : formatPaperMoney(transaction.fee)} />
          <FieldRow label="Net amount" value={reset ? 'Not applicable' : formatPaperMoney(transaction.netAmount ?? transaction.totalAmount)} />
          <FieldRow label="Realized P/L" value={reset ? 'Not applicable' : formatPaperMoney(transaction.realizedProfitLoss)} />
          <FieldRow label="Cash after" value={formatPaperMoney(transaction.cashBalanceAfter)} />
          <FieldRow label="Session" value={transaction.sessionNumber == null ? 'Unavailable' : String(transaction.sessionNumber)} />
          <FieldRow label="Current session" value={transaction.isCurrentSession == null ? 'Unavailable' : transaction.isCurrentSession ? 'Yes' : 'No'} />
          <FieldRow label="Executed at" value={formatPaperDateTime(transaction.executedAt ?? transaction.transactionTime)} />
        </PaperSection>
      ) : (
        <View style={styles.retryBlock}>
          <ActionButton label="Back to Transactions" onPress={() => router.replace('/paper-trading/transactions' as Href)} variant="ghost" />
          <ActionButton label="Try again" onPress={() => void loadTransaction('refresh')} />
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
  retryBlock: {
    gap: Spacing.sm,
    padding: Spacing.md,
  },
});
