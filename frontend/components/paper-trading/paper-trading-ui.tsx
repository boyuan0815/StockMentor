import { type Href, useRouter } from 'expo-router';
import type { ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View, type GestureResponderEvent } from 'react-native';

import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import type { PaperPositionResponse, PaperTradeTransactionResponse } from '@/types/paper-trading';
import type { ApiNumber } from '@/types/stocks';
import {
  formatPaperDateTime,
  formatPaperMoney,
  formatQuantity,
  getTransactionDisplayTitle,
  getTransactionSideLabel,
  isResetTransaction,
} from '@/utils/paper-trading-display';
import { getMovementColor } from '@/utils/stock-display';

type PaperHeaderProps = {
  onBack?: () => void;
  onRefresh?: () => void;
  subtitle?: string;
  title: string;
};

type MetricProps = {
  label: string;
  toneValue?: ApiNumber;
  value: string;
};

type PositionRowProps = {
  onOpenSell: (position: PaperPositionResponse) => void;
  position: PaperPositionResponse;
};

type TransactionRowProps = {
  onPress?: (transaction: PaperTradeTransactionResponse) => void;
  transaction: PaperTradeTransactionResponse;
};

export function PaperHeader({ onBack, onRefresh, subtitle, title }: PaperHeaderProps) {
  return (
    <View style={styles.header}>
      {onBack ? (
        <Pressable
          accessibilityLabel="Back"
          accessibilityRole="button"
          onPress={onBack}
          style={({ pressed }) => [styles.headerIcon, pressed ? styles.pressed : undefined]}>
          <IconSymbol color={Colors.light.text} name="chevron.left" size={24} />
        </Pressable>
      ) : null}
      <View style={styles.headerCopy}>
        <Text selectable style={styles.headerTitle}>
          {title}
        </Text>
        {subtitle ? (
          <Text selectable numberOfLines={1} style={styles.headerSubtitle}>
            {subtitle}
          </Text>
        ) : null}
      </View>
      {onRefresh ? (
        <Pressable
          accessibilityLabel="Refresh practice data"
          accessibilityRole="button"
          onPress={onRefresh}
          style={({ pressed }) => [styles.headerIcon, pressed ? styles.pressed : undefined]}>
          <IconSymbol color={Colors.light.text} name="arrow.clockwise" size={21} />
        </Pressable>
      ) : null}
    </View>
  );
}

export function PaperSection({
  action,
  children,
  title,
}: {
  action?: ReactNode;
  children: ReactNode;
  title: string;
}) {
  return (
    <View style={styles.section}>
      <View style={styles.sectionHeader}>
        <Text selectable style={styles.sectionTitle}>
          {title}
        </Text>
        {action}
      </View>
      {children}
    </View>
  );
}

export function PaperMetric({ label, toneValue, value }: MetricProps) {
  const color = toneValue === undefined ? Colors.light.text : getMovementColor(toneValue);

  return (
    <View style={styles.metric}>
      <Text selectable style={styles.metricLabel}>
        {label}
      </Text>
      <Text selectable adjustsFontSizeToFit minimumFontScale={0.82} numberOfLines={1} style={[styles.metricValue, { color }]}>
        {value}
      </Text>
    </View>
  );
}

export function InlineNotice({ message, tone = 'info' }: { message: string; tone?: 'info' | 'warn' | 'error' }) {
  return (
    <View style={[styles.notice, tone === 'error' ? styles.errorNotice : tone === 'warn' ? styles.warnNotice : undefined]}>
      <Text selectable style={styles.noticeText}>
        {message}
      </Text>
    </View>
  );
}

