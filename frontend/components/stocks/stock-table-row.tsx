import { type Href, useRouter } from 'expo-router';
import { Pressable, StyleSheet, Text, View, type GestureResponderEvent } from 'react-native';

import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import type { StockListItemResponse, WatchlistStockResponse } from '@/types/stocks';
import {
  formatPercent,
  formatPrice,
  getMovementColor,
  getPreferredPercentChange,
  getPreferredPrice,
} from '@/utils/stock-display';

export type StockTableItem = StockListItemResponse | WatchlistStockResponse;
export type StockDetailReturnContext = {
  returnTo: 'stocks' | 'watchlist' | 'search-context' | 'search-tab';
  searchFrom?: string;
  searchSymbol?: string;
};

type WatchlistTableRowProps = {
  detailReturnContext: StockDetailReturnContext;
  rowNumber: number;
  stock: StockTableItem;
};

type StockListTableRowProps = WatchlistTableRowProps & {
  onPaperTradePress: (stock: StockTableItem) => void;
};

type SearchQuoteRowProps = {
  detailReturnContext: StockDetailReturnContext;
  onToggleWatchlist: (stock: StockListItemResponse) => Promise<void>;
  pending: boolean;
  stock: StockListItemResponse;
};

export function WatchlistTableRow({ detailReturnContext, rowNumber, stock }: WatchlistTableRowProps) {
  return <QuoteTableRow detailReturnContext={detailReturnContext} rowNumber={rowNumber} stock={stock} />;
}

export function SearchFallbackTableRow({ detailReturnContext, rowNumber, stock }: WatchlistTableRowProps) {
  return <QuoteTableRow detailReturnContext={detailReturnContext} rowNumber={rowNumber} stock={stock} />;
}

export function StockListTableRow({
  detailReturnContext,
  onPaperTradePress,
  rowNumber,
  stock,
}: StockListTableRowProps) {
  const router = useRouter();
  const percentChange = getPreferredPercentChange(stock);
  const movementColor = getMovementColor(percentChange);

  const openDetail = () => {
    router.push({
      pathname: '/stocks/[symbol]',
      params: buildDetailParams(stock.symbol, detailReturnContext),
    } as Href);
  };

  const handleActionPress = (event: GestureResponderEvent) => {
    event.stopPropagation();
    onPaperTradePress(stock);
  };

  return (
    <Pressable
      accessibilityHint={`Opens delayed market data details for ${stock.symbol}.`}
      accessibilityLabel={`${stock.symbol}, ${stock.companyName}`}
      accessibilityRole="button"
      onPress={openDetail}
      style={({ pressed }) => [styles.row, pressed ? styles.rowPressed : undefined]}>
      <NumberCell rowNumber={rowNumber} />
      <IdentityCell stock={stock} />
      <View style={styles.stockListPriceCell}>
        <Text selectable numberOfLines={1} style={[styles.priceText, { color: movementColor }]}>
          {formatPrice(getPreferredPrice(stock))}
        </Text>
      </View>
      <View style={styles.stockListChangeCell}>
        <Text selectable numberOfLines={1} style={[styles.changeText, { color: movementColor }]}>
          {formatPercent(percentChange)}
        </Text>
      </View>
      <View style={styles.actionCell}>
        <Pressable
          accessibilityHint={`Opens a guarded practice buy ticket for ${stock.symbol}.`}
          accessibilityLabel={`Paper trade ${stock.symbol}`}
          accessibilityRole="button"
          onPress={handleActionPress}
          style={({ pressed }) => [styles.paperButton, pressed ? styles.actionPressed : undefined]}>
          {({ pressed }) => (
            <Text
              numberOfLines={1}
              style={[styles.paperButtonText, pressed ? styles.paperButtonTextPressed : undefined]}>
              Paper Trade
            </Text>
          )}
        </Pressable>
      </View>
    </Pressable>
  );
}

export function SearchQuoteRow({
  detailReturnContext,
  onToggleWatchlist,
  pending,
  stock,
}: SearchQuoteRowProps) {
  const router = useRouter();

  const openDetail = () => {
    router.push({
      pathname: '/stocks/[symbol]',
      params: buildDetailParams(stock.symbol, detailReturnContext),
    } as Href);
  };

  const handleHeartPress = (event: GestureResponderEvent) => {
    event.stopPropagation();
    void onToggleWatchlist(stock);
  };

  return (
    <Pressable
      accessibilityHint={`Opens delayed market data details for ${stock.symbol}.`}
      accessibilityLabel={`${stock.symbol}, ${stock.companyName}`}
      accessibilityRole="button"
      onPress={openDetail}
      style={({ pressed }) => [styles.searchRow, pressed ? styles.rowPressed : undefined]}>
      <View style={styles.searchIdentity}>
        <Text selectable numberOfLines={1} style={styles.searchName}>
          {stock.companyName}
        </Text>
        <Text selectable style={styles.searchSymbol}>
          {stock.symbol}
        </Text>
      </View>
      <Pressable
        accessibilityLabel={stock.isWatchlisted ? 'Remove from watchlist' : 'Add to watchlist'}
        accessibilityRole="button"
        accessibilityState={{ disabled: pending }}
        disabled={pending}
        onPress={handleHeartPress}
        style={styles.heartButton}>
        <IconSymbol
          color={stock.isWatchlisted ? Colors.light.destructive : Colors.light.mutedText}
          name={stock.isWatchlisted ? 'heart.fill' : 'heart'}
          size={25}
        />
      </Pressable>
    </Pressable>
  );
}

