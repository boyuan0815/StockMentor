import { Link, type Href } from 'expo-router';
import { Image } from 'expo-image';
import { useEffect, useRef } from 'react';
import { Animated, StyleSheet, Text, View } from 'react-native';

import { ActionButton } from '@/components/foundation/action-button';
import { PageHeader } from '@/components/foundation/page-header';
import { Screen } from '@/components/foundation/screen';
import { Colors, Spacing } from '@/constants/theme';

export function WelcomeScreen() {
  const introAnim = useRef(new Animated.Value(0)).current;
  const introStyle = {
    opacity: introAnim,
    transform: [
      {
        translateY: introAnim.interpolate({
          inputRange: [0, 1],
          outputRange: [28, 0],
        }),
      },
    ],
  };

  useEffect(() => {
    Animated.timing(introAnim, {
      duration: 1200,
      toValue: 1,
      useNativeDriver: true,
    }).start();
  }, [introAnim]);

  return (
    <Screen scroll="auto" contentStyle={styles.content}>
      <Animated.View style={[styles.intro, introStyle]}>
        <View style={styles.brandBlock}>
          <Image
            accessibilityLabel="StockMentor"
            contentFit="contain"
            source={require('../assets/images/stockmentor-icon-transparent-1024.png')}
            style={styles.brandIcon}
          />
          <Text selectable style={styles.brandName}>
            StockMentor
          </Text>
        </View>
        <PageHeader
          title="Learn the market without the rush"
        />

        <View style={styles.actions}>
          <Link href={'/login' as Href} asChild>
            <ActionButton label="Sign in" />
          </Link>
          <Link href={'/register' as Href} asChild>
            <ActionButton label="Create account" variant="secondary" />
          </Link>
        </View>
      </Animated.View>
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
  actions: {
    gap: Spacing.sm,
  },
  brandBlock: {
    alignItems: 'center',
    gap: 4,
    marginBottom: Spacing.md,
  },
  brandIcon: {
    height: 62,
    width: 62,
  },
  brandName: {
    color: Colors.light.brandNavy,
    fontSize: 22,
    fontWeight: '800',
    lineHeight: 22,
  },
  intro: {
    gap: Spacing.xl,
    width: '100%',
  },
});
