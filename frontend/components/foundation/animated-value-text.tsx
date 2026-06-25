import { useEffect, useRef, useState } from 'react';
import {
  Animated,
  Text,
  type StyleProp,
  type TextProps,
  type TextStyle,
} from 'react-native';

type AnimatedValueTextProps = TextProps & {
  style?: StyleProp<TextStyle>;
  value: string;
};

export function AnimatedValueText({ style, value, ...props }: AnimatedValueTextProps) {
  const [displayValue, setDisplayValue] = useState(value);
  const previousValueRef = useRef(value);
  const progress = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    if (previousValueRef.current === value) {
      return;
    }

    const previousValue = previousValueRef.current;
    previousValueRef.current = value;
    setDisplayValue(value);

    if (!previousValue || !value) {
      progress.setValue(1);
      return;
    }

    progress.setValue(0);
    Animated.timing(progress, {
      duration: 220,
      toValue: 1,
      useNativeDriver: true,
    }).start();
  }, [progress, value]);

  return (
    <Animated.Text
      {...props}
      style={[
        style,
        {
          opacity: progress.interpolate({
            inputRange: [0, 1],
            outputRange: [0.35, 1],
          }),
          transform: [
            {
              translateY: progress.interpolate({
                inputRange: [0, 1],
                outputRange: [5, 0],
              }),
            },
          ],
        },
      ]}>
      {displayValue}
    </Animated.Text>
  );
}

export function StaticValueText({ style, value, ...props }: AnimatedValueTextProps) {
  return (
    <Text {...props} style={style}>
      {value}
    </Text>
  );
}
