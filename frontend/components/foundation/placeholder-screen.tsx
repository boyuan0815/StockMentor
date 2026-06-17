import { Link, type Href } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { ActionButton } from '@/components/foundation/action-button';
import { PageHeader } from '@/components/foundation/page-header';
import { Screen } from '@/components/foundation/screen';
import { Colors, Radius, Spacing } from '@/constants/theme';

type PlaceholderAction = {
  label: string;
  href: string;
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
};

type PlaceholderScreenProps = {
  eyebrow: string;
  title: string;
  description: string;
  actions?: PlaceholderAction[];
};

export function PlaceholderScreen({
  actions = [],
  description,
  eyebrow,
  title,
}: PlaceholderScreenProps) {
  return (
    <Screen>
      <PageHeader eyebrow={eyebrow} title={title} description={description} />
      <View style={styles.card}>
        <Text selectable style={styles.label}>
          Placeholder
        </Text>
        <Text selectable style={styles.body}>
          This screen is intentionally incomplete in Phase 1. It exists so later frontend phases
          can add real backend-connected flows without changing the route shell.
        </Text>
      </View>
      {actions.length > 0 ? (
        <View style={styles.actions}>
          {actions.map((action) => (
            <Link key={`${action.href}-${action.label}`} href={action.href as Href} asChild>
              <ActionButton label={action.label} variant={action.variant} />
            </Link>
          ))}
        </View>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.sm,
    padding: Spacing.lg,
  },
  label: {
    color: Colors.light.secondaryTint,
    fontSize: 13,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  body: {
    color: Colors.light.mutedText,
    fontSize: 15,
    lineHeight: 22,
  },
  actions: {
    gap: Spacing.sm,
  },
});
