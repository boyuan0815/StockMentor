import { useEffect, useRef } from 'react';
import { Animated, StyleSheet, Text, View } from 'react-native';

import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Spacing } from '@/constants/theme';
import type {
  BehaviorProfileSummaryResponse,
  InvestmentProfileResponse,
} from '@/types/profile';

type ProfileSummaryCardProps = {
  investmentProfile: InvestmentProfileResponse | null;
  behaviorSummary?: BehaviorProfileSummaryResponse | null;
  animationKey?: number;
};

export function ProfileSummaryCard({ animationKey = 0, behaviorSummary, investmentProfile }: ProfileSummaryCardProps) {
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

  const quizMeters = [
    {
      leftLabel: 'Cautious',
      rightLabel: 'Aggressive',
      score: investmentProfile.riskScore,
      title: 'Risk tolerance',
      value: formatEnum(investmentProfile.riskTolerance),
    },
    {
      leftLabel: 'Stability',
      rightLabel: 'Growth',
      score: investmentProfile.goalScore,
      title: 'Primary Goal',
      value: formatEnum(investmentProfile.investmentGoal),
    },
    {
      leftLabel: 'Novice',
      rightLabel: 'Expert',
      score: investmentProfile.experienceScore,
      title: 'Experience',
      value: formatEnum(investmentProfile.experienceLevel),
    },
  ];

  return (
    <View style={styles.stack}>
      <View style={styles.card}>
        <View style={styles.cardHeader}>
          <View>
            <Text selectable style={styles.cardTitle}>
              Quiz Generated Profile
            </Text>
            <Text selectable style={styles.cardSubtitle}>
              From your latest quiz responses
            </Text>
          </View>
          <View style={[styles.badge, styles.quizBadge]}>
            <IconSymbol color="#137A6A" name="checkmark" size={11} />
            <Text selectable style={[styles.badgeText, styles.quizBadgeText]}>
              QUIZ
            </Text>
          </View>
        </View>

        <View style={styles.meters}>
          {quizMeters.map((meter) => (
            <ProfileMeter
              accent="#137A6A"
              animationKey={animationKey}
              key={meter.title}
              {...meter}
            />
          ))}
        </View>

        <View style={styles.detailGrid}>
          <DetailTile
            label="Volatility comfort"
            value={formatEnum(investmentProfile.preferredVolatility) ?? 'Not provided'}
          />
          <DetailTile
            label="Time horizon"
            value={formatEnum(investmentProfile.preferredHorizon) ?? 'Not provided'}
          />
        </View>
      </View>

      <View style={styles.card}>
        <View style={styles.cardHeader}>
          <View>
            <Text selectable style={styles.cardTitle}>
              Paper-trading behaviour
            </Text>
            <Text selectable style={styles.cardSubtitle}>
              From your paper-trading activity
            </Text>
          </View>
          <View style={[styles.badge, styles.behaviorBadge]}>
            <IconSymbol color="#A75A22" name="chart.line.uptrend.xyaxis" size={11} />
            <Text selectable style={[styles.badgeText, styles.behaviorBadgeText]}>
              {formatEnum(behaviorSummary?.behaviorConfidence) ?? 'Low'}
            </Text>
          </View>
        </View>

        <View style={styles.detailGrid}>
          <DetailTile label="Style" value={formatEnum(behaviorSummary?.behaviorStyle) ?? 'Learning'} />
          <DetailTile label="Updated" value={behaviorUpdatedLabel(behaviorSummary)} />
        </View>

        <View>
          <View style={styles.behaviorMeterHeader}>
            <Text selectable style={styles.meterLabel}>
              RISK SIGNAL
            </Text>
            <View style={styles.behaviorScoreRow}>
              <Text selectable style={styles.behaviorScore}>
                {formatNullableNumber(behaviorSummary?.behaviorRiskScore) ?? '-'}
              </Text>
              <Text selectable style={styles.confidenceBadge}>
                {formatEnum(behaviorSummary?.behaviorConfidence) ?? 'Low'}
              </Text>
            </View>
          </View>
          <AnimatedRange
            accent="#A75A22"
            animationKey={animationKey}
            percent={clampScore(behaviorSummary?.behaviorRiskScore)}
          />
          <View style={styles.rangeLabels}>
            <Text selectable style={styles.rangeLabel}>Low</Text>
            <Text selectable style={styles.rangeLabel}>High</Text>
          </View>
        </View>
      </View>
    </View>
  );
}

function ProfileMeter({
  accent,
  animationKey,
  leftLabel,
  rightLabel,
  score,
  title,
  value,
}: {
  accent: string;
  animationKey: number;
  leftLabel: string;
  rightLabel: string;
  score: number | null | undefined;
  title: string;
  value: string | null;
}) {
  const safeScore = clampScore(score);
  return (
    <View style={styles.meter}>
      <View style={styles.meterTop}>
        <View style={styles.meterTitleRow}>
          <Text selectable style={styles.meterLabel}>
            {title}
          </Text>
          <Text selectable numberOfLines={1} style={styles.meterValue}>
            {value ?? 'Not provided'}
          </Text>
        </View>
        <Text selectable style={[styles.meterScore, { color: accent }]}>
          {safeScore}
        </Text>
      </View>
      <AnimatedRange accent={accent} animationKey={animationKey} percent={safeScore} />
      <View style={styles.rangeLabels}>
        <Text selectable style={styles.rangeLabel}>{leftLabel}</Text>
        <Text selectable style={styles.rangeLabel}>{rightLabel}</Text>
      </View>
    </View>
  );
}

