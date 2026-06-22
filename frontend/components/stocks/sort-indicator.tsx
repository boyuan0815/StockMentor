import { StyleSheet, View } from 'react-native';

import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors } from '@/constants/theme';

type SortIndicatorProps = {
  direction: 'asc' | 'desc';
  selected: boolean;
};

export function SortIndicator({ direction, selected }: SortIndicatorProps) {
  const upActive = selected && direction === 'asc';

  if (selected) {
    return (
      <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.selectedContainer}>
        <IconSymbol
          color={Colors.light.text}
          name={upActive ? 'arrowtriangle.up.fill' : 'arrowtriangle.down.fill'}
          size={9}
        />
      </View>
    );
  }

  return (
    <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.container}>
      <IconSymbol
        color={Colors.light.mutedText}
        name="arrowtriangle.up.fill"
        size={7}
      />
      <IconSymbol
        color={Colors.light.mutedText}
        name="arrowtriangle.down.fill"
        size={7}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    height: 12,
    justifyContent: 'center',
    width: 8,
  },
  selectedContainer: {
    alignItems: 'center',
    height: 12,
    justifyContent: 'center',
    width: 8,
  },
});
