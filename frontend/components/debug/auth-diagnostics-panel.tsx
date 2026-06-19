import { StyleSheet, Text, View } from 'react-native';

import { useAuthDiagnostics } from '@/api/diagnostics';
import { Colors, Radius, Spacing } from '@/constants/theme';

export function AuthDiagnosticsPanel() {
  const diagnostics = useAuthDiagnostics();

  if (!__DEV__) {
    return null;
  }

  const lastRequest = diagnostics.lastAuthRequest;
  const resultText = lastRequest
    ? getResultText(lastRequest)
    : 'No request sent for current input yet.';

  return (
    <View style={styles.container}>
      <Text selectable style={styles.eyebrow}>
        Dev auth diagnostics
      </Text>
      <Text selectable style={styles.row}>
        API base: {diagnostics.apiBaseUrl}
      </Text>
      <Text selectable style={styles.row}>
        Last auth request: {lastRequest ? `${lastRequest.method} ${lastRequest.path}` : 'none'}
      </Text>
      <Text selectable style={styles.row}>
        Last result: {resultText}
      </Text>
    </View>
  );
}

function getResultText(lastRequest: NonNullable<ReturnType<typeof useAuthDiagnostics>['lastAuthRequest']>) {
  if (lastRequest.phase === 'pending') {
    return 'pending';
  }

  if (lastRequest.phase === 'success') {
    return `HTTP ${lastRequest.status ?? 'ok'}`;
  }

  const status = lastRequest.status ? `HTTP ${lastRequest.status}` : 'no HTTP status';
  const code = lastRequest.errorCode ? ` ${lastRequest.errorCode}` : '';
  const message = lastRequest.message ? ` - ${lastRequest.message}` : '';
  return `${status}${code}${message}`;
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.xs,
    padding: Spacing.md,
  },
  eyebrow: {
    color: Colors.light.secondaryTint,
    fontSize: 12,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  row: {
    color: Colors.light.mutedText,
    fontSize: 12,
    lineHeight: 17,
  },
});
