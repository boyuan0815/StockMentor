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
        eyebrow="StockMentor"
        title="Learn the market without the rush"
        description="A calm place to set up a beginner investing profile before exploring delayed educational market data and practice tools."
      />

      <View style={styles.ledgerCard}>
        <Text selectable style={styles.ledgerLabel}>
          Beginner-first setup
        </Text>
        <Text selectable style={styles.ledgerText}>
          Create an account, answer the onboarding quiz, and let the backend save your profile.
          StockMentor keeps this phase focused on account access and profile setup.
        </Text>
      </View>

      <View style={styles.actions}>
        <Link href={'/login' as Href} asChild>
          <ActionButton label="Sign in" />
        </Link>
        <Link href={'/register' as Href} asChild>
          <ActionButton label="Create account" variant="secondary" />
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
