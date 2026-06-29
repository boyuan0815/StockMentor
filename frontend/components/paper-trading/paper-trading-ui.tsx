import { type Href, useRouter } from 'expo-router';
import { Image } from 'expo-image';
import type { ReactNode } from 'react';
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  type GestureResponderEvent,
} from 'react-native';

import { AnimatedValueText } from '@/components/foundation/animated-value-text';
import { ActionButton } from '@/components/foundation/action-button';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import type { PaperPositionResponse, PaperTradeTransactionResponse } from '@/types/paper-trading';
import type { ApiNumber } from '@/types/stocks';
import {
  formatPaperMoney,
  formatPaperPercent,
  formatQuantity,
  formatSignedPaperMoney,
  getTransactionDisplayTitle,
  getTransactionSideLabel,
  isResetTransaction,
} from '@/utils/paper-trading-display';
import { getMovementColor } from '@/utils/stock-display';

const BRAND_NAVY = '#052344';

type PaperHeaderProps = {
  brandIcon?: boolean;
  iconName?: Parameters<typeof IconSymbol>[0]['name'];
  onBack?: () => void;
  onRefresh?: () => void;
  refreshAccessibilityLabel?: string;
  refreshDisabled?: boolean;
  title: string;
};

type MetricProps = {
  label: string;
  prominent?: boolean;
  toneValue?: ApiNumber;
  value: string;
};

type TransactionRowProps = {
  companyName?: string | null;
  hideCurrentSessionMeta?: boolean;
  onPress?: (transaction: PaperTradeTransactionResponse) => void;
  transaction: PaperTradeTransactionResponse;
};