function QuoteTableRow({ detailReturnContext, rowNumber, stock }: WatchlistTableRowProps) {
  const router = useRouter();
  const percentChange = getPreferredPercentChange(stock);
  const movementColor = getMovementColor(percentChange);

  const openDetail = () => {
    router.push({
      pathname: '/stocks/[symbol]',
      params: buildDetailParams(stock.symbol, detailReturnContext),
    } as Href);
  };

  return (
    <Pressable
      accessibilityHint={`Opens delayed market data details for ${stock.symbol}.`}
      accessibilityLabel={`${stock.symbol}, ${stock.companyName}`}
      accessibilityRole="button"
      onPress={openDetail}
      style={({ pressed }) => [styles.row, pressed ? styles.rowPressed : undefined]}>
      <NumberCell rowNumber={rowNumber} />
      <IdentityCell stock={stock} />
      <View style={styles.watchlistPriceCell}>
        <Text selectable numberOfLines={1} style={[styles.priceText, { color: movementColor }]}>
          {formatPrice(getPreferredPrice(stock))}
        </Text>
      </View>
      <View style={styles.watchlistChangeCell}>
        <Text selectable numberOfLines={1} style={[styles.changeText, { color: movementColor }]}>
          {formatPercent(percentChange)}
        </Text>
      </View>
    </Pressable>
  );
}

function buildDetailParams(symbol: string, context: StockDetailReturnContext) {
  const params: Record<string, string> = {
    returnTo: context.returnTo,
    symbol,
  };

  if (context.searchFrom) {
    params.searchFrom = context.searchFrom;
  }

  if (context.searchSymbol) {
    params.searchSymbol = context.searchSymbol;
  }

  return params;
}

function NumberCell({ rowNumber }: { rowNumber: number }) {
  return (
    <Text selectable style={styles.numberCell}>
      {rowNumber}
    </Text>
  );
}

function IdentityCell({ stock }: { stock: StockTableItem }) {
  return (
    <View style={styles.identityCell}>
      <Text selectable numberOfLines={1} style={styles.symbol}>
        {stock.symbol}
      </Text>
      <Text selectable numberOfLines={1} style={styles.company}>
        {stock.companyName}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 54,
    paddingHorizontal: Spacing.md,
    paddingVertical: 6,
  },
  rowPressed: {
    backgroundColor: '#F8FAFC',
  },
  numberCell: {
    color: Colors.light.text,
    fontSize: 15,
    fontVariant: ['tabular-nums'],
    fontWeight: '700',
    width: 28,
  },
  identityCell: {
    flex: 1,
    gap: 2,
    minWidth: 0,
  },
  symbol: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '600',
    letterSpacing: 0,
    lineHeight: 18,
  },
  company: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '400',
    lineHeight: 16,
  },
  watchlistPriceCell: {
    alignItems: 'flex-end',
    width: 92,
  },
  watchlistChangeCell: {
    alignItems: 'flex-end',
    width: 72,
  },
  stockListPriceCell: {
    alignItems: 'flex-end',
    width: 80,
  },
  stockListChangeCell: {
    alignItems: 'flex-end',
    width: 64,
  },
  priceText: {
    fontSize: 14,
    fontVariant: ['tabular-nums'],
    fontWeight: '500',
  },
  changeText: {
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '600',
  },
  actionCell: {
    alignItems: 'flex-end',
    width: 94,
  },
  paperButton: {
    alignItems: 'center',
    borderColor: '#94A3B8',
    borderRadius: 999,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: 30,
    paddingHorizontal: Spacing.sm,
    width: 90,
  },
  paperButtonText: {
    color: Colors.light.text,
    fontSize: 11,
    fontWeight: '600',
  },
  actionPressed: {
    backgroundColor: '#052344',
    borderColor: '#052344',
  },
  paperButtonTextPressed: {
    color: '#FFFFFF',
  },
  searchRow: {
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    gap: Spacing.md,
    minHeight: 62,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  searchIdentity: {
    flex: 1,
    gap: 2,
    minWidth: 0,
  },
  searchName: {
    color: Colors.light.text,
    fontSize: 15,
    fontWeight: '500',
    lineHeight: 20,
  },
  searchSymbol: {
    color: '#C2410C',
    fontSize: 13,
    fontWeight: '500',
    lineHeight: 18,
  },
  heartButton: {
    alignItems: 'center',
    height: 44,
    justifyContent: 'center',
    width: 44,
  },
});
