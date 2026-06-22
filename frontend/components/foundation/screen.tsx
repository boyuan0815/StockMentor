import { useState, type PropsWithChildren } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  RefreshControl,
  ScrollView,
  StyleSheet,
  View,
  type StyleProp,
  type ViewStyle,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Colors, Spacing } from '@/constants/theme';

type ScreenProps = PropsWithChildren<{
  scroll?: boolean | 'auto';
  contentStyle?: StyleProp<ViewStyle>;
  keyboardAware?: boolean;
  onRefresh?: () => void;
  refreshing?: boolean;
}>;

export function Screen({
  children,
  contentStyle,
  keyboardAware = false,
  onRefresh,
  refreshing = false,
  scroll = true,
}: ScreenProps) {
  const insets = useSafeAreaInsets();
  const [containerHeight, setContainerHeight] = useState(0);
  const [contentHeight, setContentHeight] = useState(0);
  const shouldAutoScroll =
    scroll === 'auto' && containerHeight > 0 && contentHeight > containerHeight + 1;
  const safeAreaContentStyle = {
    paddingBottom: Math.max(Spacing.xl, insets.bottom + Spacing.md),
    paddingTop: Math.max(Spacing.xl, insets.top + Spacing.md),
  };

  const staticContent = (
    <View
      onLayout={(event) => setContentHeight(event.nativeEvent.layout.height)}
      style={[styles.content, safeAreaContentStyle, contentStyle]}>
      {children}
    </View>
  );

  const content = scroll === true || shouldAutoScroll ? (
    <ScrollView
      alwaysBounceVertical={Boolean(onRefresh)}
      bounces={Boolean(onRefresh)}
      contentContainerStyle={[styles.content, safeAreaContentStyle, contentStyle]}
      contentInsetAdjustmentBehavior="automatic"
      keyboardShouldPersistTaps="handled"
      onContentSizeChange={(_, height) => setContentHeight(height)}
      onLayout={(event) => setContainerHeight(event.nativeEvent.layout.height)}
      overScrollMode="never"
      refreshControl={
        onRefresh ? <RefreshControl onRefresh={onRefresh} refreshing={refreshing} /> : undefined
      }
      showsVerticalScrollIndicator={scroll !== 'auto'}
      style={styles.container}>
      {children}
    </ScrollView>
  ) : (
    <View
      onLayout={(event) => setContainerHeight(event.nativeEvent.layout.height)}
      style={styles.container}>
      {staticContent}
    </View>
  );

  if (!keyboardAware) {
    return content;
  }

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={styles.container}>
      {content}
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.light.background,
  },
  content: {
    flexGrow: 1,
    gap: Spacing.xl,
    paddingBottom: Spacing.xxl,
    paddingHorizontal: Spacing.xl,
    paddingTop: Spacing.xxl,
  },
});
