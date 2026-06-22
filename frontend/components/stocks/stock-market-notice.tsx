import { useEffect, useRef, useState } from 'react';
import { Animated, Easing, StyleSheet, Text, View } from 'react-native';

import { Colors, Spacing } from '@/constants/theme';
import type { DelayedStockFields, StockListItemResponse } from '@/types/stocks';
import { getMarketNoticeCopy } from '@/utils/stock-display';

type StockMarketNoticeProps = {
  stocks: (DelayedStockFields | StockListItemResponse)[];
};

export function StockMarketNotice({ stocks }: StockMarketNoticeProps) {
  const copy = getMarketNoticeCopy(stocks);
  const translateX = useRef(new Animated.Value(0)).current;
  const [viewportWidth, setViewportWidth] = useState(0);
  const [textWidth, setTextWidth] = useState(0);
  const estimatedTextWidth = Math.ceil(copy.label.length * 7.8);
  const displayedTextWidth = Math.max(textWidth, estimatedTextWidth, viewportWidth);
  const shouldAnimate = viewportWidth > 0 && displayedTextWidth > viewportWidth;
  const marqueeTextStyle = [
    styles.label,
    { width: displayedTextWidth },
  ];

  useEffect(() => {
    translateX.stopAnimation();
    translateX.setValue(0);

    if (!shouldAnimate) {
      return undefined;
    }

    const gap = 56;
    const distance = displayedTextWidth + gap;
    const animation = Animated.loop(
      Animated.sequence([
        Animated.delay(800),
        Animated.timing(translateX, {
          duration: Math.max(9000, distance * 42),
          easing: Easing.linear,
          toValue: -distance,
          useNativeDriver: true,
        }),
        Animated.timing(translateX, {
          duration: 0,
          toValue: 0,
          useNativeDriver: true,
        }),
      ]),
    );

    animation.start();
    return () => animation.stop();
  }, [copy.label, displayedTextWidth, shouldAnimate, translateX]);

  return (
    <View style={styles.container}>
      <View
        onLayout={(event) => setViewportWidth(event.nativeEvent.layout.width)}
        style={styles.viewport}>
        <Animated.View
          style={[
            styles.marquee,
            shouldAnimate ? { transform: [{ translateX }] } : undefined,
          ]}>
          <Text selectable numberOfLines={1} ellipsizeMode="clip" style={marqueeTextStyle}>
            {copy.label}
          </Text>
          {shouldAnimate ? (
            <Text
              selectable
              numberOfLines={1}
              ellipsizeMode="clip"
              style={[marqueeTextStyle, styles.duplicateLabel]}>
              {copy.label}
            </Text>
          ) : null}
        </Animated.View>
      </View>
      <Text
        onLayout={(event) => setTextWidth(event.nativeEvent.layout.width)}
        style={[styles.label, styles.measureLabel]}>
        {copy.label}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#FFF1F2',
    borderBottomColor: '#FFE4E6',
    borderBottomWidth: 1,
    borderTopColor: '#FFE4E6',
    borderTopWidth: 1,
    justifyContent: 'center',
    minHeight: 32,
    overflow: 'hidden',
    paddingHorizontal: Spacing.md,
  },
  viewport: {
    overflow: 'hidden',
    width: '100%',
  },
  marquee: {
    alignItems: 'center',
    flexDirection: 'row',
  },
  label: {
    color: Colors.light.destructive,
    flexShrink: 0,
    fontSize: 12,
    fontWeight: '500',
    includeFontPadding: false,
  },
  duplicateLabel: {
    marginLeft: 56,
  },
  measureLabel: {
    opacity: 0,
    position: 'absolute',
    left: 0,
    top: -100,
  },
});
