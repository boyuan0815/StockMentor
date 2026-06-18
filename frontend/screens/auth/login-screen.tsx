import { Link, type Href } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { AuthDiagnosticsPanel } from '@/components/debug/auth-diagnostics-panel';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { ActionButton } from '@/components/foundation/action-button';
import { PageHeader } from '@/components/foundation/page-header';
import { Screen } from '@/components/foundation/screen';
import { FormTextField } from '@/components/forms/form-text-field';
import { Colors, Spacing } from '@/constants/theme';
import { useAuthSession } from '@/providers/auth-session-provider';
import { getApiErrorMessage } from '@/utils/api-error-copy';

type LoginFieldErrors = {
  identity?: string;
  password?: string;
};

export function LoginScreen() {
  const { signIn } = useAuthSession();
  const [identity, setIdentity] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<LoginFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isPending, setIsPending] = useState(false);

  const handleIdentityChange = (value: string) => {
    setIdentity(value);
    setFormError(null);
    setFieldErrors((current) => ({ ...current, identity: undefined }));
  };

  const handlePasswordChange = (value: string) => {
    setPassword(value);
    setFormError(null);
    setFieldErrors((current) => ({ ...current, password: undefined }));
  };

  const handleSubmit = async () => {
    if (isPending) {
      return;
    }

    const nextErrors: LoginFieldErrors = {};
    const trimmedIdentity = identity.trim();

    if (!trimmedIdentity) {
      nextErrors.identity = 'Enter your email or username.';
    }
    if (!password) {
      nextErrors.password = 'Enter your password.';
    }

    setFieldErrors(nextErrors);
    setFormError(null);

    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    setIsPending(true);
    try {
      await signIn({
        username: trimmedIdentity,
        password,
      });
    } catch (error) {
      setFormError(getApiErrorMessage(error, 'login'));
    } finally {
      setIsPending(false);
    }
  };

  return (
    <Screen contentStyle={styles.content}>
      <PageHeader
        eyebrow="Welcome back"
        title="Sign in to StockMentor"
        description="Use your email or username. Your password stays only in this in-memory app session."
      />

      {formError ? <ErrorBanner title="Sign in failed" message={formError} /> : null}
      <AuthDiagnosticsPanel />

      <View style={styles.form}>
        <FormTextField
          autoComplete="username"
          editable={!isPending}
          error={fieldErrors.identity}
          keyboardType="email-address"
          label="Email or username"
          onChangeText={handleIdentityChange}
          placeholder="you@example.com"
          returnKeyType="next"
          textContentType="username"
          value={identity}
        />
        <FormTextField
          autoComplete="current-password"
          editable={!isPending}
          error={fieldErrors.password}
          label="Password"
          onChangeText={handlePasswordChange}
          onSubmitEditing={handleSubmit}
          placeholder="Your password"
          returnKeyType="go"
          secureTextEntry
          textContentType="password"
          value={password}
        />
        <ActionButton
          accessibilityLabel={isPending ? 'Signing in' : 'Sign in'}
          disabled={isPending}
          label={isPending ? 'Signing in...' : 'Sign in'}
          onPress={handleSubmit}
        />
      </View>

      <View style={styles.switcher}>
        <Text selectable style={styles.switcherText}>
          New to StockMentor?
        </Text>
        <Link href={'/register' as Href} asChild>
          <ActionButton disabled={isPending} label="Create an account" variant="secondary" />
        </Link>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: {
    gap: Spacing.xl,
  },
  form: {
    gap: Spacing.lg,
  },
  switcher: {
    gap: Spacing.sm,
  },
  switcherText: {
    color: Colors.light.mutedText,
    fontSize: 15,
    lineHeight: 22,
  },
});
