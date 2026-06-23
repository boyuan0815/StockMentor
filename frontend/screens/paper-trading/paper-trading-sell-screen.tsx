import { useFocusEffect } from '@react-navigation/native';
import { type Href, useRouter } from 'expo-router';
import { useCallback, useMemo, useRef, useState } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { paperTradingApi } from '@/api/paper-trading';
import { ActionButton } from '@/components/foundation/action-button';
import { ConfirmationPanel } from '@/components/foundation/confirmation-panel';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { SkeletonRows } from '@/components/foundation/skeleton-block';
import {
  FieldRow,
  InlineNotice,
  PaperHeader,
  PaperSection,
  ResultPanel,
} from '@/components/paper-trading/paper-trading-ui';
import { Colors, Spacing } from '@/constants/theme';
import { useAuthSession } from '@/providers/auth-session-provider';
import { useToast } from '@/providers/toast-provider';
import type { BasicAuthCredentials } from '@/types/auth';
import type { PaperPortfolioResponse, PaperPositionResponse, PaperTradeExecutionResponse } from '@/types/paper-trading';
import {
  formatPaperMoney,
  formatQuantity,
  getPaperTradingApiErrorMessage,
  getTradeResultSummary,
  validatePaperTradeQuantity,
} from '@/utils/paper-trading-display';
import { normalizeStockSymbol } from '@/utils/stock-display';

type PaperTradingSellScreenProps = {
  from?: string;
  symbol?: string;
};

