import { StyleSheet, Text, View } from 'react-native';

import { EmptyState } from '@/components/foundation/empty-state';
import { Colors, Spacing } from '@/constants/theme';
import type { StockExplanationResponse } from '@/types/stocks';

type AiExplanationCardProps = {
  explanation: StockExplanationResponse;
};

type HighlightTone = 'positive' | 'negative' | 'caution';

const phraseHighlights: { phrase: string; tone: HighlightTone }[] = [
  { phrase: 'increased uncertainty', tone: 'negative' },
  { phrase: 'caution is necessary', tone: 'caution' },
  { phrase: 'positive momentum', tone: 'positive' },
  { phrase: 'controlled buying', tone: 'positive' },
  { phrase: 'downward movement', tone: 'negative' },
  { phrase: 'upward movement', tone: 'positive' },
  { phrase: 'downward trend', tone: 'negative' },
  { phrase: 'strong uptrend', tone: 'positive' },
  { phrase: 'steady demand', tone: 'positive' },
  { phrase: 'price increased', tone: 'positive' },
  { phrase: 'price decreased', tone: 'negative' },
  { phrase: 'price decline', tone: 'negative' },
  { phrase: 'weak momentum', tone: 'negative' },
  { phrase: 'moves downward', tone: 'negative' },
  { phrase: 'moves upward', tone: 'positive' },
  { phrase: 'fluctuations', tone: 'caution' },
  { phrase: 'uncertainty', tone: 'caution' },
  { phrase: 'volatile', tone: 'caution' },
  { phrase: 'risk', tone: 'caution' },
];

const PHRASE_HIGHLIGHTS = phraseHighlights.sort(
  (first, second) => second.phrase.length - first.phrase.length,
);

export function AiExplanationCard({ explanation }: AiExplanationCardProps) {
  const hasExplanation = explanation.available && Boolean(explanation.explanation?.trim());
  const paragraphs = explanation.explanation
    ?.split(/\r?\n+/)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean) ?? [];

  return (
    <View style={styles.container}>
      {hasExplanation ? (
        <View style={styles.explanationStack}>
          <Text selectable style={styles.disclaimerText}>
            {explanation.symbol} / {explanation.timeframe}. Learning content only, not financial advice.
          </Text>
          <View style={styles.paragraphStack}>
            {paragraphs.map((paragraph, index) => (
              <Text selectable key={`${paragraph}-${index}`} style={styles.explanationText}>
                {renderHighlightedText(paragraph)}
              </Text>
            ))}
          </View>
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

function renderHighlightedText(paragraph: string) {
  const parts: (string | { text: string; tone: HighlightTone })[] = [];
  const lowerParagraph = paragraph.toLowerCase();
  let index = 0;

  while (index < paragraph.length) {
    const signedNumber = paragraph.slice(index).match(/^[+-]\d+(?:\.\d+)?%?/);
    if (signedNumber) {
      const text = signedNumber[0];
      parts.push({ text, tone: text.startsWith('+') ? 'positive' : 'negative' });
      index += text.length;
      continue;
    }

    const phraseMatch = PHRASE_HIGHLIGHTS.find(({ phrase, tone }) => {
      if (!lowerParagraph.startsWith(phrase, index)) {
        return false;
      }
      if (!hasPhraseBoundaries(lowerParagraph, index, index + phrase.length)) {
        return false;
      }
      return !hasNegatingPrefix(lowerParagraph, index);
    });

    if (phraseMatch) {
      const text = paragraph.slice(index, index + phraseMatch.phrase.length);
      parts.push({ text, tone: phraseMatch.tone });
      index += phraseMatch.phrase.length;
      continue;
    }

    parts.push(paragraph[index]);
    index += 1;
  }

  return mergePlainParts(parts).map((part, partIndex) => {
    if (typeof part === 'string') {
      return part;
    }

    return (
      <Text key={`${part.text}-${partIndex}`} style={highlightStyleForTone(part.tone)}>
        {part.text}
      </Text>
    );
  });
}

// Short-term rendering improvement. A future backend/AI contract should return structured highlight spans.
function hasNegatingPrefix(paragraph: string, start: number) {
  const prefix = paragraph.slice(Math.max(0, start - 18), start);
  return /\b(?:lack of|without|no|not)\s*$/.test(prefix);
}

function hasPhraseBoundaries(paragraph: string, start: number, end: number) {
  const before = start === 0 ? '' : paragraph[start - 1];
  const after = end >= paragraph.length ? '' : paragraph[end];
  return !/[a-z0-9]/i.test(before) && !/[a-z0-9]/i.test(after);
}

function mergePlainParts(parts: (string | { text: string; tone: HighlightTone })[]) {
  return parts.reduce<(string | { text: string; tone: HighlightTone })[]>((merged, part) => {
    const previous = merged[merged.length - 1];
    if (typeof previous === 'string' && typeof part === 'string') {
      merged[merged.length - 1] = previous + part;
    } else {
      merged.push(part);
    }
    return merged;
  }, []);
}

function highlightStyleForTone(tone: HighlightTone) {
  if (tone === 'positive') {
    return styles.positiveText;
  }
  if (tone === 'negative') {
    return styles.negativeText;
  }
  return styles.negativeText;
}

const styles = StyleSheet.create({
  container: {
    gap: Spacing.md,
  },
  disclaimerText: {
    color: Colors.light.mutedText,
    fontSize: 12,
    lineHeight: 17,
  },
  explanationStack: {
    gap: Spacing.sm,
  },
  paragraphStack: {
    gap: Spacing.sm,
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
  positiveText: {
    color: Colors.light.success,
    fontWeight: '600',
  },
  negativeText: {
    color: Colors.light.destructive,
    fontWeight: '600',
  },
});
