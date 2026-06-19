import { StyleSheet, Text, View } from 'react-native';

import { ActionButton } from '@/components/foundation/action-button';
import { Colors, Radius, Spacing } from '@/constants/theme';

type ConfirmationPanelProps = {
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel?: string;
  pending?: boolean;
  pendingLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
};

export function ConfirmationPanel({
  cancelLabel = 'Cancel',
  confirmLabel,
  message,
  onCancel,
  onConfirm,
  pending = false,
  pendingLabel = 'Working...',
  title,
}: ConfirmationPanelProps) {
  return (
    <View accessibilityLiveRegion={pending ? 'polite' : 'none'} style={styles.container}>
      <View style={styles.copy}>
        <Text selectable style={styles.title}>
          {title}
        </Text>
        <Text selectable style={styles.message}>
          {message}
        </Text>
      </View>
      <View style={styles.actions}>
        <ActionButton
          disabled={pending}
          label={pending ? pendingLabel : confirmLabel}
          onPress={onConfirm}
          variant="secondary"
        />
        <ActionButton disabled={pending} label={cancelLabel} onPress={onCancel} variant="ghost" />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.lg,
    padding: Spacing.lg,
  },
  copy: {
    gap: Spacing.sm,
  },
  title: {
    color: Colors.light.text,
    fontSize: 17,
    fontWeight: '800',
  },
  message: {
    color: Colors.light.mutedText,
    fontSize: 15,
    lineHeight: 22,
  },
  actions: {
    gap: Spacing.sm,
  },
});
