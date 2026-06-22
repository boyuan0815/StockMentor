import { type Href, useLocalSearchParams, useRouter } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { ActionButton } from '@/components/foundation/action-button';
import { Screen } from '@/components/foundation/screen';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import { normalizeStockSymbol } from '@/utils/stock-display';

export default function StockExplanationRoute() {
  const router = useRouter();
  const { symbol } = useLocalSearchParams<{ symbol: string }>();
  const normalizedSymbol = normalizeStockSymbol(symbol);

  const handleBack = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }

    router.replace({
      pathname: '/stocks/[symbol]',
      params: { symbol: normalizedSymbol },
    } as Href);
  };

  return (
    <Screen contentStyle={styles.content} scroll>
      <View style={styles.header}>
        <Pressable
          accessibilityHint="Returns to the previous page."
          accessibilityLabel="Back"
          accessibilityRole="button"
          onPress={handleBack}
          style={({ pressed }) => [styles.backButton, pressed ? styles.pressed : undefined]}>
          <IconSymbol color={Colors.light.text} name="chevron.left" size={20} />
        </Pressable>
        <View style={styles.headerCopy}>
          <Text selectable style={styles.title}>
            AI Stock Explanation
          </Text>
          <Text selectable style={styles.subtitle}>
            Open from stock detail.
          </Text>
        </View>
      </View>
      <View style={styles.panel}>
        <Text selectable style={styles.body}>
          This route does not request AI content.
        </Text>
      </View>
      <View style={styles.actions}>
        <ActionButton
          accessibilityHint={`Returns to ${normalizedSymbol} stock detail without loading an AI explanation.`}
          label="Back to stock detail"
          onPress={() =>
            router.replace({
              pathname: '/stocks/[symbol]',
              params: { symbol: normalizedSymbol },
            } as Href)
          }
          variant="secondary"
        />
        <ActionButton
          accessibilityHint="Returns to the previous screen."
          label="Go back"
          onPress={() => router.back()}
          variant="ghost"
        />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: {
    alignSelf: 'center',
    gap: Spacing.sm,
    maxWidth: 680,
    width: '100%',
  },
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.md,
  },
  backButton: {
    alignItems: 'center',
    height: 44,
    justifyContent: 'center',
    width: 36,
  },
  headerCopy: {
    flex: 1,
  },
  title: {
    color: Colors.light.text,
    fontSize: 18,
    fontWeight: '700',
  },
  subtitle: {
    color: Colors.light.mutedText,
    fontSize: 13,
    lineHeight: 18,
  },
  panel: {
    backgroundColor: Colors.light.surface,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    borderTopColor: Colors.light.border,
    borderTopWidth: 1,
    padding: Spacing.md,
  },
  body: {
    color: Colors.light.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
  actions: {
    gap: Spacing.sm,
  },
  pressed: {
    opacity: 0.82,
  },
});