export function PaperTradingSellScreen({ from, symbol }: PaperTradingSellScreenProps) {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { credentials } = useAuthSession();
  const { showToast } = useToast();
  const requestIdRef = useRef(0);
  const routeSymbol = normalizeStockSymbol(symbol);
  const [portfolio, setPortfolio] = useState<PaperPortfolioResponse | null>(null);
  const [selectedSymbol, setSelectedSymbol] = useState(routeSymbol);
  const [quantity, setQuantity] = useState('');
  const [quantityError, setQuantityError] = useState<string | null>(null);
  const [confirmQuantity, setConfirmQuantity] = useState<number | null>(null);
  const [result, setResult] = useState<PaperTradeExecutionResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadPortfolio = useCallback(
    async (mode: 'focus' | 'refresh' = 'focus') => {
      const requestId = requestIdRef.current + 1;
      requestIdRef.current = requestId;

      if (!credentials) {
        setErrorMessage('Sign in again to load held positions.');
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
        const response = await paperTradingApi.getPortfolio(credentials);
        if (requestIdRef.current !== requestId) {
          return;
        }
        const positions = response.positions ?? [];
        setPortfolio(response);
        setSelectedSymbol((current) => {
          if (routeSymbol && positions.some((position) => position.symbol === routeSymbol)) {
            return routeSymbol;
          }
          if (current && positions.some((position) => position.symbol === current)) {
            return current;
          }
          return positions[0]?.symbol ?? '';
        });
      } catch (error) {
        if (requestIdRef.current !== requestId) {
          return;
        }
        setErrorMessage(getPaperTradingApiErrorMessage(error, 'Held positions could not be loaded.'));
      } finally {
        if (requestIdRef.current === requestId) {
          setIsLoading(false);
          setIsRefreshing(false);
        }
      }
    },
    [credentials, routeSymbol],
  );

  useFocusEffect(
    useCallback(() => {
      setSelectedSymbol(routeSymbol);
      setResult(null);
      setConfirmQuantity(null);
      setQuantityError(null);
      void loadPortfolio('focus');
      return undefined;
    }, [loadPortfolio, routeSymbol]),
  );

  const positions = portfolio?.positions ?? [];
  const selectedPosition = useMemo(
    () => positions.find((position) => position.symbol === selectedSymbol) ?? null,
    [positions, selectedSymbol],
  );
  const routeSymbolIsUnheld = Boolean(routeSymbol) && !positions.some((position) => position.symbol === routeSymbol);

  const handleBack = () => {
    router.replace('/paper-trading' as Href);
  };

  const reviewTrade = () => {
    if (!selectedPosition) {
      setQuantityError('Choose a held position first.');
      return;
    }

    const validation = validatePaperTradeQuantity(quantity, {
      maxHolding: selectedPosition.quantity,
    });

    if (validation.message || validation.quantity === null) {
      setQuantityError(validation.message);
      return;
    }

    setQuantityError(null);
    setConfirmQuantity(validation.quantity);
  };

  const submitTrade = async () => {
    if (!credentials || !confirmQuantity || !selectedPosition || isSubmitting) {
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await paperTradingApi.sell(credentials, {
        quantity: confirmQuantity,
        symbol: selectedPosition.symbol,
      });
      setResult(response);
      setConfirmQuantity(null);
      showToast(`${selectedPosition.symbol} practice sell completed.`);
      void reloadPaperTradingReads(credentials);
    } catch (error) {
      showToast(getPaperTradingApiErrorMessage(error, 'Practice sell could not be completed.'), 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const continueTrading = () => {
    setResult(null);
    setQuantity('');
    setQuantityError(null);
    setConfirmQuantity(null);
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
      refreshControl={<RefreshControl onRefresh={() => void loadPortfolio('refresh')} refreshing={isRefreshing} />}
      style={styles.container}>
      <PaperHeader onBack={handleBack} onRefresh={() => void loadPortfolio('refresh')} subtitle="Held positions only" title="Practice Sell" />

      {errorMessage ? <ErrorBanner title="Sell ticket needs attention" message={errorMessage} /> : null}

      <PaperSection title="Position">
        {isLoading ? (
          <SkeletonRows count={3} />
        ) : positions.length === 0 ? (
          <View style={styles.emptyWrap}>
            <EmptyState title="No holdings to sell" description="Buy a practice stock before opening a sell ticket." />
            <ActionButton label="Back to Practice" onPress={handleBack} variant="ghost" />
          </View>
        ) : (
          <View style={styles.selectorRows}>
            {routeSymbolIsUnheld ? <InlineNotice message="No holding for this symbol." tone="warn" /> : null}
            {positions.map((position) => (
              <PositionSelectorRow
                key={position.positionId ?? position.symbol}
                onPress={() => {
                  setSelectedSymbol(position.symbol);
                  setResult(null);
                }}
                position={position}
                selected={position.symbol === selectedSymbol}
              />
            ))}
          </View>
        )}
      </PaperSection>

      {positions.length > 0 ? (
        <PaperSection title="Quantity">
          <TextInput
            accessibilityLabel="Practice sell quantity"
            keyboardType="number-pad"
            onChangeText={(value) => {
              setQuantity(value);
              setQuantityError(null);
              setResult(null);
            }}
            placeholder="Whole shares"
            placeholderTextColor={Colors.light.mutedText}
            style={styles.quantityInput}
            value={quantity}
          />
          {selectedPosition ? (
            <Text selectable style={styles.holdingHint}>
              Holding {formatQuantity(selectedPosition.quantity)} shares of {selectedPosition.symbol}
            </Text>
          ) : null}
          {quantityError ? <InlineNotice message={quantityError} tone="error" /> : null}
          <ActionButton
            disabled={!selectedPosition || isSubmitting}
            label="Review Practice Sell"
            onPress={reviewTrade}
          />
        </PaperSection>
      ) : null}

      {confirmQuantity && selectedPosition ? (
        <PaperSection title="Confirm">
          <ConfirmationPanel
            confirmLabel="Sell"
            message={`Sell ${confirmQuantity.toLocaleString('en-US')} ${selectedPosition.symbol}. Current holding is ${formatQuantity(
              selectedPosition.quantity,
            )} shares. The backend delayed stored price will be used.`}
            onCancel={() => setConfirmQuantity(null)}
            onConfirm={() => void submitTrade()}
            pending={isSubmitting}
            pendingLabel="Selling..."
            title="Confirm practice sell?"
          />
        </PaperSection>
      ) : null}

      {result ? (
        <PaperSection title="Result">
          <ResultPanel title="Practice sell completed">
            <Text selectable style={styles.resultText}>
              {getTradeResultSummary(result.transaction)}
            </Text>
            <FieldRow label="Cash after" value={formatPaperMoney(result.transaction?.cashBalanceAfter ?? result.account?.cashBalance ?? null)} />
          </ResultPanel>
          <View style={styles.resultActions}>
            <ActionButton label="Back to Practice" onPress={() => router.replace('/paper-trading' as Href)} variant="ghost" />
            <ActionButton label="View Transactions" onPress={() => router.push('/paper-trading/transactions' as Href)} variant="ghost" />
            <ActionButton label="Continue" onPress={continueTrading} />
          </View>
        </PaperSection>
      ) : null}
    </ScrollView>
  );
}

async function reloadPaperTradingReads(credentials: BasicAuthCredentials) {
  await Promise.allSettled([
    paperTradingApi.getAccount(credentials),
    paperTradingApi.getPortfolio(credentials),
    paperTradingApi.getTransactions(credentials, { currentSessionOnly: true, size: 5 }),
  ]);
}

function PositionSelectorRow({
  onPress,
  position,
  selected,
}: {
  onPress: () => void;
  position: PaperPositionResponse;
  selected: boolean;
}) {
  return (
    <Pressable
      accessibilityLabel={`Select ${position.symbol}`}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={({ pressed }) => [
        styles.positionRow,
        selected ? styles.positionRowSelected : undefined,
        pressed ? styles.positionRowPressed : undefined,
      ]}>
      <View style={styles.positionIdentity}>
        <Text selectable numberOfLines={1} style={styles.positionSymbol}>
          {position.symbol}
        </Text>
        <Text selectable numberOfLines={1} style={styles.positionCompany}>
          {position.companyName ?? 'Company unavailable'}
        </Text>
      </View>
      <View style={styles.positionSide}>
        <Text selectable style={styles.positionQuantity}>
          {formatQuantity(position.quantity)} shares
        </Text>
        <Text selectable style={styles.positionValue}>
          {formatPaperMoney(position.valuationMarketValue ?? position.marketValue)}
        </Text>
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
  selectorRows: {
    gap: 0,
  },
  positionRow: {
    alignItems: 'center',
    borderTopColor: Colors.light.border,
    borderTopWidth: 1,
    flexDirection: 'row',
    gap: Spacing.md,
    minHeight: 58,
    paddingVertical: Spacing.sm,
  },
  positionRowSelected: {
    backgroundColor: '#F8FAFC',
  },
  positionRowPressed: {
    opacity: 0.82,
  },
  positionIdentity: {
    flex: 1,
    gap: 2,
    minWidth: 0,
  },
  positionSymbol: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '700',
  },
  positionCompany: {
    color: Colors.light.mutedText,
    fontSize: 12,
  },
  positionSide: {
    alignItems: 'flex-end',
    gap: 2,
    width: 110,
  },
  positionQuantity: {
    color: Colors.light.text,
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '600',
  },
  positionValue: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontVariant: ['tabular-nums'],
  },
  quantityInput: {
    backgroundColor: '#F8FAFC',
    borderColor: Colors.light.border,
    borderRadius: 8,
    borderWidth: 1,
    color: Colors.light.text,
    fontSize: 18,
    minHeight: 48,
    paddingHorizontal: Spacing.md,
  },
  holdingHint: {
    color: Colors.light.mutedText,
    fontSize: 13,
  },
  emptyWrap: {
    gap: Spacing.md,
  },
  resultText: {
    color: Colors.light.text,
    fontSize: 14,
    lineHeight: 20,
  },
  resultActions: {
    gap: Spacing.sm,
  },
});
