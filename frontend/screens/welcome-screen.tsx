import { Link, type Href } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { ActionButton } from '@/components/foundation/action-button';
import { PageHeader } from '@/components/foundation/page-header';
import { Screen } from '@/components/foundation/screen';
import { Colors, Radius, Spacing } from '@/constants/theme';

export function WelcomeScreen() {
  return (
    <Screen scroll="auto" contentStyle={styles.content}>
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

      <Text selectable style={styles.dataNote}>
        Frontend screens call only the Spring Boot backend. Stock browsing, suggestions, and
        practice tools arrive in later phases.
      </Text>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: {
    alignSelf: 'center',
    gap: Spacing.lg,
    justifyContent: 'center',
    maxWidth: 520,
    width: '100%',
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
  dataNote: {
    color: Colors.light.mutedText,
    fontSize: 13,
    lineHeight: 19,
    textAlign: 'center',
  },
});
