import { StyleSheet, Text, View } from 'react-native';

import { Colors, Radius, Spacing } from '@/constants/theme';

type EmptyStateProps = {
  title: string;
  description: string;
};

export function EmptyState({ description, title }: EmptyStateProps) {
  return (
    <View style={styles.container}>
      <Text selectable style={styles.title}>
        {title}
      </Text>
      <Text selectable style={styles.description}>
        {description}
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
    gap: Spacing.sm,
    padding: Spacing.lg,
  },
  title: {
    color: Colors.light.text,
    fontSize: 17,
    fontWeight: '800',
  },
  description: {
    color: Colors.light.mutedText,
    fontSize: 15,
    lineHeight: 22,
  },
});
