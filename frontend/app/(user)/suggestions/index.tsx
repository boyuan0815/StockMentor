import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useRef } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { EmptyState } from '@/components/foundation/empty-state';
import { PaperHeader } from '@/components/paper-trading/paper-trading-ui';
import { Colors, Radius, Spacing } from '@/constants/theme';

export default function SuggestionsRoute() {
  const insets = useSafeAreaInsets();
  const scrollRef = useRef<ScrollView | null>(null);

  useFocusEffect(
    useCallback(() => {
      scrollRef.current?.scrollTo({ animated: false, y: 0 });
      return undefined;
    }, []),
  );

  return (
    <View style={styles.container}>
      <View style={[styles.fixedHeader, { paddingTop: insets.top + 2 }]}>
        <PaperHeader brandIcon title="Suggestions" />
      </View>
      <ScrollView
        ref={scrollRef}
        contentContainerStyle={[
          styles.content,
          { paddingBottom: Math.max(Spacing.xxl, insets.bottom + Spacing.xl) },
        ]}
        contentInsetAdjustmentBehavior="never"
        overScrollMode="never"
        style={styles.scroller}>
        <View style={styles.card}>
          <EmptyState
            title="Educational suggestions"
            description="A later phase will read cached backend suggestions and keep refresh cooldown guardrails."
          />
          <Text selectable style={styles.note}>
            StockMentor will keep suggestions educational and separate from real financial advice.
          </Text>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  content: {
    gap: 0,
    paddingHorizontal: 0,
    width: '100%',
  },
  fixedHeader: {
    backgroundColor: Colors.light.background,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
  },
  scroller: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  card: {
    backgroundColor: Colors.light.surface,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
    borderTopColor: Colors.light.border,
    borderTopWidth: 1,
    gap: Spacing.md,
    padding: Spacing.md,
  },
  note: {
    backgroundColor: '#F8FAFC',
    borderColor: Colors.light.border,
    borderRadius: Radius.sm,
    borderWidth: 1,
    color: Colors.light.mutedText,
    fontSize: 13,
    lineHeight: 18,
    padding: Spacing.md,
  },
});