function AnimatedRange({ accent, animationKey, percent }: { accent: string; animationKey: number; percent: number }) {
  const progress = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    progress.setValue(0);
    Animated.timing(progress, {
      duration: 720,
      toValue: percent,
      useNativeDriver: false,
    }).start();
  }, [animationKey, percent, progress]);

  const width = progress.interpolate({
    inputRange: [0, 100],
    outputRange: ['0%', '100%'],
  });

  return (
    <View style={styles.rangeTrack}>
      <Animated.View style={[styles.rangeFill, { backgroundColor: accent, width }]} />
      <Animated.View style={[styles.rangeThumb, { backgroundColor: accent, left: width }]} />
    </View>
  );
}

function DetailTile({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.detailTile}>
      <Text selectable style={styles.detailLabel}>
        {label}
      </Text>
      <Text selectable numberOfLines={1} style={styles.detailValue}>
        {value}
      </Text>
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

function behaviorUpdatedLabel(summary: BehaviorProfileSummaryResponse | null | undefined) {
  if (summary?.sourceNote?.toLowerCase().includes('reset')) {
    return 'Rebuilding after reset';
  }
  return formatDate(summary?.updatedAt) ?? 'No trades yet';
}

function clampScore(value: number | null | undefined) {
  return Math.max(0, Math.min(100, Math.round(value ?? 0)));
}

const styles = StyleSheet.create({
  stack: {
    gap: Spacing.md,
  },
  card: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: 20,
    borderWidth: 1,
    gap: Spacing.lg,
    padding: Spacing.lg,
  },
  badge: {
    alignItems: 'center',
    borderRadius: 999,
    flexDirection: 'row',
    gap: 5,
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  badgeText: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
  },
  behaviorBadge: {
    backgroundColor: 'rgba(167,90,34,0.10)',
  },
  behaviorBadgeText: {
    color: '#A75A22',
  },
  behaviorMeterHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 9,
  },
  behaviorScore: {
    color: Colors.light.text,
    fontSize: 16,
    fontVariant: ['tabular-nums'],
    fontWeight: '800',
  },
  behaviorScoreRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  cardHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'space-between',
  },
  cardSubtitle: {
    color: '#8B97A8',
    fontSize: 12,
    fontWeight: '500',
    marginTop: 2,
  },
  cardTitle: {
    color: '#12294A',
    fontSize: 18,
    fontWeight: '800',
  },
  confidenceBadge: {
    borderColor: '#A75A22',
    borderRadius: 999,
    borderWidth: 1,
    color: '#A75A22',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.6,
    paddingHorizontal: 9,
    paddingVertical: 2,
    textTransform: 'uppercase',
  },
  detailGrid: {
    flexDirection: 'row',
    gap: Spacing.md,
  },
  detailLabel: {
    color: '#8B97A8',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.8,
    marginBottom: 5,
    textTransform: 'uppercase',
  },
  detailTile: {
    backgroundColor: '#F6F8FB',
    borderRadius: 14,
    flex: 1,
    paddingHorizontal: 14,
    paddingVertical: 13,
  },
  detailValue: {
    color: '#12294A',
    fontSize: 15,
    fontWeight: '800',
  },
  title: {
    color: Colors.light.text,
    fontSize: 20,
    fontWeight: '900',
    lineHeight: 25,
  },
  description: {
    color: Colors.light.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
  meter: {
    gap: Spacing.xs,
  },
  meterLabel: {
    color: '#8B97A8',
    fontSize: 10.5,
    fontWeight: '800',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  meterScore: {
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    fontWeight: '800',
  },
  meterTitleRow: {
    alignItems: 'baseline',
    flex: 1,
    flexDirection: 'row',
    gap: 9,
    minWidth: 0,
  },
  meterTop: {
    alignItems: 'baseline',
    flexDirection: 'row',
    gap: Spacing.md,
    justifyContent: 'space-between',
  },
  meterValue: {
    color: '#12294A',
    flexShrink: 1,
    fontSize: 16,
    fontWeight: '800',
  },
  meters: {
    gap: 18,
  },
  quizBadge: {
    backgroundColor: 'rgba(19,122,106,0.10)',
  },
  quizBadgeText: {
    color: '#137A6A',
  },
  rangeFill: {
    borderRadius: 999,
    height: 6,
    left: 0,
    position: 'absolute',
    top: 0,
  },
  rangeLabel: {
    color: '#A9B3C2',
    fontSize: 10,
    fontWeight: '700',
  },
  rangeLabels: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 2,
  },
  rangeThumb: {
    borderColor: '#FFFFFF',
    borderRadius: 999,
    borderWidth: 3,
    height: 14,
    marginLeft: -7,
    marginTop: -4,
    position: 'absolute',
    top: 0,
    width: 14,
  },
  rangeTrack: {
    backgroundColor: '#EAEEF4',
    borderRadius: 999,
    height: 6,
    overflow: 'visible',
  },
});
