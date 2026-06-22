import { StyleSheet, Text, View } from 'react-native';

import { StockPill } from '@/components/stocks/stock-pills';
import { Colors, Radius, Spacing } from '@/constants/theme';
import type { ApiNumber, DelayedStockFields } from '@/types/stocks';
import {
  formatBackendDateTime,
  formatPercent,
  formatPrice,
  getMovementColor,
  getPreferredPercentChange,
  getPreferredPrice,
  getPriceAvailabilityCopy,
  getStockSourceLabel,
  labelize,
} from '@/utils/stock-display';

type PriceSummaryStock = DelayedStockFields & {
  currentPrice?: ApiNumber;
  percentChange?: ApiNumber;
  lastUpdated?: string | null;
  source?: string | null;
  lastBackendUpdatedAt?: string | null;
};

type StockPriceSummaryProps = {
  stock: PriceSummaryStock;
  compact?: boolean;
};

export function StockPriceSummary({ compact = false, stock }: StockPriceSummaryProps) {
  const price = getPreferredPrice(stock);
  const percentChange = getPreferredPercentChange(stock);
  const displayedTime = stock.displayedMarketTime ?? stock.lastUpdated ?? null;

  return (
    <View style={[styles.container, compact ? styles.compactContainer : undefined]}>
      <View style={styles.priceRow}>
        <View style={styles.priceStack}>
          <Text selectable style={[styles.price, compact ? styles.compactPrice : undefined]}>
            {formatPrice(price)}
          </Text>
          <Text selectable style={[styles.change, { color: getMovementColor(percentChange) }]}>
            {formatPercent(percentChange)}
          </Text>
        </View>
        <StockPill
          label={stock.priceFreshnessStatus ?? (stock.displayedPrice === null ? 'Compatibility price' : 'Delayed')}
          tone={stock.isPriceAvailable === false ? 'warning' : 'info'}
        />
      </View>

      <Text selectable style={styles.note}>
        {stock.dataNote ?? getPriceAvailabilityCopy(stock)}
      </Text>

      {compact ? null : (
        <View style={styles.metadataGrid}>
          <Metadata label="Displayed market time" value={formatBackendDateTime(displayedTime)} />
          <Metadata label="Target display time" value={formatBackendDateTime(stock.targetDisplayMarketTime)} />
          <Metadata label="Source" value={getStockSourceLabel(stock)} />
          <Metadata label="Backend update" value={formatBackendDateTime(stock.lastBackendUpdatedAt)} />
          <Metadata label="Market time zone" value={stock.marketTimeZone ?? 'Backend default'} />
          <Metadata label="Data delay" value={stock.dataDelayMinutes === null || stock.dataDelayMinutes === undefined ? 'Unavailable' : `${stock.dataDelayMinutes} minutes`} />
        </View>
      )}
    </View>
  );
}

function Metadata({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.metadataItem}>
      <Text selectable style={styles.metadataLabel}>
        {label}
      </Text>
      <Text selectable style={styles.metadataValue}>
        {labelize(value)}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.md,
    padding: Spacing.lg,
  },
  compactContainer: {
    borderWidth: 0,
    padding: 0,
  },
  priceRow: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'space-between',
  },
  priceStack: {
    flex: 1,
    gap: Spacing.xs,
  },
  price: {
    color: Colors.light.text,
    fontSize: 30,
    fontVariant: ['tabular-nums'],
    fontWeight: '900',
    lineHeight: 36,
  },
  compactPrice: {
    fontSize: 23,
    lineHeight: 29,
  },
  change: {
    fontSize: 15,
    fontVariant: ['tabular-nums'],
    fontWeight: '800',
  },
  note: {
    color: Colors.light.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
  metadataGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  metadataItem: {
    backgroundColor: Colors.light.background,
    borderColor: Colors.light.border,
    borderRadius: Radius.sm,
    borderWidth: 1,
    flexGrow: 1,
    flexShrink: 1,
    gap: Spacing.xs,
    minWidth: 150,
    padding: Spacing.md,
  },
  metadataLabel: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '700',
  },
  metadataValue: {
    color: Colors.light.text,
    fontSize: 14,
    lineHeight: 20,
  },
});
