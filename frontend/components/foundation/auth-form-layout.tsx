import type { PropsWithChildren } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { PageHeader } from '@/components/foundation/page-header';
import { Colors, Spacing } from '@/constants/theme';

type AuthFormLayoutProps = PropsWithChildren<{
  eyebrow: string;
  title: string;
  description: string;
}>;

export function AuthFormLayout({ children, description, eyebrow, title }: AuthFormLayoutProps) {
  const insets = useSafeAreaInsets();

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={styles.container}>
      <ScrollView
        alwaysBounceVertical={false}
        bounces={false}
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={[
          styles.content,
          {
            paddingBottom: Math.max(Spacing.xl, insets.bottom + Spacing.lg),
            paddingTop: Math.max(Spacing.xl, insets.top + Spacing.lg),
          },
        ]}
        keyboardDismissMode="interactive"
        keyboardShouldPersistTaps="handled"
        overScrollMode="never"
        showsVerticalScrollIndicator={false}
        style={styles.scroll}>
        <View style={styles.body}>
          <PageHeader eyebrow={eyebrow} title={title} description={description} />
          {children}
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  scroll: {
    flex: 1,
  },
  content: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingHorizontal: Spacing.xl,
  },
  body: {
    alignSelf: 'center',
    gap: Spacing.xl,
    maxWidth: 520,
    width: '100%',
  },
});
