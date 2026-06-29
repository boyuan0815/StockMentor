import { useCallback, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { adminApi } from '@/api/admin';
import { ActionButton } from '@/components/foundation/action-button';
import {
  AdminConfirmModal,
  AdminInlineError,
  AdminKeyValueGrid,
  AdminMetric,
  AdminMetricGrid,
  AdminMutedText,
  AdminPage,
  AdminSection,
  formatAdminDate,
  formatAdminEnum,
  formatAdminNumber,
  useAdminRequest,
} from '@/components/admin/admin-ui';
import { Colors, Radius, Spacing } from '@/constants/theme';
import type { AdminBackfillRequest, AdminBackfillType, BackfillResultDto } from '@/types/admin';

const supportedSymbols = ['NVDA', 'TSLA', 'AMD', 'AAPL', 'MSFT', 'GOOG', 'KO', 'JNJ'];
const backfillTypes: Array<{ description: string; label: string; value: AdminBackfillType }> = [
  {
    description: 'Fetch stored 1-minute rows for one trading date.',
    label: 'Intraday date',
    value: 'INTRADAY_DATE',
  },
  {
    description: 'Insert missing daily candles for a required date range.',
    label: 'Daily range',
    value: 'DAILY_RANGE',
  },
  {
    description: 'Repair missing daily rows, using backend defaults when dates are omitted.',
    label: 'Daily missing',
    value: 'DAILY_MISSING',
  },
  {
    description: 'Delete old 1-minute rows only when the backend cleanup rules allow it.',
    label: 'Cleanup 1-minute',
    value: 'CLEANUP_1MIN',
  },
];

export function AdminStockMaintenanceScreen() {
  const { adminToken, credentials, handleAdminError } = useAdminRequest();
  const [type, setType] = useState<AdminBackfillType>('INTRADAY_DATE');
  const [allSymbols, setAllSymbols] = useState(true);
  const [selectedSymbols, setSelectedSymbols] = useState<string[]>(['NVDA']);
  const [date, setDate] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [validationMessage, setValidationMessage] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [result, setResult] = useState<BackfillResultDto | null>(null);

  const requestPreview = useMemo(() => buildRequest(type, allSymbols, selectedSymbols, date, startDate, endDate), [
    allSymbols,
    date,
    endDate,
    selectedSymbols,
    startDate,
    type,
  ]);

  const openConfirmation = useCallback(() => {
    setErrorMessage(null);
    setResult(null);
    const validation = validateRequest(requestPreview);
    setValidationMessage(validation);
    if (!validation) {
      setConfirmOpen(true);
    }
  }, [requestPreview]);

  const runBackfill = useCallback(async () => {
    if (!credentials || !adminToken || pending) {
      return;
    }

    const validation = validateRequest(requestPreview);
    if (validation) {
      setValidationMessage(validation);
      setConfirmOpen(false);
      return;
    }

    setPending(true);
    setErrorMessage(null);
    setValidationMessage(null);

    try {
      const nextResult = await adminApi.runBackfill(credentials, adminToken, requestPreview);
      setResult(nextResult);
      setConfirmOpen(false);
    } catch (error) {
      setErrorMessage(handleAdminError(error));
    } finally {
      setPending(false);
    }
  }, [adminToken, credentials, handleAdminError, pending, requestPreview]);

  const toggleSymbol = useCallback((symbol: string) => {
    setSelectedSymbols((current) =>
      current.includes(symbol) ? current.filter((item) => item !== symbol) : [...current, symbol],
    );
  }, []);

  return (
    <AdminPage title="Stock maintenance">
      <AdminInlineError message={errorMessage || validationMessage} title={errorMessage ? 'Backfill failed' : 'Check form'} />

      <AdminSection title="Backfill mode">
        <View style={styles.modeGrid}>
          {backfillTypes.map((option) => (
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ selected: type === option.value }}
              key={option.value}
              onPress={() => setType(option.value)}
              style={({ pressed }) => [
                styles.modeCard,
                type === option.value ? styles.modeCardActive : undefined,
                pressed ? styles.pressed : undefined,
              ]}>
              <Text style={[styles.modeTitle, type === option.value ? styles.modeTextActive : undefined]}>
                {option.label}
              </Text>
              <Text style={[styles.modeDescription, type === option.value ? styles.modeDescriptionActive : undefined]}>
                {option.description}
              </Text>
            </Pressable>
          ))}
        </View>
      </AdminSection>

      <AdminSection title="Symbols">
        {type === 'CLEANUP_1MIN' ? (
          <AdminMutedText>Cleanup uses backend cleanup rules and ignores symbol/date form fields.</AdminMutedText>
        ) : (
          <View style={styles.formStack}>
            <Pressable
              accessibilityRole="checkbox"
              accessibilityState={{ checked: allSymbols }}
              onPress={() => setAllSymbols((current) => !current)}
              style={({ pressed }) => [styles.checkboxRow, pressed ? styles.pressed : undefined]}>
              <View style={[styles.checkbox, allSymbols ? styles.checkboxActive : undefined]}>
                <Text style={styles.checkboxMark}>{allSymbols ? 'x' : ''}</Text>
              </View>
              <Text style={styles.checkboxText}>All supported symbols</Text>
            </Pressable>
            {!allSymbols ? (
              <View style={styles.symbolGrid}>
                {supportedSymbols.map((symbol) => {
                  const selected = selectedSymbols.includes(symbol);
                  return (
                    <Pressable
                      accessibilityRole="checkbox"
                      accessibilityState={{ checked: selected }}
                      key={symbol}
                      onPress={() => toggleSymbol(symbol)}
                      style={({ pressed }) => [
                        styles.symbolChip,
                        selected ? styles.symbolChipActive : undefined,
                        pressed ? styles.pressed : undefined,
                      ]}>
                      <Text style={[styles.symbolText, selected ? styles.symbolTextActive : undefined]}>{symbol}</Text>
                    </Pressable>
                  );
                })}
              </View>
            ) : null}
          </View>
        )}
      </AdminSection>

      {type !== 'CLEANUP_1MIN' ? (
        <AdminSection title="Dates">
          {type === 'INTRADAY_DATE' ? (
            <TextInput
              accessibilityLabel="Trading date"
              autoCapitalize="none"
              onChangeText={setDate}
              placeholder="Trading date, YYYY-MM-DD"
              style={styles.input}
              value={date}
            />
          ) : (
            <View style={styles.dateRow}>
              <TextInput
                accessibilityLabel="Start date"
                autoCapitalize="none"
                onChangeText={setStartDate}
                placeholder={type === 'DAILY_MISSING' ? 'Start date optional, YYYY-MM-DD' : 'Start date, YYYY-MM-DD'}
                style={styles.input}
                value={startDate}
              />
              <TextInput
                accessibilityLabel="End date"
                autoCapitalize="none"
                onChangeText={setEndDate}
                placeholder={type === 'DAILY_MISSING' ? 'End date optional, YYYY-MM-DD' : 'End date, YYYY-MM-DD'}
                style={styles.input}
                value={endDate}
              />
            </View>
          )}
          {type === 'DAILY_MISSING' ? (
            <AdminMutedText>Leave both dates empty to use backend defaults, or enter both dates for a bounded repair.</AdminMutedText>
          ) : null}
        </AdminSection>
      ) : null}

      <AdminSection
        title="Run maintenance"
        action={
          <ActionButton
            disabled={pending}
            label={pending ? 'Running...' : 'Review and run'}
            onPress={openConfirmation}
            variant="primary"
          />
        }>
        <AdminKeyValueGrid
          rows={[
            ['Job type', formatAdminEnum(requestPreview.type)],
            ['Symbols', requestPreview.symbols?.join(', ') ?? 'All supported symbols'],
            ['Date', requestPreview.date ?? 'Not used'],
            ['Start date', requestPreview.startDate ?? 'Not used'],
            ['End date', requestPreview.endDate ?? 'Not used'],
          ]}
        />
      </AdminSection>

      {result ? (
        <AdminSection title="Result summary">
          <AdminMetricGrid>
            <AdminMetric label="Saved rows" tone="success" value={formatAdminNumber(result.savedRows)} />
            <AdminMetric label="Skipped rows" tone="warn" value={formatAdminNumber(result.skippedRows)} />
            <AdminMetric label="Deleted rows" tone="danger" value={formatAdminNumber(result.deletedRows)} />
          </AdminMetricGrid>
          <AdminKeyValueGrid
            rows={[
              ['Job type', formatAdminEnum(result.jobType)],
              ['Symbols', result.symbols.join(', ') || 'Not available'],
              ['Start date', result.startDate ?? 'Not available'],
              ['End date', result.endDate ?? 'Not available'],
            ]}
          />
          <View style={styles.messageList}>
            {result.messages.length > 0 ? (
              result.messages.map((message, index) => (
                <Text selectable key={`${index}-${message}`} style={styles.messageText}>
                  {message}
                </Text>
              ))
            ) : (
              <AdminMutedText>No backend messages returned.</AdminMutedText>
            )}
          </View>
        </AdminSection>
      ) : null}

      <AdminConfirmModal
        visible={confirmOpen}
        title="Run stock maintenance?"
        message="This calls the backend maintenance endpoint. It may fetch provider data or delete old 1-minute rows according to backend rules."
        confirmLabel="Run maintenance"
        pendingLabel="Running..."
        pending={pending}
        danger={type === 'CLEANUP_1MIN'}
        onCancel={() => (pending ? undefined : setConfirmOpen(false))}
        onConfirm={runBackfill}
      />
    </AdminPage>
  );
}

