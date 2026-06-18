import { StyleSheet, Text, View } from 'react-native';

import { Colors, Radius, Spacing } from '@/constants/theme';
import type {
  BehaviorProfileSummaryResponse,
  InvestmentProfileResponse,
} from '@/types/profile';

type ProfileSummaryCardProps = {
  investmentProfile: InvestmentProfileResponse | null;
  behaviorSummary?: BehaviorProfileSummaryResponse | null;
};

export function ProfileSummaryCard({
  behaviorSummary,
  investmentProfile,
}: ProfileSummaryCardProps) {
  if (!investmentProfile) {
    return (
      <View style={styles.card}>
        <Text selectable style={styles.title}>
          Profile needs setup
        </Text>
        <Text selectable style={styles.description}>
          StockMentor could not find a saved investment profile for this account.
        </Text>
      </View>
    );
  }

  const rows = [
    ['Version', investmentProfile.profileVersion?.toString()],
    ['Source', formatEnum(investmentProfile.profileSource)],
    ['Risk preference', formatEnum(investmentProfile.riskTolerance)],
    ['Goal', formatEnum(investmentProfile.investmentGoal)],
    ['Experience', formatEnum(investmentProfile.experienceLevel)],
    ['Volatility comfort', formatEnum(investmentProfile.preferredVolatility)],
    ['Time horizon', formatEnum(investmentProfile.preferredHorizon)],
    ['Risk score', formatNullableNumber(investmentProfile.riskScore)],
    ['Goal score', formatNullableNumber(investmentProfile.goalScore)],
    ['Experience score', formatNullableNumber(investmentProfile.experienceScore)],
    ['Updated', formatDate(investmentProfile.updatedAt)],
  ];

  return (
    <View style={styles.card}>
      <View style={styles.header}>
        <Text selectable style={styles.eyebrow}>
          Investment profile
        </Text>
        <Text selectable style={styles.title}>
          Your saved preferences
        </Text>
      </View>

      <View style={styles.rows}>
        {rows.map(([label, value]) => (
          <View key={label} style={styles.row}>
            <Text selectable style={styles.rowLabel}>
              {label}
            </Text>
            <Text selectable style={styles.rowValue}>
              {value || 'Not provided'}
            </Text>
          </View>
        ))}
      </View>

      {behaviorSummary ? (
        <View style={styles.behaviorBox}>
          <Text selectable style={styles.behaviorTitle}>
            Practice behavior summary
          </Text>
          <Text selectable style={styles.description}>
            {behaviorSummary.behaviorSummaryText || behaviorSummary.sourceNote || 'No practice behavior summary yet.'}
          </Text>
        </View>
      ) : null}
    </View>
  );
}

function formatEnum(value: string | null | undefined) {
  if (!value) {
    return null;
  }

  return value
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function formatNullableNumber(value: number | null | undefined) {
  return value === null || value === undefined ? null : value.toString();
}

function formatDate(value: string | null | undefined) {
  if (!value) {
    return null;
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.lg,
    padding: Spacing.lg,
  },
  header: {
    gap: Spacing.xs,
  },
  eyebrow: {
    color: Colors.light.secondaryTint,
    fontSize: 12,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  title: {
    color: Colors.light.text,
    fontSize: 18,
    fontWeight: '800',
  },
  description: {
    color: Colors.light.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
  rows: {
    gap: Spacing.sm,
  },
  row: {
    borderTopColor: Colors.light.border,
    borderTopWidth: 1,
    gap: Spacing.xs,
    paddingTop: Spacing.sm,
  },
  rowLabel: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  rowValue: {
    color: Colors.light.text,
    fontSize: 15,
    fontWeight: '700',
    lineHeight: 20,
  },
  behaviorBox: {
    backgroundColor: Colors.light.softTeal,
    borderRadius: Radius.md,
    gap: Spacing.xs,
    padding: Spacing.md,
  },
  behaviorTitle: {
    color: Colors.light.secondaryTint,
    fontSize: 14,
    fontWeight: '800',
  },
});
