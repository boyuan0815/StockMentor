import { StyleSheet, Text, View } from 'react-native';

import { Colors, Radius, Spacing } from '@/constants/theme';
import { labelize } from '@/utils/stock-display';

type PillTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

type StockPillProps = {
  label: string | null | undefined;
  tone?: PillTone;
};

export function StockPill({ label, tone = 'neutral' }: StockPillProps) {
  const text = labelize(label);

  return (
    <View style={[styles.pill, getPillStyle(tone)]}>
      <Text selectable style={[styles.text, getTextStyle(tone)]}>
        {text}
      </Text>
    </View>
  );
}

export function RiskPill({ value }: { value: string | null | undefined }) {
  const normalized = value?.toLowerCase();
  const tone = normalized === 'aggressive' || normalized === 'high' ? 'danger' : normalized === 'moderate' || normalized === 'medium' ? 'warning' : 'success';
  return <StockPill label={value ?? 'Risk unavailable'} tone={tone} />;
}

export function TrendPill({ value }: { value: string | null | undefined }) {
  const normalized = value?.toLowerCase();
  const tone = normalized?.includes('up') ? 'success' : normalized?.includes('down') ? 'danger' : 'info';
  return <StockPill label={value ?? 'Trend unavailable'} tone={tone} />;
}

function getPillStyle(tone: PillTone) {
  switch (tone) {
    case 'success':
      return styles.successPill;
    case 'warning':
      return styles.warningPill;
    case 'danger':
      return styles.dangerPill;
    case 'info':
      return styles.infoPill;
    case 'neutral':
    default:
      return styles.neutralPill;
  }
}

function getTextStyle(tone: PillTone) {
  switch (tone) {
    case 'success':
      return styles.successText;
    case 'warning':
      return styles.warningText;
    case 'danger':
      return styles.dangerText;
    case 'info':
      return styles.infoText;
    case 'neutral':
    default:
      return styles.neutralText;
  }
}

const styles = StyleSheet.create({
  pill: {
    alignSelf: 'flex-start',
    borderRadius: Radius.sm,
    borderWidth: 1,
    paddingHorizontal: Spacing.sm,
    paddingVertical: Spacing.xs,
  },
  text: {
    fontSize: 12,
    fontWeight: '800',
    lineHeight: 16,
  },
  neutralPill: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
  },
  neutralText: {
    color: Colors.light.mutedText,
  },
  infoPill: {
    backgroundColor: Colors.light.softBlue,
    borderColor: '#BFDBFE',
  },
  infoText: {
    color: Colors.light.tint,
  },
  successPill: {
    backgroundColor: '#DCFCE7',
    borderColor: '#BBF7D0',
  },
  successText: {
    color: Colors.light.success,
  },
  warningPill: {
    backgroundColor: '#FEF3C7',
    borderColor: '#FDE68A',
  },
  warningText: {
    color: Colors.light.caution,
  },
  dangerPill: {
    backgroundColor: '#FEE2E2',
    borderColor: '#FECACA',
  },
  dangerText: {
    color: Colors.light.destructive,
  },
});
