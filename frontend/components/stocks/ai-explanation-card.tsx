import { StyleSheet, Text, View } from 'react-native';

import { EmptyState } from '@/components/foundation/empty-state';
import { HighlightedText } from '@/components/stocks/highlighted-text';
import { Colors, Spacing } from '@/constants/theme';
import type { StockExplanationResponse } from '@/types/stocks';

type AiExplanationCardProps = {
  explanation: StockExplanationResponse;
};

export function AiExplanationCard({ explanation }: AiExplanationCardProps) {
  const hasExplanation = explanation.available && Boolean(explanation.explanation?.trim());
  const explanationText = explanation.explanation?.trim().replace(/\n{3,}/g, '\n\n') ?? '';

  return (
    <View style={styles.container}>
      {hasExplanation ? (
        <View style={styles.explanationStack}>
          <HighlightedText
            highlights={explanation.explanationHighlights}
            style={styles.explanationText}
            text={explanationText}
          />
          <Text selectable style={styles.dataNote}>
            Data window: {explanation.dataStartDate ?? 'start unavailable'} to {explanation.dataEndDate ?? 'end unavailable'}
          </Text>
        </View>
      ) : (
        <EmptyState
          title="Explanation unavailable"
          description={explanation.message ?? 'StockMentor does not have a learning explanation available for this stock yet.'}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: Spacing.md,
  },
  explanationStack: {
    gap: Spacing.sm,
    paddingTop: Spacing.md,
  },
  explanationText: {
    color: Colors.light.text,
    fontSize: 15,
    lineHeight: 23,
  },
  dataNote: {
    color: Colors.light.mutedText,
    fontSize: 12,
    lineHeight: 17,
  },
});