export function PaperHeader({
  brandIcon = false,
  iconName,
  onBack,
  onRefresh,
  refreshAccessibilityLabel = 'Refresh data',
  refreshDisabled = false,
  title,
}: PaperHeaderProps) {
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
      {brandIcon ? (
        <Image
          accessibilityLabel="StockMentor"
          contentFit="contain"
          source={require('../../assets/images/stockmentor-icon-transparent-1024.png')}
          style={styles.brandLogo}
        />
      ) : iconName ? (
        <IconSymbol color={Colors.light.text} name={iconName} size={24} />
      ) : null}
      <Text selectable numberOfLines={1} style={styles.headerTitle}>
        {title}
      </Text>
      {onRefresh ? (
        <Pressable
          accessibilityRole="button"
          disabled={refreshDisabled}
          onPress={onRefresh}
          accessibilityLabel={refreshAccessibilityLabel}
          style={({ pressed }) => [
            styles.headerIcon,
            pressed && !refreshDisabled ? styles.pressed : undefined,
            refreshDisabled ? styles.disabled : undefined,
          ]}>
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
  title?: string;
}) {
  return (
    <View style={styles.section}>
      {title || action ? (
        <View style={styles.sectionHeader}>
          {title ? (
            <Text selectable style={styles.sectionTitle}>
              {title}
            </Text>
          ) : (
            <View />
          )}
          {action}
        </View>
      ) : null}
      {children}
    </View>
  );
}

export function PaperMetric({ label, prominent = false, toneValue, value }: MetricProps) {
  const color = toneValue === undefined ? Colors.light.text : getMovementColor(toneValue);

  return (
    <View style={styles.metric}>
      <Text selectable style={[styles.metricLabel, prominent ? styles.metricLabelProminent : undefined]}>
        {label}
      </Text>
      <AnimatedValueText
        adjustsFontSizeToFit
        minimumFontScale={0.82}
        numberOfLines={1}
        selectable
        style={[styles.metricValue, prominent ? styles.metricValueProminent : undefined, { color }]}
        value={value}
      />
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

export function PortfolioTabs({
  activeTab,
  onSelect,
}: {
  activeTab: 'assets' | 'history';
  onSelect: (tab: 'assets' | 'history') => void;
}) {
  return (
    <View style={styles.tabs}>
      {(['assets', 'history'] as const).map((tab) => {
        const active = activeTab === tab;
        return (
          <Pressable
            accessibilityLabel={`${tab === 'assets' ? 'Assets' : 'History'} tab`}
            accessibilityRole="button"
            accessibilityState={{ selected: active }}
            key={tab}
            onPress={() => onSelect(tab)}
            style={[styles.tab, active ? styles.tabActive : undefined]}>
            <Text style={[styles.tabText, active ? styles.tabTextActive : undefined]}>
              {tab === 'assets' ? 'Assets' : 'History'}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export function PositionsTable({
  onOpenSell,
  positions,
}: {
  onOpenSell: (position: PaperPositionResponse) => void;
  positions: PaperPositionResponse[];
}) {
  return (
    <View style={styles.positionsTable}>
      <View style={styles.positionsTableRow}>
        <View style={styles.fixedColumn}>
          <View style={[styles.fixedSymbolCell, styles.positionsHeaderCell]}>
            <Text style={styles.tableHeaderText}>Stock</Text>
          </View>
          {positions.map((position) => (
            <View key={`fixed-${position.positionId ?? position.symbol}`} style={styles.fixedSymbolRow}>
              <Text selectable numberOfLines={1} style={styles.symbol}>
                {position.symbol}
              </Text>
              <Text selectable numberOfLines={1} style={styles.company}>
                {position.companyName ?? 'Company unavailable'}
              </Text>
            </View>
          ))}
        </View>
        <ScrollView horizontal showsHorizontalScrollIndicator={false}>
          <View>
            <View style={styles.metricHeaderRow}>
              <MetricHeader title="Latest Value/QTY" width={116} />
              <MetricHeader title="Current Price/Avg Cost" width={122} />
              <MetricHeader title="P/L" width={96} />
              <MetricHeader title="% Position" width={112} />
              <MetricHeader align="center" title="Action" width={100} />
            </View>
            {positions.map((position) => (
              <View key={position.positionId ?? position.symbol} style={styles.metricDataRow}>
                <MetricPair
                  primary={formatPaperMoney(position.valuationMarketValue ?? position.marketValue)}
                  secondary={formatQuantity(position.quantity)}
                  width={116}
                />
                <MetricPair
                  primary={formatPaperMoney(position.valuationPrice ?? position.currentPrice)}
                  secondary={formatPaperMoney(position.averageCost)}
                  width={122}
                />
                <MetricPair
                  primary={formatSignedPaperMoney(position.unrealizedProfitLoss)}
                  primaryTone={position.unrealizedProfitLoss}
                  secondary={formatPaperPercent(position.unrealizedProfitLossPercent)}
                  secondaryTone={position.unrealizedProfitLossPercent}
                  width={96}
                />
                <MetricPair
                  primary={formatPaperPercent(position.portfolioWeightPercent)}
                  width={112}
                />
                <View style={[styles.metricCell, styles.actionColumn]}>
                  <Pressable
                    accessibilityLabel={`Sell ${position.symbol}`}
                    accessibilityRole="button"
                    onPress={(event: GestureResponderEvent) => {
                      event.stopPropagation();
                      onOpenSell(position);
                    }}
                    style={({ pressed }) => [styles.sellButton, pressed ? styles.sellButtonPressed : undefined]}>
                    {({ pressed }) => (
                      <Text style={[styles.sellButtonText, pressed ? styles.sellButtonTextPressed : undefined]}>
                        Sell
                      </Text>
                    )}
                  </Pressable>
                </View>
              </View>
            ))}
          </View>
        </ScrollView>
      </View>
    </View>
  );
}

function MetricHeader({
  align = 'right',
  title,
  width,
}: {
  align?: 'center' | 'right';
  title: string;
  width: number;
}) {
  const alignItems = align === 'center' ? 'center' : 'flex-end';
  const textAlign = align === 'center' ? 'center' : 'right';

  return (
    <View
      style={[
        styles.metricCell,
        styles.positionsHeaderCell,
        {
          width,
          alignItems,
        },
      ]}>
      <Text
        numberOfLines={2}
        style={[
          styles.tableHeaderText,
          {
            width: '100%',
            textAlign,
          },
        ]}>
        {title}
      </Text>
    </View>
  );
}

function MetricPair({
  primary,
  primaryTone,
  secondary,
  secondaryTone,
  width,
}: {
  primary: string;
  primaryTone?: ApiNumber;
  secondary?: string;
  secondaryTone?: ApiNumber;
  width: number;
}) {
  return (
    <View style={[styles.metricCell, { width }]}>
      <AnimatedValueText
        numberOfLines={1}
        selectable
        style={[
          styles.positionPrimary,
          primaryTone === undefined ? undefined : { color: getMovementColor(primaryTone) },
        ]}
        value={primary}
      />
      {secondary ? (
        <AnimatedValueText
          numberOfLines={1}
          selectable
          style={[
            styles.positionSecondary,
            secondaryTone === undefined ? undefined : { color: getMovementColor(secondaryTone) },
          ]}
          value={secondary}
        />
      ) : null}
    </View>
  );
}

export function TransactionTableHeader() {
  return (
    <View style={styles.transactionHeaderRow}>
      <Text style={[styles.transactionHeaderText, styles.transactionHeaderStatus]}>Action</Text>
      <Text style={[styles.transactionHeaderText, styles.transactionHeaderSymbol]}>Stock</Text>
      <Text style={[styles.transactionHeaderText, styles.transactionHeaderQuantity]}>Price/Qty</Text>
      <Text style={[styles.transactionHeaderText, styles.transactionHeaderProfitLoss]}>P/L</Text>
    </View>
  );
}

export function TransactionRow({
  companyName,
  hideCurrentSessionMeta = false,
  onPress,
  transaction,
}: TransactionRowProps) {
  const router = useRouter();
  const canOpen = Boolean(transaction.transactionId);
  const side = getTransactionSideLabel(transaction.side);
  const reset = isResetTransaction(transaction);
  const realizedProfitLoss =
    transaction.realizedProfitLossAfterFees ?? transaction.realizedProfitLoss;
  const profitLoss =
    transaction.side === 'SELL' ? formatSignedPaperMoney(realizedProfitLoss) : '-';

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
      style={({ pressed }) => [
        styles.transactionRow,
        reset ? styles.resetTransactionRow : undefined,
        pressed && canOpen ? styles.pressedRow : undefined,
      ]}>
      <View style={styles.transactionStatusCell}>
        <Text selectable style={[styles.transactionSideText, reset ? styles.resetText : transaction.side === 'SELL' ? styles.sellText : styles.buyText]}>
          {reset ? 'Reset' : side}
        </Text>
        {!hideCurrentSessionMeta && transaction.isCurrentSession === false ? (
          <Text selectable style={styles.transactionMeta}>Old session</Text>
        ) : null}
      </View>
      <View style={styles.transactionSymbolCell}>
        <Text selectable numberOfLines={1} style={styles.symbol}>
          {reset ? 'Portfolio Reset' : transaction.symbol ?? 'Unavailable'}
        </Text>
        <Text selectable numberOfLines={1} style={styles.company}>
          {reset ? `Session ${transaction.sessionNumber ?? 'Unavailable'} starts` : companyName ?? 'Company unavailable'}
        </Text>
      </View>
      <View style={styles.transactionQuantityCell}>
        <Text selectable numberOfLines={1} style={styles.transactionPrimary}>
          {reset ? '-' : formatPaperMoney(transaction.executionPrice ?? transaction.price)}
        </Text>
        <Text selectable numberOfLines={1} style={styles.transactionMeta}>
          {reset ? '' : formatQuantity(transaction.quantity)}
        </Text>
      </View>
      <View style={styles.transactionProfitLossCell}>
        <Text
          selectable
          numberOfLines={1}
          style={[
            styles.transactionPrimary,
            transaction.side === 'SELL' ? { color: getMovementColor(realizedProfitLoss) } : undefined,
          ]}>
          {profitLoss}
        </Text>
      </View>
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
      <IconSymbol color={BRAND_NAVY} name="checkmark.circle.fill" size={24} />
      <View style={styles.resultCopy}>
        <Text selectable style={styles.resultTitle}>
          {title}
        </Text>
        {children}
      </View>
    </View>
  );
}

export function FieldRow({
  label,
  tone,
  toneValue,
  value,
}: {
  label: string;
  tone?: 'positive' | 'negative' | 'neutral';
  toneValue?: ApiNumber;
  value: string;
}) {
  const toneColor =
    tone === 'positive'
      ? Colors.light.success
      : tone === 'negative'
        ? Colors.light.destructive
        : tone === 'neutral'
          ? Colors.light.mutedText
          : undefined;

  return (
    <View style={styles.fieldRow}>
      <Text selectable style={styles.fieldLabel}>
        {label}
      </Text>
      <Text
        selectable
        style={[
          styles.fieldValue,
          toneColor ? { color: toneColor } : toneValue === undefined ? undefined : { color: getMovementColor(toneValue) },
        ]}>
        {value}
      </Text>
    </View>
  );
}

export function ConfirmOverlay({
  cancelLabel = 'Cancel',
  children,
  confirmLabel,
  danger = false,
  message,
  onCancel,
  onConfirm,
  pending,
  pendingLabel,
  title,
  visible,
}: {
  cancelLabel?: string;
  children?: ReactNode;
  confirmLabel: string;
  danger?: boolean;
  message?: string;
  onCancel: () => void;
  onConfirm: () => void;
  pending: boolean;
  pendingLabel: string;
  title: string;
  visible: boolean;
}) {
  return (
    <Modal animationType="fade" transparent visible={visible}>
      <View style={styles.modalBackdrop}>
        <View style={styles.confirmCard}>
          <View style={styles.confirmHeaderLayer}>
            <Text selectable style={styles.confirmTitle}>{title}</Text>
            {message ? <Text selectable style={styles.confirmMessage}>{message}</Text> : null}
          </View>
          {children}
          <View style={styles.confirmActions}>
            <ActionButton
              disabled={pending}
              label={cancelLabel}
              onPress={onCancel}
              style={styles.confirmButton}
              variant="ghost"
            />
            <ActionButton
              disabled={pending}
              label={pending ? pendingLabel : confirmLabel}
              onPress={onConfirm}
              style={[styles.confirmButton, danger ? undefined : styles.confirmPrimaryButton]}
              variant={danger ? 'danger' : 'primary'}
            />
          </View>
        </View>
      </View>
    </Modal>
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
  brandLogo: {
    height: 30,
    width: 30,
  },
  headerTitle: {
    color: Colors.light.text,
    flex: 1,
    fontSize: 22,
    fontWeight: '700',
    lineHeight: 28,
  },
  section: {
    backgroundColor: Colors.light.surface,
    gap: Spacing.sm,
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
    fontWeight: '600',
  },
  metric: {
    flex: 1,
    gap: Spacing.xs,
    minWidth: 112,
  },
  metricLabel: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '500',
  },
  metricLabelProminent: {
    color: Colors.light.mutedText,
    fontSize: 14,
    fontWeight: '500',
  },
  metricValue: {
    color: Colors.light.text,
    fontSize: 19,
    fontVariant: ['tabular-nums'],
    fontWeight: '600',
  },
  metricValueProminent: {
    fontSize: 28,
    fontWeight: '700',
    lineHeight: 34,
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
  tabs: {
    backgroundColor: Colors.light.background,
    flexDirection: 'row',
    gap: Spacing.md,
    paddingBottom: Spacing.md,
    paddingHorizontal: Spacing.md,
    paddingTop: Spacing.sm,
  },
  tab: {
    backgroundColor: '#E5E7EB',
    borderBottomColor: 'transparent',
    borderBottomWidth: 2,
    borderRadius: 10,
    flex: 1,
    minHeight: 36,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: Spacing.lg,
  },
  tabActive: {
    backgroundColor: '#FFF7ED',
    borderBottomColor: '#F97316',
  },
  tabText: {
    color: Colors.light.text,
    fontSize: 15,
    fontWeight: '500',
  },
  tabTextActive: {
    color: '#C2410C',
    fontWeight: '700',
  },
  positionsTable: {
    backgroundColor: Colors.light.surface,
    position: 'relative',
  },
  positionsTableRow: {
    flexDirection: 'row',
  },
  fixedColumn: {
    backgroundColor: Colors.light.surface,
    width: 94,
  },
  positionsHeaderCell: {
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    minHeight: 36,
  },
  fixedSymbolCell: {
    backgroundColor: Colors.light.surface,
    justifyContent: 'center',
    paddingHorizontal: Spacing.sm,
    width: 94,
  },
  fixedSymbolRow: {
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    gap: 2,
    justifyContent: 'center',
    minHeight: 58,
    paddingHorizontal: Spacing.sm,
    width: 94,
  },
  metricHeaderRow: {
    flexDirection: 'row',
  },
  metricDataRow: {
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    minHeight: 58,
  },
  metricCell: {
    alignItems: 'flex-end',
    justifyContent: 'center',
    paddingHorizontal: 4,
  },
  actionColumn: {
    alignItems: 'center',
    width: 100,
  },
  tableHeaderText: {
    color: Colors.light.mutedText,
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  tableHeaderRight: {
    textAlign: 'right',
  },
  tableHeaderCenter: {
    textAlign: 'center',
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
  positionPrimary: {
    color: Colors.light.text,
    fontSize: 14,
    fontVariant: ['tabular-nums'],
    fontWeight: '500',
    lineHeight: 18,
    textAlign: 'right',
  },
  positionSecondary: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontVariant: ['tabular-nums'],
    lineHeight: 16,
    textAlign: 'right',
  },
  sellButton: {
    alignItems: 'center',
    borderColor: Colors.light.destructive,
    borderRadius: 999,
    borderWidth: 1,
    minHeight: 30,
    justifyContent: 'center',
    width: 66,
  },
  sellButtonPressed: {
    backgroundColor: Colors.light.destructive,
  },
  sellButtonText: {
    color: Colors.light.destructive,
    fontSize: 12,
    fontWeight: '700',
  },
  sellButtonTextPressed: {
    color: '#FFFFFF',
  },
  transactionHeaderRow: {
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 36,
    paddingHorizontal: Spacing.md,
  },
  transactionHeaderText: {
    color: Colors.light.mutedText,
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  transactionHeaderStatus: {
    width: 68,
  },
  transactionHeaderSymbol: {
    flex: 1,
  },
  transactionHeaderQuantity: {
    textAlign: 'right',
    width: 86,
  },
  transactionHeaderProfitLoss: {
    textAlign: 'right',
    width: 90,
  },
  transactionRow: {
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 64,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  transactionStatusCell: {
    gap: 2,
    width: 68,
  },
  transactionSymbolCell: {
    flex: 1,
    gap: 2,
    minWidth: 0,
  },
  transactionQuantityCell: {
    alignItems: 'flex-end',
    gap: 2,
    width: 86,
  },
  transactionProfitLossCell: {
    alignItems: 'flex-end',
    width: 90,
  },
  resetTransactionRow: {
    backgroundColor: '#F8FAFC',
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
    color: BRAND_NAVY,
  },
  transactionMeta: {
    color: Colors.light.mutedText,
    fontSize: 11,
    lineHeight: 15,
  },
  transactionPrimary: {
    color: Colors.light.text,
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '500',
    lineHeight: 17,
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
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'space-between',
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
  modalBackdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(15, 23, 42, 0.42)',
    flex: 1,
    justifyContent: 'center',
    padding: Spacing.lg,
  },
  confirmCard: {
    backgroundColor: Colors.light.surface,
    borderRadius: 14,
    gap: Spacing.md,
    maxWidth: 420,
    overflow: 'hidden',
    shadowColor: '#000000',
    shadowOffset: { height: 10, width: 0 },
    shadowOpacity: 0.16,
    shadowRadius: 24,
    width: '100%',
    elevation: 8,
  },
  confirmHeaderLayer: {
    backgroundColor: '#F1F5F9',
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    gap: Spacing.xs,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.lg,
  },
  confirmTitle: {
    color: Colors.light.text,
    fontSize: 20,
    fontWeight: '600',
  },
  confirmMessage: {
    color: Colors.light.text,
    fontSize: 14,
    lineHeight: 20,
  },
  confirmActions: {
    flexDirection: 'row',
    gap: Spacing.sm,
    paddingBottom: Spacing.lg,
    paddingHorizontal: Spacing.lg,
  },
  confirmButton: {
    flex: 1,
  },
  confirmPrimaryButton: {
    backgroundColor: BRAND_NAVY,
    borderColor: BRAND_NAVY,
  },
  disabled: {
    opacity: 0.46,
  },
  pressed: {
    opacity: 0.82,
  },
  pressedRow: {
    backgroundColor: '#F8FAFC',
  },
});
