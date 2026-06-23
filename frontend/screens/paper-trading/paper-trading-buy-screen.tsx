import { useFocusEffect } from '@react-navigation/native';
import { type Href, useRouter } from 'expo-router';
import { useCallback, useMemo, useRef, useState } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { paperTradingApi } from '@/api/paper-trading';
import { stocksApi } from '@/api/stocks';
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
import type { PaperTradeExecutionResponse } from '@/types/paper-trading';
import type { StockListItemResponse } from '@/types/stocks';
import {
  formatPaperMoney,
  getPaperTradingApiErrorMessage,
  getSelectedStockTradeWarning,
  getTradeResultSummary,
  validatePaperTradeQuantity,
} from '@/utils/paper-trading-display';
import { getMovementColor, getPreferredPercentChange, getPreferredPrice, normalizeStockSymbol } from '@/utils/stock-display';

type PaperTradingBuyScreenProps = {
  from?: string;
  returnTo?: string;
  searchFrom?: string;
  searchSymbol?: string;
  symbol?: string;
};

export function PaperTradingBuyScreen({
  from,
  returnTo,
  searchFrom,
  searchSymbol,
  symbol,
}: PaperTradingBuyScreenProps) {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { credentials } = useAuthSession();
  const { showToast } = useToast();
  const requestIdRef = useRef(0);
  const [stocks, setStocks] = useState<StockListItemResponse[]>([]);
  const [selectedSymbol, setSelectedSymbol] = useState(normalizeStockSymbol(symbol));
  const [quantity, setQuantity] = useState('');
  const [quantityError, setQuantityError] = useState<string | null>(null);
  const [confirmQuantity, setConfirmQuantity] = useState<number | null>(null);
  const [result, setResult] = useState<PaperTradeExecutionResponse | null>(null);
  const [isLoadingStocks, setIsLoadingStocks] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadStocks = useCallback(
    async (mode: 'focus' | 'refresh' = 'focus') => {
      const requestId = requestIdRef.current + 1;
      requestIdRef.current = requestId;

      if (!credentials) {
        setErrorMessage('Sign in again to load supported stocks.');
        setIsLoadingStocks(false);
        setIsRefreshing(false);
        return;
      }

      if (mode === 'refresh') {
        setIsRefreshing(true);
      } else {
        setIsLoadingStocks(true);
      }
      setErrorMessage(null);

      try {
        const response = await stocksApi.getStocks(credentials);
        if (requestIdRef.current !== requestId) {
          return;
        }
        setStocks(response.stocks ?? []);
      } catch (error) {
        if (requestIdRef.current !== requestId) {
          return;
        }
        setErrorMessage(getPaperTradingApiErrorMessage(error, 'Supported stocks could not be loaded.'));
      } finally {
        if (requestIdRef.current === requestId) {
          setIsLoadingStocks(false);
          setIsRefreshing(false);
        }
      }
    },
    [credentials],
  );

  useFocusEffect(
    useCallback(() => {
      setSelectedSymbol(normalizeStockSymbol(symbol));
      setResult(null);
      setConfirmQuantity(null);
      setQuantityError(null);
      void loadStocks('focus');
      return undefined;
    }, [loadStocks, symbol]),
  );

  const selectedStock = useMemo(
    () => stocks.find((stock) => stock.symbol === selectedSymbol) ?? null,
    [selectedSymbol, stocks],
  );
  const tradeWarning = selectedStock ? getSelectedStockTradeWarning(selectedStock) : null;

  const handleBack = () => {
    if (from === 'detail' && symbol) {
      router.replace({
        pathname: '/stocks/[symbol]',
        params: buildDetailParams(normalizeStockSymbol(symbol), returnTo, searchFrom, searchSymbol),
      } as Href);
      return;
    }

    if (from === 'stocks') {
      router.replace('/stocks' as Href);
      return;
    }

    router.replace('/paper-trading' as Href);
  };

  const reviewTrade = () => {
    const validation = validatePaperTradeQuantity(quantity);
    if (!selectedSymbol) {
      setQuantityError('Choose a supported stock first.');
      return;
    }
    if (validation.message || validation.quantity === null) {
      setQuantityError(validation.message);
      return;
    }

    setQuantityError(null);
    setConfirmQuantity(validation.quantity);
  };

  const submitTrade = async () => {
    if (!credentials || !confirmQuantity || !selectedSymbol || isSubmitting) {
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await paperTradingApi.buy(credentials, {
        quantity: confirmQuantity,
        symbol: selectedSymbol,
      });
      setResult(response);
      setConfirmQuantity(null);
      showToast(`${selectedSymbol} practice buy completed.`);
      void reloadPaperTradingReads(credentials);
    } catch (error) {
      showToast(getPaperTradingApiErrorMessage(error, 'Practice buy could not be completed.'), 'error');
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
      refreshControl={<RefreshControl onRefresh={() => void loadStocks('refresh')} refreshing={isRefreshing} />}
      style={styles.container}>
      <PaperHeader onBack={handleBack} onRefresh={() => void loadStocks('refresh')} subtitle="No real trade is executed" title="Practice Buy" />

      {errorMessage ? <ErrorBanner title="Buy ticket needs attention" message={errorMessage} /> : null}

      <PaperSection title="Stock">
        {isLoadingStocks ? (
          <SkeletonRows count={3} />
        ) : stocks.length === 0 ? (
          <EmptyState title="No supported stocks" description="Supported stocks are unavailable right now." />
        ) : (
          <View style={styles.selectorRows}>
            {stocks.map((stock) => (
              <StockSelectorRow
                key={stock.symbol}
                onPress={() => {
                  setSelectedSymbol(stock.symbol);
                  setResult(null);
                }}
                selected={stock.symbol === selectedSymbol}
                stock={stock}
              />
            ))}
          </View>
        )}
        {tradeWarning ? <InlineNotice message={tradeWarning} tone="warn" /> : null}
      </PaperSection>

      <PaperSection title="Quantity">
        <TextInput
          accessibilityLabel="Practice buy quantity"
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
        {quantityError ? <InlineNotice message={quantityError} tone="error" /> : null}
        <ActionButton
          disabled={!selectedSymbol || isSubmitting}
          label="Review Practice Buy"
          onPress={reviewTrade}
        />
      </PaperSection>

      {confirmQuantity ? (
        <PaperSection title="Confirm">
          <ConfirmationPanel
            confirmLabel="Buy"
            message={`Buy ${confirmQuantity.toLocaleString('en-US')} ${selectedSymbol}. The backend delayed stored price will be used.`}
            onCancel={() => setConfirmQuantity(null)}
            onConfirm={() => void submitTrade()}
            pending={isSubmitting}
            pendingLabel="Buying..."
            title="Confirm practice buy?"
          />
        </PaperSection>
      ) : null}

      {result ? (
        <PaperSection title="Result">
          <ResultPanel title="Practice buy completed">
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

function StockSelectorRow({
  onPress,
  selected,
  stock,
}: {
  onPress: () => void;
  selected: boolean;
  stock: StockListItemResponse;
}) {
  const percentChange = getPreferredPercentChange(stock);
  const movementColor = getMovementColor(percentChange);

  return (
    <Pressable
      accessibilityLabel={`Select ${stock.symbol}`}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={({ pressed }) => [
        styles.stockRow,
        selected ? styles.stockRowSelected : undefined,
        pressed ? styles.stockRowPressed : undefined,
      ]}>
      <View style={styles.stockIdentity}>
        <Text selectable numberOfLines={1} style={styles.stockSymbol}>
          {stock.symbol}
        </Text>
        <Text selectable numberOfLines={1} style={styles.stockCompany}>
          {stock.companyName}
        </Text>
      </View>
      <View style={styles.stockQuote}>
        <Text selectable numberOfLines={1} style={[styles.stockPrice, { color: movementColor }]}>
          {formatPaperMoney(getPreferredPrice(stock))}
        </Text>
      </View>
    </Pressable>
  );
}

function buildDetailParams(
  symbol: string,
  returnTo: string | undefined,
  searchFrom: string | undefined,
  searchSymbol: string | undefined,
) {
  const params: Record<string, string> = { symbol };
  if (returnTo) {
    params.returnTo = returnTo;
  }
  if (searchFrom) {
    params.searchFrom = searchFrom;
  }
  if (searchSymbol) {
    params.searchSymbol = searchSymbol;
  }
  return params;
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
  stockRow: {
    alignItems: 'center',
    borderTopColor: Colors.light.border,
    borderTopWidth: 1,
    flexDirection: 'row',
    gap: Spacing.md,
    minHeight: 56,
    paddingVertical: Spacing.sm,
  },
  stockRowSelected: {
    backgroundColor: '#F8FAFC',
  },
  stockRowPressed: {
    opacity: 0.82,
  },
  stockIdentity: {
    flex: 1,
    gap: 2,
    minWidth: 0,
  },
  stockSymbol: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '700',
  },
  stockCompany: {
    color: Colors.light.mutedText,
    fontSize: 12,
  },
  stockQuote: {
    alignItems: 'flex-end',
    width: 96,
  },
  stockPrice: {
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '600',
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
  resultText: {
    color: Colors.light.text,
    fontSize: 14,
    lineHeight: 20,
  },
  resultActions: {
    gap: Spacing.sm,
  },
});
