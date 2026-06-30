import { Link, type Href } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { ActionButton } from '@/components/foundation/action-button';
import { AuthFormLayout } from '@/components/foundation/auth-form-layout';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { FormTextField } from '@/components/forms/form-text-field';
import { Colors, Spacing } from '@/constants/theme';
import { useAuthSession } from '@/providers/auth-session-provider';
import { getApiErrorMessage } from '@/utils/api-error-copy';
import { normalizeUnknownApiError } from '@/api/errors';

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
  const [hasAttemptedSubmit, setHasAttemptedSubmit] = useState(false);
  const [isPending, setIsPending] = useState(false);
  const [loginAuthError, setLoginAuthError] = useState<{
    kind: 'email' | 'username';
    value: string;
  } | null>(null);

  const handleIdentityChange = (value: string) => {
    setIdentity(value);
    setFormError(null);
    setLoginAuthError(null);
    if (hasAttemptedSubmit) {
      setFieldErrors(validateLoginFields({ identity: value, password }));
    }
  };

  const handlePasswordChange = (value: string) => {
    setPassword(value);
    setFormError(null);
    setLoginAuthError(null);
    if (hasAttemptedSubmit) {
      setFieldErrors(validateLoginFields({ identity, password: value }));
    }
  };

  const handleSubmit = async () => {
    if (isPending) {
      return;
    }

    const trimmedIdentity = identity.trim();
    const nextErrors = validateLoginFields({ identity, password });

    setHasAttemptedSubmit(true);
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
      const apiError = normalizeUnknownApiError(error);

      if (apiError.status === 401 && trimmedIdentity) {
        setLoginAuthError({
          kind: trimmedIdentity.includes('@') ? 'email' : 'username',
          value: trimmedIdentity,
        });
        setFormError(null);
      } else {
        setLoginAuthError(null);
        setFormError(getApiErrorMessage(error, 'login', { loginIdentifier: trimmedIdentity }));
      }
    } finally {
      setIsPending(false);
    }
  };

  const currentValidationErrors = hasAttemptedSubmit
    ? validateLoginFields({ identity, password })
    : {};
  const hasCurrentValidationErrors = Object.keys(currentValidationErrors).length > 0;
  const visibleFieldErrors = hasAttemptedSubmit ? currentValidationErrors : fieldErrors;

  return (
    <AuthFormLayout
      eyebrow="Welcome back,"
      title="Sign in">

      {loginAuthError ? (
        <ErrorBanner title="Sign in failed">
          <Text selectable style={styles.errorMessage}>
            The {loginAuthError.kind}{' '}
            <Text style={styles.errorEmphasis}>{loginAuthError.value}</Text>{' '}
            or password was not accepted.
          </Text>
        </ErrorBanner>
      ) : formError ? (
        <ErrorBanner title="Sign in failed" message={formError} />
      ) : null}

      <View style={styles.form}>
        <FormTextField
          autoComplete="username"
          editable={!isPending}
          error={visibleFieldErrors.identity}
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
          error={visibleFieldErrors.password}
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
          disabled={isPending || hasCurrentValidationErrors}
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
    </AuthFormLayout>
  );
}

function validateLoginFields({ identity, password }: { identity: string; password: string }) {
  const errors: LoginFieldErrors = {};
  const trimmedIdentity = identity.trim();

  if (!trimmedIdentity) {
    errors.identity = 'Enter your email or username.';
  } else if (trimmedIdentity.includes('@') && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(trimmedIdentity)) {
    errors.identity = 'Enter a valid email address.';
  } else if (!trimmedIdentity.includes('@') && !isValidUsername(trimmedIdentity)) {
    errors.identity = 'Use 3 to 30 letters, numbers, dots, underscores, or hyphens.';
  }

  if (!password) {
    errors.password = 'Enter your password.';
  } else if (password.length < 8 || password.length > 72) {
    errors.password = 'Password must be 8 to 72 characters.';
  }

  return errors;
}

function isValidUsername(value: string) {
  return value.length >= 3 && value.length <= 30 && /^[A-Za-z0-9._-]+$/.test(value);
}

const styles = StyleSheet.create({
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
  errorMessage: {
    color: Colors.light.text,
    fontSize: 15,
    lineHeight: 22,
  },
  errorEmphasis: {
    fontWeight: '800',
  },
});
