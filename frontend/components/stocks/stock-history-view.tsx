import { StyleSheet, Text, View } from 'react-native';

import { ActionButton } from '@/components/foundation/action-button';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { StockPill } from '@/components/stocks/stock-pills';
import { Colors, Spacing } from '@/constants/theme';
import type { StockHistoryPointResponse, StockHistoryResponse } from '@/types/stocks';
import {
  formatPlainNumber,
  formatPrice,
  formatVolume,
  getHistoryPointLabel,
  toNumber,
} from '@/utils/stock-display';

type StockHistoryViewProps = {
  history: StockHistoryResponse | null;
  loading: boolean;
  errorMessage: string | null;
  onRetry: () => void;
};

export function StockHistoryView({
  errorMessage,
  history,
  loading,
  onRetry,
}: StockHistoryViewProps) {
  if (loading && !history) {
    return (
      <View style={styles.stableShell}>
        <Text selectable style={styles.loadingText}>
          Loading history...
        </Text>
      </View>
    );
  }

  if (errorMessage) {
    return (
      <View style={styles.stack}>
        <ErrorBanner title="History needs attention" message={errorMessage} />
        <ActionButton
          accessibilityHint="Retries the stock history request for the selected timeframe."
          label="Try loading history again"
          onPress={onRetry}
          variant="secondary"
        />
      </View>
    );
  }

  if (!history || history.points.length === 0) {
    return (
      <EmptyState
        title="No history points yet"
        description={history?.message ?? 'The backend returned no stored points for this timeframe.'}
      />
    );
  }

  const summary = summarizeHistory(history.points);
  const recentPoints = history.points.slice(-5).reverse();

  return (
    <View style={styles.container}>
      {loading ? (
        <Text selectable style={styles.inlineLoading}>
          Loading selected timeframe...
        </Text>
      ) : null}
      <View style={styles.headerRow}>
        <StockPill label={history.timeframe} tone="info" />
      </View>

      <View style={styles.summaryGrid}>
        <SummaryMetric label="Returned points" value={`${history.points.length}`} />
        <SummaryMetric label="First close" value={formatPrice(summary.firstClose)} />
        <SummaryMetric label="Latest close" value={formatPrice(summary.latestClose)} />
        <SummaryMetric label="Low close" value={formatPrice(summary.lowClose)} />
        <SummaryMetric label="High close" value={formatPrice(summary.highClose)} />
      </View>

      <View style={styles.pointsStack}>
        <Text selectable style={styles.sectionLabel}>
          Recent returned points
        </Text>
        {recentPoints.map((point, index) => (
          <HistoryPointRow key={`${getHistoryPointLabel(point)}-${index}`} point={point} />
        ))}
      </View>
    </View>
  );
}

function SummaryMetric({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.metric}>
      <Text selectable style={styles.metricLabel}>
        {label}
      </Text>
      <Text selectable style={styles.metricValue}>
        {value}
      </Text>
    </View>
  );
}

function HistoryPointRow({ point }: { point: StockHistoryPointResponse }) {
  return (
    <View style={styles.pointRow}>
      <View style={styles.pointDate}>
        <Text selectable style={styles.pointLabel}>
          {getHistoryPointLabel(point)}
        </Text>
      </View>
      <View style={styles.pointNumbers}>
        <Text selectable style={styles.pointClose}>
          {formatPrice(point.closePrice)}
        </Text>
        <Text selectable style={styles.pointMeta}>
          H {formatPlainNumber(point.highPrice)} / L {formatPlainNumber(point.lowPrice)}
        </Text>
        <Text selectable style={styles.pointMeta}>
          {formatVolume(point.volume)}
        </Text>
      </View>
    </View>
  );
}

function summarizeHistory(points: StockHistoryPointResponse[]) {
  const closes = points
    .map((point) => toNumber(point.closePrice))
    .filter((value): value is number => value !== null);

  if (closes.length === 0) {
    return {
      firstClose: null,
      latestClose: null,
      lowClose: null,
      highClose: null,
    };
  }

  return {
    firstClose: closes[0],
    latestClose: closes[closes.length - 1],
    lowClose: Math.min(...closes),
    highClose: Math.max(...closes),
  };
}

const styles = StyleSheet.create({
  stack: {
    gap: Spacing.md,
  },
  stableShell: {
    justifyContent: 'center',
    minHeight: 360,
    paddingHorizontal: Spacing.md,
  },
  loadingText: {
    color: Colors.light.mutedText,
    fontSize: 13,
    fontWeight: '500',
  },
  container: {
    backgroundColor: Colors.light.surface,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    borderTopColor: Colors.light.border,
    borderTopWidth: 1,
    gap: Spacing.md,
    minHeight: 320,
    padding: Spacing.md,
  },
  inlineLoading: {
    color: Colors.light.mutedText,
    fontSize: 13,
    fontWeight: '700',
  },
  headerRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'flex-end',
  },
  summaryGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  metric: {
    backgroundColor: Colors.light.background,
    borderColor: Colors.light.border,
    borderRadius: 6,
    borderWidth: 1,
    flexGrow: 1,
    gap: Spacing.xs,
    minWidth: 130,
    padding: Spacing.sm,
  },
  metricLabel: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '700',
  },
  metricValue: {
    color: Colors.light.text,
    fontSize: 14,
    fontVariant: ['tabular-nums'],
    fontWeight: '800',
  },
  pointsStack: {
    gap: Spacing.sm,
  },
  sectionLabel: {
    color: Colors.light.text,
    fontSize: 15,
    fontWeight: '700',
  },
  pointRow: {
    backgroundColor: Colors.light.background,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'space-between',
    paddingVertical: Spacing.sm,
  },
  pointDate: {
    flex: 1,
    gap: Spacing.xs,
  },
  pointLabel: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '700',
  },
  pointNumbers: {
    alignItems: 'flex-end',
    gap: Spacing.xs,
  },
  pointClose: {
    color: Colors.light.text,
    fontSize: 14,
    fontVariant: ['tabular-nums'],
    fontWeight: '700',
  },
  pointMeta: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontVariant: ['tabular-nums'],
  },
});