function buildRequest(
  type: AdminBackfillType,
  allSymbols: boolean,
  selectedSymbols: string[],
  date: string,
  startDate: string,
  endDate: string,
): AdminBackfillRequest {
  if (type === 'CLEANUP_1MIN') {
    return { type };
  }

  const symbols = allSymbols ? undefined : selectedSymbols;
  if (type === 'INTRADAY_DATE') {
    return { type, symbols, date: date.trim() || undefined };
  }

  return {
    type,
    symbols,
    startDate: startDate.trim() || undefined,
    endDate: endDate.trim() || undefined,
  };
}

function validateRequest(request: AdminBackfillRequest) {
  if (request.type !== 'CLEANUP_1MIN' && request.symbols && request.symbols.length === 0) {
    return 'Choose at least one symbol, or switch to all supported symbols.';
  }

  if (request.type === 'INTRADAY_DATE') {
    if (!request.date) {
      return 'INTRADAY_DATE requires a trading date.';
    }
    return isDateOnly(request.date) ? null : 'Use YYYY-MM-DD for the trading date.';
  }

  if (request.type === 'DAILY_RANGE') {
    if (!request.startDate || !request.endDate) {
      return 'DAILY_RANGE requires start date and end date.';
    }
    if (!isDateOnly(request.startDate) || !isDateOnly(request.endDate)) {
      return 'Use YYYY-MM-DD for daily range dates.';
    }
    return request.startDate <= request.endDate ? null : 'Start date must be on or before end date.';
  }

  if (request.type === 'DAILY_MISSING') {
    if (!request.startDate && !request.endDate) {
      return null;
    }
    if (!request.startDate || !request.endDate) {
      return 'DAILY_MISSING uses both dates or no dates.';
    }
    if (!isDateOnly(request.startDate) || !isDateOnly(request.endDate)) {
      return 'Use YYYY-MM-DD for daily missing dates.';
    }
    return request.startDate <= request.endDate ? null : 'Start date must be on or before end date.';
  }

  return null;
}

