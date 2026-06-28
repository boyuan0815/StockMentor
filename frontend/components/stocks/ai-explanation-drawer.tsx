import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import { stocksApi } from '@/api/stocks';
import { ActionButton } from '@/components/foundation/action-button';
import { AiExplanationCard } from '@/components/stocks/ai-explanation-card';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import type { BasicAuthCredentials } from '@/types/auth';
import type {
  StockExplanationResponse,
  StockExplanationTimeframe,
  StockTimeframe,
} from '@/types/stocks';
import { getStockApiErrorMessage, isAiExplanationTimeframe } from '@/utils/stock-display';

type AiExplanationDrawerProps = {
  credentials: BasicAuthCredentials | null;
  onContentReady?: () => void;
  onOpen?: () => void;
  symbol: string;
  timeframe: StockTimeframe;
};

export function AiExplanationDrawer({
  credentials,
  onContentReady,
  onOpen,
  symbol,
  timeframe,
}: AiExplanationDrawerProps) {
  const [open, setOpen] = useState(false);
  const [loadedSymbol, setLoadedSymbol] = useState<string | null>(null);
  const [loadedTimeframe, setLoadedTimeframe] = useState<StockExplanationTimeframe | null>(null);
  const [explanation, setExplanation] = useState<StockExplanationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const requestIdRef = useRef(0);
  const supported = isAiExplanationTimeframe(timeframe);
  const currentExplanation =
    loadedSymbol === symbol && loadedTimeframe === timeframe ? explanation : null;

  const loadExplanation = useCallback(
    async (requestedTimeframe: StockExplanationTimeframe) => {
      const requestSymbol = symbol;
      const requestId = requestIdRef.current + 1;
      requestIdRef.current = requestId;

      if (!credentials) {
        setLoading(false);
        setErrorMessage('Sign in again to load this learning explanation.');
        return;
      }

      setLoading(true);
      setErrorMessage(null);

      try {
        const response = await stocksApi.getStockExplanation(
          credentials,
          requestSymbol,
          requestedTimeframe,
        );
        if (requestIdRef.current !== requestId) {
          return;
        }
        setExplanation(response);
        setLoadedSymbol(requestSymbol);
        setLoadedTimeframe(requestedTimeframe);
      } catch (error) {
        if (requestIdRef.current !== requestId) {
          return;
        }
        setErrorMessage(getStockApiErrorMessage(error, 'AI explanation could not be loaded.'));
      } finally {
        if (requestIdRef.current === requestId) {
          setLoading(false);
        }
      }
    },
    [credentials, symbol],
  );

  useEffect(() => {
    requestIdRef.current += 1;
    setOpen(false);
    setExplanation(null);
    setErrorMessage(null);
    setLoadedSymbol(null);
    setLoadedTimeframe(null);
    setLoading(false);
  }, [symbol, timeframe]);

  useEffect(() => {
    if (!open || !supported) {
      return;
    }

    if (loadedSymbol === symbol && loadedTimeframe === timeframe && currentExplanation) {
      return;
    }

    void loadExplanation(timeframe);
  }, [
    currentExplanation,
    loadedSymbol,
    loadedTimeframe,
    loadExplanation,
    open,
    supported,
    symbol,
    timeframe,
  ]);

  useEffect(() => {
    if (open) {
      onContentReady?.();
    }
  }, [currentExplanation, errorMessage, loading, onContentReady, open, supported]);

  const handleToggle = () => {
    setOpen((current) => {
      const next = !current;
      if (next) {
        onOpen?.();
      }
      return next;
    });
  };

  return (
    <View style={styles.container}>
      <Pressable
        accessibilityHint="Opens or closes the educational AI explanation for the selected timeframe."
        accessibilityLabel="AI stock explanation"
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        onPress={handleToggle}
        style={({ pressed }) => [styles.header, pressed ? styles.headerPressed : undefined]}>
        <View style={styles.headerCopy}>
          <Text selectable style={styles.title}>
            {open ? 'Close AI Stock Explanation' : 'View AI Stock Explanation'}
          </Text>
        </View>
        <IconSymbol
          color={Colors.light.text}
          name={open ? 'chevron.up' : 'chevron.down'}
          size={22}
        />
      </Pressable>

      {open ? (
        <View style={styles.body}>
          {supported ? (
            loading && !currentExplanation ? (
              <View style={styles.loadingRow}>
                <ActivityIndicator color={Colors.light.text} size="small" />
                <Text selectable style={styles.loadingText}>
                  Generating AI explanation...
                </Text>
              </View>
            ) : errorMessage ? (
              <View style={styles.errorBox}>
                <Text selectable style={styles.errorTitle}>
                  AI explanation failed.
                </Text>
                <Text selectable style={styles.errorMessage}>
                  {errorMessage}
                </Text>
                <ActionButton
                  accessibilityHint="Retries the same AI explanation request after the failed load."
                  label="Try again"
                  onPress={() => void loadExplanation(timeframe)}
                  variant="secondary"
                />
              </View>
            ) : currentExplanation ? (
              <AiExplanationCard explanation={currentExplanation} />
            ) : (
              <Text selectable style={styles.mutedText}>
                Tap to generate the selected timeframe.
              </Text>
            )
          ) : (
            <Text selectable style={styles.mutedText}>
              AI explanation is available for 1D, 5D, 1M, and 3M only.
            </Text>
          )}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: Colors.light.surface,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    borderTopColor: Colors.light.border,
    borderTopWidth: 1,
    overflow: 'hidden',
  },
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'space-between',
    minHeight: 50,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  headerPressed: {
    backgroundColor: '#F1F5F9',
  },
  headerCopy: {
    flex: 1,
    gap: Spacing.xs,
  },
  title: {
    color: Colors.light.text,
    fontSize: 16,
    fontWeight: '700',
  },
  body: {
    borderTopColor: Colors.light.border,
    borderTopWidth: 1,
    paddingBottom: Spacing.xxl + Spacing.xl,
    paddingHorizontal: Spacing.sm,
    paddingTop: Spacing.sm,
  },
  loadingRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 42,
  },
  loadingText: {
    color: Colors.light.mutedText,
    fontSize: 13,
    fontWeight: '500',
  },
  errorBox: {
    gap: Spacing.sm,
  },
  errorTitle: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '600',
  },
  errorMessage: {
    color: Colors.light.mutedText,
    fontSize: 13,
    lineHeight: 18,
  },
  mutedText: {
    color: Colors.light.mutedText,
    fontSize: 13,
    lineHeight: 18,
  },
});
