import { Pressable, StyleSheet, Text, type PressableProps } from 'react-native';

import { Colors, Radius, Spacing } from '@/constants/theme';

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';

type ActionButtonProps = PressableProps & {
  label: string;
  variant?: ButtonVariant;
};

export function ActionButton({
  accessibilityLabel,
  accessibilityState,
  disabled,
  label,
  style,
  variant = 'primary',
  ...props
}: ActionButtonProps) {
  const buttonStyle = getButtonStyle(variant);
  const labelStyle = getLabelStyle(variant);

  return (
    <Pressable
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityRole="button"
      accessibilityState={{ ...accessibilityState, disabled: Boolean(disabled) }}
      disabled={disabled}
      style={(state) => [
        styles.base,
        buttonStyle,
        state.pressed && !disabled ? styles.pressed : undefined,
        disabled ? styles.disabled : undefined,
        typeof style === 'function' ? style(state) : style,
      ]}
      {...props}>
      <Text style={[styles.label, labelStyle]}>{label}</Text>
    </Pressable>
  );
}

function getButtonStyle(variant: ButtonVariant) {
  switch (variant) {
    case 'secondary':
      return styles.secondary;
    case 'ghost':
      return styles.ghost;
    case 'danger':
      return styles.danger;
    case 'primary':
    default:
      return styles.primary;
  }
}

function getLabelStyle(variant: ButtonVariant) {
  switch (variant) {
    case 'secondary':
      return styles.secondaryLabel;
    case 'ghost':
      return styles.ghostLabel;
    case 'danger':
      return styles.dangerLabel;
    case 'primary':
    default:
      return styles.primaryLabel;
  }
}

const styles = StyleSheet.create({
  base: {
    alignItems: 'center',
    borderRadius: Radius.md,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: 44,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.md,
  },
  primary: {
    backgroundColor: Colors.light.tint,
    borderColor: Colors.light.tint,
  },
  secondary: {
    backgroundColor: Colors.light.softTeal,
    borderColor: Colors.light.secondaryTint,
  },
  ghost: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
  },
  danger: {
    backgroundColor: Colors.light.destructive,
    borderColor: Colors.light.destructive,
  },
  disabled: {
    opacity: 0.46,
  },
  pressed: {
    opacity: 0.86,
  },
  label: {
    fontSize: 15,
    fontWeight: '700',
  },
  primaryLabel: {
    color: Colors.light.surface,
  },
  secondaryLabel: {
    color: Colors.light.secondaryTint,
  },
  ghostLabel: {
    color: Colors.light.text,
  },
  dangerLabel: {
    color: Colors.light.surface,
  },
});
