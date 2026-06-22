import { type Href, useLocalSearchParams, useRouter } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Screen } from '@/components/foundation/screen';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import { normalizeStockSymbol } from '@/utils/stock-display';

export default function BuyRoute() {
  const router = useRouter();
  const { from, returnTo, searchFrom, searchSymbol, symbol } = useLocalSearchParams<{
    from?: string;
    returnTo?: string;
    searchFrom?: string;
    searchSymbol?: string;
    symbol?: string;
  }>();
  const normalizedSymbol = normalizeStockSymbol(symbol);

  const handleBack = () => {
    if (from === 'detail' && normalizedSymbol) {
      router.replace({
        pathname: '/stocks/[symbol]',
        params: buildDetailParams(normalizedSymbol, returnTo, searchFrom, searchSymbol),
      } as Href);
      return;
    }

    if (from === 'stocks') {
      router.replace('/stocks' as Href);
      return;
    }

    if (router.canGoBack()) {
      router.back();
      return;
    }

    if (normalizedSymbol) {
      router.replace({
        pathname: '/stocks/[symbol]',
        params: { symbol: normalizedSymbol },
      } as Href);
      return;
    }

    router.replace('/stocks' as Href);
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
            Paper Trade
          </Text>
          <Text selectable style={styles.subtitle}>
            Placeholder only
          </Text>
        </View>
      </View>

      <View style={styles.panel}>
        <Text selectable style={styles.symbol}>
          {normalizedSymbol || 'Stock'}
        </Text>
        <Text selectable style={styles.body}>
          Practice trading will be added in the next phase.
        </Text>
      </View>
    </Screen>
  );
}

function buildDetailParams(
  symbol: string,
  returnTo: string | undefined,
  searchFrom: string | undefined,
  searchSymbol: string | undefined,
) {
  const params: Record<string, string> = { symbol };
  if (returnTo) {
    params.returnTo = returnTo;
  }
  if (searchFrom) {
    params.searchFrom = searchFrom;
  }
  if (searchSymbol) {
    params.searchSymbol = searchSymbol;
  }
  return params;
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
    gap: Spacing.sm,
    padding: Spacing.md,
  },
  symbol: {
    color: Colors.light.text,
    fontSize: 22,
    fontWeight: '700',
  },
  body: {
    color: Colors.light.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
  pressed: {
    opacity: 0.82,
  },
});