export function PositionRow({ onOpenSell, position }: PositionRowProps) {
  const handleSell = (event: GestureResponderEvent) => {
    event.stopPropagation();
    onOpenSell(position);
  };

  return (
    <View style={styles.positionRow}>
      <View style={styles.positionIdentity}>
        <Text selectable numberOfLines={1} style={styles.symbol}>
          {position.symbol}
        </Text>
        <Text selectable numberOfLines={1} style={styles.company}>
          {position.companyName ?? 'Company unavailable'}
        </Text>
      </View>
      <View style={styles.positionNumbers}>
        <Text selectable numberOfLines={1} style={styles.positionPrimary}>
          {formatPaperMoney(position.valuationMarketValue ?? position.marketValue)}
        </Text>
        <Text selectable numberOfLines={1} style={styles.positionSecondary}>
          {formatQuantity(position.quantity)} shares
        </Text>
      </View>
      <View style={styles.positionNumbers}>
        <Text selectable numberOfLines={1} style={styles.positionPrimary}>
          {formatPaperMoney(position.valuationPrice ?? position.currentPrice)}
        </Text>
        <Text selectable numberOfLines={1} style={styles.positionSecondary}>
          Cost {formatPaperMoney(position.averageCost)}
        </Text>
      </View>
      <Pressable
        accessibilityLabel={`Sell ${position.symbol}`}
        accessibilityRole="button"
        onPress={handleSell}
        style={({ pressed }) => [styles.sellButton, pressed ? styles.sellButtonPressed : undefined]}>
        {({ pressed }) => (
          <Text style={[styles.sellButtonText, pressed ? styles.sellButtonTextPressed : undefined]}>
            Sell
          </Text>
        )}
      </Pressable>
    </View>
  );
}

export function TransactionRow({ onPress, transaction }: TransactionRowProps) {
  const router = useRouter();
  const canOpen = Boolean(transaction.transactionId);
  const side = getTransactionSideLabel(transaction.side);
  const reset = isResetTransaction(transaction);

  const openTransaction = () => {
    if (onPress) {
      onPress(transaction);
      return;
    }
    if (!transaction.transactionId) {
      return;
    }
    router.push({
      pathname: '/paper-trading/transactions/[transactionId]',
      params: { transactionId: String(transaction.transactionId) },
    } as Href);
  };

  return (
    <Pressable
      accessibilityLabel={getTransactionDisplayTitle(transaction)}
      accessibilityRole={canOpen ? 'button' : 'text'}
      disabled={!canOpen}
      onPress={openTransaction}
      style={({ pressed }) => [styles.transactionRow, pressed && canOpen ? styles.pressedRow : undefined]}>
      <View style={styles.transactionSide}>
        <Text selectable style={[styles.transactionSideText, reset ? styles.resetText : transaction.side === 'SELL' ? styles.sellText : styles.buyText]}>
          {side}
        </Text>
        <Text selectable style={styles.transactionMeta}>
          {transaction.isCurrentSession === false ? 'Old session' : 'Current'}
        </Text>
      </View>
      <View style={styles.transactionIdentity}>
        <Text selectable numberOfLines={1} style={styles.symbol}>
          {reset ? 'Session reset' : transaction.symbol ?? 'Unavailable'}
        </Text>
        <Text selectable numberOfLines={1} style={styles.company}>
          {reset
            ? `Session ${transaction.sessionNumber ?? 'Unavailable'}`
            : `${formatQuantity(transaction.quantity)} shares`}
        </Text>
      </View>
      <View style={styles.transactionAmount}>
        <Text selectable numberOfLines={1} style={styles.transactionPrimary}>
          {reset ? 'Reset' : formatPaperMoney(transaction.netAmount ?? transaction.totalAmount)}
        </Text>
        <Text selectable numberOfLines={1} style={styles.transactionMeta}>
          {formatPaperDateTime(transaction.executedAt ?? transaction.transactionTime)}
        </Text>
      </View>
      {canOpen ? <IconSymbol color={Colors.light.mutedText} name="chevron.right" size={18} /> : null}
    </Pressable>
  );
}

export function ResultPanel({
  children,
  title,
}: {
  children: ReactNode;
  title: string;
}) {
  return (
    <View style={styles.resultPanel}>
      <IconSymbol color="#052344" name="checkmark.circle.fill" size={24} />
      <View style={styles.resultCopy}>
        <Text selectable style={styles.resultTitle}>
          {title}
        </Text>
        {children}
      </View>
    </View>
  );
}

