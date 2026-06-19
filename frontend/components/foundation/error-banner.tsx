import { StyleSheet, Text, View } from 'react-native';
import type { ReactNode } from 'react';
import { Colors, Radius, Spacing } from '@/constants/theme';

type ErrorBannerProps = {
  title?: string;
  message?: string;
  children?: ReactNode;
};

export function ErrorBanner({
  message,
  title = 'Something went wrong',
  children,
}: ErrorBannerProps) {
  return (
    <View accessibilityLiveRegion="assertive" accessibilityRole="alert" style={styles.container}>
      <Text selectable style={styles.title}>
        {title}
      </Text>

      {children ? (
        children
      ) : message ? (
        <Text selectable style={styles.message}>
          {message}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#FEF2F2',
    borderColor: '#FECACA',
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.xs,
    padding: Spacing.lg,
  },
  title: {
    color: Colors.light.destructive,
    fontSize: 15,
    fontWeight: '800',
  },
  message: {
    color: Colors.light.text,
    fontSize: 14,
    lineHeight: 20,
  },
});
