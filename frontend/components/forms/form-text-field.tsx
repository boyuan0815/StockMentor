import { useState } from 'react';
import {
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
  type KeyboardTypeOptions,
  type TextInputProps,
} from 'react-native';

import { Colors, Radius, Spacing } from '@/constants/theme';

type FormTextFieldProps = Omit<
  TextInputProps,
  'keyboardType' | 'onChangeText' | 'secureTextEntry' | 'style' | 'value'
> & {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
  error?: string | null;
  helperText?: string;
  keyboardType?: KeyboardTypeOptions;
  secureTextEntry?: boolean;
};

export function FormTextField({
  autoCapitalize = 'none',
  autoCorrect = false,
  editable = true,
  error,
  helperText,
  keyboardType = 'default',
  label,
  onChangeText,
  secureTextEntry = false,
  value,
  ...props
}: FormTextFieldProps) {
  const [isPasswordVisible, setIsPasswordVisible] = useState(false);
  const hasError = Boolean(error);
  const effectiveSecureTextEntry = secureTextEntry && !isPasswordVisible;

  return (
    <View style={styles.container}>
      <Text selectable style={styles.label}>
        {label}
      </Text>
      <View style={[styles.inputShell, hasError ? styles.inputShellError : undefined]}>
        <TextInput
          accessibilityHint={error || helperText}
          accessibilityLabel={label}
          autoCapitalize={autoCapitalize}
          autoCorrect={autoCorrect}
          editable={editable}
          keyboardType={keyboardType}
          onChangeText={onChangeText}
          secureTextEntry={effectiveSecureTextEntry}
          style={[styles.input, secureTextEntry ? styles.inputWithToggle : undefined]}
          value={value}
          {...props}
        />
        {secureTextEntry ? (
          <Pressable
            accessibilityLabel={isPasswordVisible ? `Hide ${label}` : `Show ${label}`}
            accessibilityRole="button"
            disabled={!editable}
            hitSlop={8}
            onPress={() => setIsPasswordVisible((current) => !current)}
            style={({ pressed }) => [
              styles.toggle,
              pressed && editable ? styles.pressed : undefined,
              !editable ? styles.disabled : undefined,
            ]}>
            <Text style={styles.toggleText}>{isPasswordVisible ? 'Hide' : 'Show'}</Text>
          </Pressable>
        ) : null}
      </View>
      {hasError ? (
        <Text accessibilityLiveRegion="polite" selectable style={styles.error}>
          {error}
        </Text>
      ) : helperText ? (
        <Text selectable style={styles.helper}>
          {helperText}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: Spacing.sm,
  },
  label: {
    color: Colors.light.text,
    fontSize: 15,
    fontWeight: '700',
  },
  inputShell: {
    alignItems: 'center',
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    minHeight: 48,
  },
  inputShellError: {
    borderColor: Colors.light.destructive,
  },
  input: {
    color: Colors.light.text,
    flex: 1,
    fontSize: 16,
    minHeight: 48,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.md,
  },
  inputWithToggle: {
    paddingRight: Spacing.sm,
  },
  toggle: {
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 44,
    minWidth: 58,
    paddingHorizontal: Spacing.md,
  },
  toggleText: {
    color: Colors.light.tint,
    fontSize: 14,
    fontWeight: '800',
  },
  helper: {
    color: Colors.light.mutedText,
    fontSize: 13,
    lineHeight: 18,
  },
  error: {
    color: Colors.light.destructive,
    fontSize: 13,
    fontWeight: '700',
    lineHeight: 18,
  },
  pressed: {
    opacity: 0.72,
  },
  disabled: {
    opacity: 0.5,
  },
});
