import { Link, type Href } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { ActionButton } from '@/components/foundation/action-button';
import { EmptyState } from '@/components/foundation/empty-state';
import { PageHeader } from '@/components/foundation/page-header';
import { Screen } from '@/components/foundation/screen';
import { Colors, Radius, Spacing } from '@/constants/theme';
import { useAuthSession } from '@/providers/auth-session-provider';

export function DashboardScreen() {
  const { user } = useAuthSession();

  return (
    <Screen scroll="auto" contentStyle={styles.content}>
      <PageHeader
        eyebrow="Dashboard"
        title={`Welcome${user?.username ? `, ${user.username}` : ''}`}
        description="Your account is ready. This phase keeps the dashboard focused on profile access while later phases add stock learning and practice tools."
      />

      <View style={styles.statusCard}>
        <Text selectable style={styles.statusLabel}>
          Account state
        </Text>
        <Text selectable style={styles.statusText}>
          Onboarding is complete for this session. StockMentor will use backend responses for future learning, suggestion, and practice screens.
        </Text>
      </View>

      <View style={styles.actions}>
        <Link href={'/profile' as Href} asChild>
          <ActionButton label="View profile" />
        </Link>
      </View>

      <EmptyState
        title="More learning tools arrive later"
        description="Stock browsing, AI suggestions, watchlists, and paper trading remain placeholders until their scoped frontend phases."
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: {
    alignSelf: 'center',
    gap: Spacing.lg,
    justifyContent: 'center',
    maxWidth: 560,
    width: '100%',
  },
  statusCard: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.sm,
    padding: Spacing.lg,
  },
  statusLabel: {
    color: Colors.light.secondaryTint,
    fontSize: 12,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  statusText: {
    color: Colors.light.text,
    fontSize: 15,
    lineHeight: 22,
  },
  actions: {
    gap: Spacing.sm,
  },
});
