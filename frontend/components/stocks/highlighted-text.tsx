import { StyleSheet, Text, type StyleProp, type TextStyle } from 'react-native';

type HighlightSegment = {
  startIndex: number;
  endIndex: number;
  style: string;
};

type HighlightedTextProps = {
  highlights?: HighlightSegment[] | null;
  numberOfLines?: number;
  selectable?: boolean;
  style: StyleProp<TextStyle>;
  text: string;
};

export function HighlightedText({
  highlights,
  numberOfLines,
  selectable = true,
  style,
  text,
}: HighlightedTextProps) {
  const segments = normalizeSegments(text, highlights);
  if (segments.length === 0) {
    return (
      <Text numberOfLines={numberOfLines} selectable={selectable} style={style}>
        {text}
      </Text>
    );
  }

  let cursor = 0;
  return (
    <Text numberOfLines={numberOfLines} selectable={selectable} style={style}>
      {segments.map((segment, index) => {
        const before = text.slice(cursor, segment.startIndex);
        const highlighted = text.slice(segment.startIndex, segment.endIndex);
        cursor = segment.endIndex;
        return (
          <Text key={`${segment.startIndex}-${segment.endIndex}`}>
            {before}
            <Text style={highlightStyle(segment.style)}>{highlighted}</Text>
            {index === segments.length - 1 ? text.slice(cursor) : null}
          </Text>
        );
      })}
    </Text>
  );
}

function normalizeSegments(text: string, highlights?: HighlightSegment[] | null) {
  if (!highlights?.length) {
    return [];
  }
  return highlights
    .filter(
      (segment) =>
        segment.startIndex >= 0 &&
        segment.endIndex > segment.startIndex &&
        segment.endIndex <= text.length &&
        ['positive', 'negative', 'emphasis'].includes(segment.style),
    )
    .sort((a, b) => a.startIndex - b.startIndex)
    .filter((segment, index, segments) => index === 0 || segment.startIndex >= segments[index - 1].endIndex)
    .slice(0, 3);
}

function highlightStyle(style: string) {
  if (style === 'positive') {
    return styles.positive;
  }
  if (style === 'negative') {
    return styles.negative;
  }
  return styles.emphasis;
}

const styles = StyleSheet.create({
  emphasis: {
    fontWeight: '800',
  },
  negative: {
    color: '#DC2626',
    fontWeight: '800',
  },
  positive: {
    color: '#059669',
    fontWeight: '800',
  },
});
