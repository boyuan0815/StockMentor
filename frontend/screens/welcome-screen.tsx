import { Link, type Href } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { ActionButton } from '@/components/foundation/action-button';
import { EmptyState } from '@/components/foundation/empty-state';
import { PageHeader } from '@/components/foundation/page-header';
import { Screen } from '@/components/foundation/screen';
import { Colors, Radius, Spacing } from '@/constants/theme';

export function WelcomeScreen() {
  return (
    <Screen contentStyle={styles.content}>
      <PageHeader
        eyebrow="StockMentor foundation"
        title="Learn the market without the rush"
        description="A calm frontend shell for beginner-friendly stock learning, delayed educational market data, and paper-trading practice."
      />

      <View style={styles.ledgerCard}>
        <Text selectable style={styles.ledgerLabel}>
          Foundation status
        </Text>
        <Text selectable style={styles.ledgerText}>
          Phase 1 sets up routes, session state, API plumbing, and base UI. The actual login,
          onboarding, stock, practice, and admin workflows arrive in later phases.
        </Text>
      </View>

      <View style={styles.actions}>
        <Link href={'/login' as Href} asChild>
          <ActionButton label="Sign in placeholder" />
        </Link>
        <Link href={'/register' as Href} asChild>
          <ActionButton label="Create account placeholder" variant="secondary" />
        </Link>
      </View>

      <EmptyState
        title="Backend-only data source"
        description="Future screens will call only the Spring Boot backend. The frontend will not call OpenAI, Twelve Data, brokerage APIs, or scheduler internals directly."
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: {
    gap: Spacing.lg,
  },
  ledgerCard: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.sm,
    padding: Spacing.xl,
  },
  ledgerLabel: {
    color: Colors.light.secondaryTint,
    fontSize: 13,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  ledgerText: {
    color: Colors.light.text,
    fontSize: 16,
    lineHeight: 24,
  },
  actions: {
    gap: Spacing.sm,
  },
});
