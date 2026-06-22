import { StyleSheet, View } from 'react-native';

import { Colors, Radius, Spacing } from '@/constants/theme';

type SkeletonBlockProps = {
  height?: number;
  width?: `${number}%` | number;
};

export function SkeletonBlock({ height = 18, width = '100%' }: SkeletonBlockProps) {
  return <View accessibilityLabel="Loading" style={[styles.block, { height, width }]} />;
}

export function SkeletonRows({ count = 4 }: { count?: number }) {
  return (
    <View style={styles.stack}>
      {Array.from({ length: count }).map((_, index) => (
        <View key={index} style={styles.row}>
          <View style={styles.rowMain}>
            <SkeletonBlock height={16} width="28%" />
            <SkeletonBlock height={12} width="58%" />
          </View>
          <View style={styles.rowSide}>
            <SkeletonBlock height={16} width={74} />
            <SkeletonBlock height={12} width={56} />
          </View>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  block: {
    backgroundColor: '#E5E7EB',
    borderRadius: Radius.sm,
  },
  stack: {
    gap: Spacing.sm,
  },
  row: {
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    gap: Spacing.md,
    minHeight: 68,
    padding: Spacing.md,
  },
  rowMain: {
    flex: 1,
    gap: Spacing.sm,
  },
  rowSide: {
    alignItems: 'flex-end',
    gap: Spacing.sm,
  },
});