export function FieldRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.fieldRow}>
      <Text selectable style={styles.fieldLabel}>
        {label}
      </Text>
      <Text selectable style={styles.fieldValue}>
        {value}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 48,
    paddingHorizontal: Spacing.md,
  },
  headerIcon: {
    alignItems: 'center',
    height: 44,
    justifyContent: 'center',
    width: 38,
  },
  headerCopy: {
    flex: 1,
    minWidth: 0,
  },
  headerTitle: {
    color: Colors.light.text,
    fontSize: 22,
    fontWeight: '700',
    lineHeight: 28,
  },
  headerSubtitle: {
    color: Colors.light.mutedText,
    fontSize: 12,
    lineHeight: 16,
  },
  section: {
    backgroundColor: Colors.light.surface,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    borderTopColor: Colors.light.border,
    borderTopWidth: 1,
    gap: Spacing.md,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
  },
  sectionHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  sectionTitle: {
    color: Colors.light.text,
    fontSize: 16,
    fontWeight: '700',
  },
  metric: {
    flex: 1,
    gap: Spacing.xs,
    minWidth: 120,
  },
  metricLabel: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '500',
  },
  metricValue: {
    color: Colors.light.text,
    fontSize: 20,
    fontVariant: ['tabular-nums'],
    fontWeight: '700',
  },
  notice: {
    backgroundColor: '#EFF6FF',
    borderColor: '#BFDBFE',
    borderRadius: 8,
    borderWidth: 1,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  warnNotice: {
    backgroundColor: '#FFF7ED',
    borderColor: '#FED7AA',
  },
  errorNotice: {
    backgroundColor: '#FEF2F2',
    borderColor: '#FECACA',
  },
  noticeText: {
    color: Colors.light.text,
    fontSize: 13,
    lineHeight: 18,
  },
  positionRow: {
    alignItems: 'center',
    borderTopColor: Colors.light.border,
    borderTopWidth: 1,
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 62,
    paddingVertical: Spacing.sm,
  },
  positionIdentity: {
    flex: 1,
    gap: 2,
    minWidth: 0,
  },
  symbol: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '600',
    lineHeight: 18,
  },
  company: {
    color: Colors.light.mutedText,
    fontSize: 12,
    lineHeight: 16,
  },
  positionNumbers: {
    alignItems: 'flex-end',
    gap: 2,
    width: 92,
  },
  positionPrimary: {
    color: Colors.light.text,
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '600',
  },
  positionSecondary: {
    color: Colors.light.mutedText,
    fontSize: 11,
    fontVariant: ['tabular-nums'],
  },
  sellButton: {
    alignItems: 'center',
    borderColor: '#94A3B8',
    borderRadius: 999,
    borderWidth: 1,
    minHeight: 30,
    justifyContent: 'center',
    width: 52,
  },
  sellButtonPressed: {
    backgroundColor: '#052344',
    borderColor: '#052344',
  },
  sellButtonText: {
    color: Colors.light.text,
    fontSize: 12,
    fontWeight: '600',
  },
  sellButtonTextPressed: {
    color: '#FFFFFF',
  },
  transactionRow: {
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 62,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  transactionSide: {
    gap: 2,
    width: 72,
  },
  transactionSideText: {
    fontSize: 14,
    fontWeight: '700',
  },
  buyText: {
    color: Colors.light.success,
  },
  sellText: {
    color: Colors.light.destructive,
  },
  resetText: {
    color: '#052344',
  },
  transactionMeta: {
    color: Colors.light.mutedText,
    fontSize: 11,
    lineHeight: 15,
  },
  transactionIdentity: {
    flex: 1,
    gap: 2,
    minWidth: 0,
  },
  transactionAmount: {
    alignItems: 'flex-end',
    gap: 2,
    width: 96,
  },
  transactionPrimary: {
    color: Colors.light.text,
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '600',
  },
  pressed: {
    opacity: 0.82,
  },
  pressedRow: {
    backgroundColor: '#F8FAFC',
  },
  resultPanel: {
    alignItems: 'flex-start',
    backgroundColor: '#F8FAFC',
    borderColor: Colors.light.border,
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: 'row',
    gap: Spacing.sm,
    padding: Spacing.md,
  },
  resultCopy: {
    flex: 1,
    gap: Spacing.xs,
  },
  resultTitle: {
    color: Colors.light.text,
    fontSize: 15,
    fontWeight: '700',
  },
  fieldRow: {
    alignItems: 'center',
    borderTopColor: Colors.light.border,
    borderTopWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  fieldLabel: {
    color: Colors.light.mutedText,
    fontSize: 13,
  },
  fieldValue: {
    color: Colors.light.text,
    flex: 1,
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '600',
    textAlign: 'right',
  },
});