function isDateOnly(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }

  const [year, month, day] = value.split('-').map(Number);
  const parsedDate = new Date(Date.UTC(year, month - 1, day));
  return (
    parsedDate.getUTCFullYear() === year &&
    parsedDate.getUTCMonth() === month - 1 &&
    parsedDate.getUTCDate() === day
  );
}

const styles = StyleSheet.create({
  modeGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.md,
  },
  modeCard: {
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.xs,
    minHeight: 112,
    padding: Spacing.md,
    width: 220,
  },
  modeCardActive: {
    backgroundColor: '#052344',
    borderColor: '#052344',
  },
  modeTitle: {
    color: Colors.light.text,
    fontSize: 16,
    fontWeight: '800',
  },
  modeTextActive: {
    color: Colors.light.surface,
  },
  modeDescription: {
    color: Colors.light.mutedText,
    fontSize: 13,
    lineHeight: 18,
  },
  modeDescriptionActive: {
    color: '#DBEAFE',
  },
  formStack: {
    gap: Spacing.md,
  },
  checkboxRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  checkbox: {
    alignItems: 'center',
    borderColor: Colors.light.border,
    borderRadius: Radius.sm,
    borderWidth: 1,
    height: 22,
    justifyContent: 'center',
    width: 22,
  },
  checkboxActive: {
    backgroundColor: '#052344',
    borderColor: '#052344',
  },
  checkboxMark: {
    color: Colors.light.surface,
    fontSize: 14,
    fontWeight: '900',
  },
  checkboxText: {
    color: Colors.light.text,
    fontSize: 15,
    fontWeight: '700',
  },
  symbolGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  symbolChip: {
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    minHeight: 40,
    justifyContent: 'center',
    paddingHorizontal: Spacing.md,
  },
  symbolChipActive: {
    backgroundColor: Colors.light.softTeal,
    borderColor: Colors.light.secondaryTint,
  },
  symbolText: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '800',
  },
  symbolTextActive: {
    color: Colors.light.secondaryTint,
  },
  dateRow: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  input: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    color: Colors.light.text,
    flex: 1,
    fontSize: 15,
    minHeight: 44,
    paddingHorizontal: Spacing.md,
  },
  messageList: {
    gap: Spacing.sm,
  },
  messageText: {
    backgroundColor: '#F8FAFC',
    borderColor: Colors.light.border,
    borderRadius: Radius.sm,
    borderWidth: 1,
    color: Colors.light.text,
    fontSize: 14,
    lineHeight: 20,
    padding: Spacing.md,
  },
  pressed: {
    opacity: 0.82,
  },
});
