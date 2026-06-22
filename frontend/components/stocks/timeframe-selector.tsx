import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Colors, Radius, Spacing } from '@/constants/theme';
import type { StockTimeframe } from '@/types/stocks';

type TimeframeSelectorProps = {
  selectedTimeframe: StockTimeframe;
  timeframes: StockTimeframe[];
  pending: boolean;
  onSelect: (timeframe: StockTimeframe) => void;
};

export function TimeframeSelector({
  onSelect,
  pending,
  selectedTimeframe,
  timeframes,
}: TimeframeSelectorProps) {
  return (
    <View accessibilityLabel="Stock history timeframe selector" style={styles.container}>
      {timeframes.map((timeframe) => {
        const selected = timeframe === selectedTimeframe;
        const disabled = pending || selected;

        return (
          <Pressable
            accessibilityHint={`Loads ${timeframe} stock history from the backend.`}
            accessibilityLabel={`${timeframe} timeframe`}
            accessibilityRole="button"
            accessibilityState={{ disabled, selected }}
            disabled={disabled}
            key={timeframe}
            onPress={() => onSelect(timeframe)}
            style={({ pressed }) => [
              styles.button,
              selected ? styles.selectedButton : undefined,
              pressed && !disabled ? styles.pressedButton : undefined,
              disabled && !selected ? styles.disabledButton : undefined,
            ]}>
            <Text style={[styles.label, selected ? styles.selectedLabel : undefined]}>
              {timeframe}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  button: {
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.sm,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: 44,
    minWidth: 54,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  selectedButton: {
    backgroundColor: Colors.light.tint,
    borderColor: Colors.light.tint,
  },
  pressedButton: {
    opacity: 0.86,
  },
  disabledButton: {
    backgroundColor: '#E2E8F0',
    borderColor: '#CBD5E1',
  },
  label: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '800',
  },
  selectedLabel: {
    color: Colors.light.surface,
  },
});
