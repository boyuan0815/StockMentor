import { Link, type Href } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { normalizeUnknownApiError } from '@/api/errors';
import { ActionButton } from '@/components/foundation/action-button';
import { AuthFormLayout } from '@/components/foundation/auth-form-layout';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { FormTextField } from '@/components/forms/form-text-field';
import { Colors, Spacing } from '@/constants/theme';
import { useAuthSession } from '@/providers/auth-session-provider';
import { getApiErrorMessage } from '@/utils/api-error-copy';

type RegisterFieldErrors = {
  email?: string;
  username?: string;
  password?: string;
  confirmPassword?: string;
};

export function RegisterScreen() {
  const { register } = useAuthSession();
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<RegisterFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [hasAttemptedSubmit, setHasAttemptedSubmit] = useState(false);
  const [isPending, setIsPending] = useState(false);

  const handleEmailChange = (value: string) => {
    setEmail(value);
    setFormError(null);
    if (hasAttemptedSubmit) {
      const nextValidation = validateRegisterFields({
        confirmPassword,
        email: value.trim().toLowerCase(),
        password,
        username: username.trim(),
      });
      setFieldErrors((current) => ({ ...current, email: nextValidation.email }));
    }
  };

  const handleUsernameChange = (value: string) => {
    setUsername(value);
    setFormError(null);
    if (hasAttemptedSubmit) {
      const nextValidation = validateRegisterFields({
        confirmPassword,
        email: email.trim().toLowerCase(),
        password,
        username: value.trim(),
      });
      setFieldErrors((current) => ({ ...current, username: nextValidation.username }));
    }
  };

  const handlePasswordChange = (value: string) => {
    setPassword(value);
    setFormError(null);
    if (hasAttemptedSubmit) {
      const nextValidation = validateRegisterFields({
        confirmPassword,
        email: email.trim().toLowerCase(),
        password: value,
        username: username.trim(),
      });
      setFieldErrors((current) => ({
        ...current,
        confirmPassword: nextValidation.confirmPassword,
        password: nextValidation.password,
      }));
    }
  };

  const handleConfirmPasswordChange = (value: string) => {
    setConfirmPassword(value);
    setFormError(null);
    if (hasAttemptedSubmit) {
      const nextValidation = validateRegisterFields({
        confirmPassword: value,
        email: email.trim().toLowerCase(),
        password,
        username: username.trim(),
      });
      setFieldErrors((current) => ({
        ...current,
        confirmPassword: nextValidation.confirmPassword,
      }));
    }
  };

  const handleSubmit = async () => {
    if (isPending) {
      return;
    }

    const trimmedEmail = email.trim().toLowerCase();
    const trimmedUsername = username.trim();
    const nextErrors = validateRegisterFields({
      confirmPassword,
      email: trimmedEmail,
      password,
      username: trimmedUsername,
    });
    const hasUnresolvedFieldErrors = Object.values(fieldErrors).some(Boolean);

    setHasAttemptedSubmit(true);
    setFormError(null);

    if (Object.keys(nextErrors).length > 0) {
      setFieldErrors((current) => ({ ...current, ...nextErrors }));
      return;
    }

    if (hasUnresolvedFieldErrors) {
      setFormError('Update the highlighted account details and try again.');
      return;
    }

    setFieldErrors(nextErrors);

    setIsPending(true);
    try {
      await register({
        email: trimmedEmail,
        username: trimmedUsername,
        password,
        confirmPassword,
      });
    } catch (error) {
      const apiError = normalizeUnknownApiError(error);
      if (apiError.status === 409 && apiError.fields) {
        const conflictErrors: RegisterFieldErrors = {};
        if (apiError.fields.email) {
          conflictErrors.email = apiError.fields.email;
        }
        if (apiError.fields.username) {
          conflictErrors.username = apiError.fields.username;
        }

        if (Object.keys(conflictErrors).length > 0) {
          setFieldErrors((current) => ({ ...current, ...conflictErrors }));
          setFormError('Update the highlighted account details and try again.');
        } else {
          setFormError(getApiErrorMessage(error, 'register'));
        }
      } else {
        setFormError(getApiErrorMessage(error, 'register'));
      }
    } finally {
      setIsPending(false);
    }
  };

  const currentValidationErrors = hasAttemptedSubmit
    ? validateRegisterFields({
        confirmPassword,
        email: email.trim().toLowerCase(),
        password,
        username: username.trim(),
      })
    : {};
  const hasCurrentValidationErrors = Object.keys(currentValidationErrors).length > 0;
  const hasDisplayedFieldErrors = Object.values(fieldErrors).some(Boolean);

  return (
    <AuthFormLayout
      eyebrow="Start calmly,"
      title="Create account">

      {formError ? <ErrorBanner title="Account was not created" message={formError} /> : null}

      <View style={styles.form}>
        <FormTextField
          autoComplete="email"
          editable={!isPending}
          error={fieldErrors.email}
          keyboardType="email-address"
          label="Email"
          onChangeText={handleEmailChange}
          placeholder="you@example.com"
          returnKeyType="next"
          textContentType="emailAddress"
          value={email}
        />
        <FormTextField
          autoComplete="username-new"
          editable={!isPending}
          error={fieldErrors.username}
          helperText="Use 3 to 30 letters, numbers, dots, underscores, or hyphens."
          label="Username"
          onChangeText={handleUsernameChange}
          placeholder="your_username"
          returnKeyType="next"
          textContentType="username"
          value={username}
        />
        <FormTextField
          autoComplete="new-password"
          editable={!isPending}
          error={fieldErrors.password}
          helperText="Use 8 to 72 characters."
          label="Password"
          onChangeText={handlePasswordChange}
          placeholder="Create a password"
          returnKeyType="next"
          secureTextEntry
          textContentType="newPassword"
          value={password}
        />
        <FormTextField
          autoComplete="new-password"
          editable={!isPending}
          error={fieldErrors.confirmPassword}
          label="Confirm password"
          onChangeText={handleConfirmPasswordChange}
          onSubmitEditing={handleSubmit}
          placeholder="Repeat the password"
          returnKeyType="go"
          secureTextEntry
          textContentType="newPassword"
          value={confirmPassword}
        />
        <ActionButton
          accessibilityLabel={isPending ? 'Creating account' : 'Create account'}
          disabled={isPending || hasCurrentValidationErrors || hasDisplayedFieldErrors}
          label={isPending ? 'Creating account...' : 'Create account'}
          onPress={handleSubmit}
        />
      </View>

      <View style={styles.switcher}>
        <Text selectable style={styles.switcherText}>
          Already have an account?
        </Text>
        <Link href={'/login' as Href} asChild>
          <ActionButton disabled={isPending} label="Sign in" variant="secondary" />
        </Link>
      </View>
    </AuthFormLayout>
  );
}

function validateRegisterFields({
  confirmPassword,
  email,
  password,
  username,
}: {
  confirmPassword: string;
  email: string;
  password: string;
  username: string;
}) {
  const errors: RegisterFieldErrors = {};

  if (!email) {
    errors.email = 'Enter your email address.';
  } else if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
    errors.email = 'Enter a valid email address.';
  }

  if (!username) {
    errors.username = 'Choose a username.';
  } else if (username.length < 3 || username.length > 30) {
    errors.username = 'Username must be 3 to 30 characters.';
  } else if (!/^[A-Za-z0-9._-]+$/.test(username)) {
    errors.username = 'Username can use letters, numbers, dots, underscores, and hyphens.';
  }

  if (!password) {
    errors.password = 'Create a password.';
  } else if (password.length < 8 || password.length > 72) {
    errors.password = 'Password must be 8 to 72 characters.';
  }

  if (!confirmPassword) {
    errors.confirmPassword = 'Confirm your password.';
  } else if (password !== confirmPassword) {
    errors.confirmPassword = 'The passwords do not match.';
  }

  return errors;
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
});
