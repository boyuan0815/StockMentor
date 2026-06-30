import { useCallback, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { adminApi } from '@/api/admin';
import {
  AdminButton,
  AdminConfirmModal,
  AdminDateInput,
  AdminInlineError,
  AdminKeyValueGrid,
  AdminMetric,
  AdminMetricGrid,
  AdminPage,
  AdminSection,
  formatAdminDate,
  formatAdminEnum,
  formatAdminNumber,
  useAdminRequest,
} from '@/components/admin/admin-ui';
import { IconSymbol } from '@/components/ui/icon-symbol';
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
  const [selectedSymbols, setSelectedSymbols] = useState<string[]>(supportedSymbols);
  const [date, setDate] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [validationMessage, setValidationMessage] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [result, setResult] = useState<BackfillResultDto | null>(null);

  const allSymbolsSelected = selectedSymbols.length === supportedSymbols.length;

  const requestPreview = useMemo(() => buildRequest(type, selectedSymbols, date, startDate, endDate), [
    date,
    endDate,
    selectedSymbols,
    startDate,
    type,
  ]);

  const tradingDateError = getTradingDateError(type, validationMessage);
  const startDateError = getStartDateError(type, validationMessage, startDate);
  const endDateError = getEndDateError(type, validationMessage, endDate);
  const symbolError = getSymbolError(validationMessage);

  const selectType = useCallback((nextType: AdminBackfillType) => {
    setType(nextType);
    setConfirmOpen(false);
    setErrorMessage(null);
    setValidationMessage(null);
    setResult(null);
  }, []);

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
    setValidationMessage(null);
    setResult(null);
  }, []);

  const toggleAllSymbols = useCallback(() => {
    setSelectedSymbols((current) => (current.length === supportedSymbols.length ? [] : [...supportedSymbols]));
    setValidationMessage(null);
    setResult(null);
  }, []);

  return (
    <AdminPage title="Stock maintenance">
      <AdminInlineError message={errorMessage} title="Backfill failed" />

      <AdminSection title="Backfill mode">
        <View style={styles.modeGrid}>
          {backfillTypes.map((option) => (
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ selected: type === option.value }}
              key={option.value}
              onPress={() => selectType(option.value)}
              style={(state) => [
                styles.modeCard,
                type === option.value ? styles.modeCardActive : undefined,
                type !== option.value && isHovered(state) ? styles.modeCardHovered : undefined,
                state.pressed ? styles.pressed : undefined,
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

      {type !== 'CLEANUP_1MIN' ? (
        <AdminSection title="Symbols">
          <View style={styles.symbolStack}>
            <View style={styles.symbolGrid}>
              {supportedSymbols.map((symbol) => {
                const selected = selectedSymbols.includes(symbol);
                return (
                  <Pressable
                    accessibilityRole="checkbox"
                    accessibilityState={{ checked: selected }}
                    key={symbol}
                    onPress={() => toggleSymbol(symbol)}
                    style={(state) => [
                      styles.symbolOption,
                      selected ? styles.symbolOptionActive : undefined,
                      isHovered(state) ? styles.symbolOptionHovered : undefined,
                      state.pressed ? styles.pressed : undefined,
                    ]}>
                    <View style={[styles.checkbox, selected ? styles.checkboxActive : undefined]}>
                      {selected ? <IconSymbol name="checkmark" color={Colors.light.surface} size={15} /> : null}
                    </View>
                    <Text style={styles.symbolText}>{symbol}</Text>
                  </Pressable>
                );
              })}
            </View>
            <Pressable
              accessibilityRole="checkbox"
              accessibilityState={{ checked: allSymbolsSelected }}
              onPress={toggleAllSymbols}
              style={(state) => [
                styles.allSymbolsRow,
                isHovered(state) ? styles.allSymbolsRowHovered : undefined,
                state.pressed ? styles.pressed : undefined,
              ]}>
              <View style={[styles.checkbox, styles.checkboxSmall, allSymbolsSelected ? styles.checkboxActive : undefined]}>
                {allSymbolsSelected ? <IconSymbol name="checkmark" color={Colors.light.surface} size={12} /> : null}
              </View>
              <Text style={styles.allSymbolsText}>All symbols</Text>
            </Pressable>
            <FieldError message={symbolError} />
          </View>
        </AdminSection>
      ) : null}

      {type !== 'CLEANUP_1MIN' ? (
        <AdminSection
          title={type === 'INTRADAY_DATE' ? 'Date' : type === 'DAILY_MISSING' ? 'Date range (optional)' : 'Date range'}>
          {type === 'INTRADAY_DATE' ? (
            <View style={styles.dateField}>
              <AdminDateInput
                accessibilityLabel="Trading date"
                onChangeText={(nextDate) => {
                  setDate(nextDate);
                  setValidationMessage(null);
                  setResult(null);
                }}
                placeholder="Trading date"
                value={date}
              />
              <FieldError message={tradingDateError} />
            </View>
          ) : (
            <View style={styles.dateRow}>
              <View style={styles.dateField}>
                <AdminDateInput
                  accessibilityLabel="Start date"
                  onChangeText={(nextDate) => {
                    setStartDate(nextDate);
                    setValidationMessage(null);
                    setResult(null);
                  }}
                  placeholder={type === 'DAILY_MISSING' ? 'Optional start date' : 'Start date'}
                  value={startDate}
                />
                <FieldError message={startDateError} />
              </View>
              <View style={styles.dateField}>
                <AdminDateInput
                  accessibilityLabel="End date"
                  onChangeText={(nextDate) => {
                    setEndDate(nextDate);
                    setValidationMessage(null);
                    setResult(null);
                  }}
                  placeholder={type === 'DAILY_MISSING' ? 'Optional end date' : 'End date'}
                  value={endDate}
                />
                <FieldError message={endDateError} />
              </View>
            </View>
          )}
        </AdminSection>
      ) : null}

      <AdminSection
        title="Run maintenance"
        action={
          <AdminButton
            disabled={pending}
            label={pending ? 'Running...' : 'Review and run'}
            onPress={openConfirmation}
            variant="primary"
          />
        }>
        <AdminKeyValueGrid
          rows={[
            ['Job type', formatAdminEnum(requestPreview.type)],
            ['Symbols', type === 'CLEANUP_1MIN' ? 'Not used' : requestPreview.symbols?.join(', ') ?? 'All symbols'],
            ['Date', requestPreview.date ?? 'Not used'],
            ['Start date', requestPreview.startDate ?? 'Not used'],
            ['End date', requestPreview.endDate ?? 'Not used'],
          ]}
        />
      </AdminSection>

      {result ? (
        <AdminSection title="Result summary" tone="success">
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
          <ResultMessages result={result} />
        </AdminSection>
      ) : null}

      <AdminConfirmModal
        visible={confirmOpen}
        title="Run stock maintenance?"
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
  selectedSymbols: string[],
  date: string,
  startDate: string,
  endDate: string,
): AdminBackfillRequest {
  if (type === 'CLEANUP_1MIN') {
    return { type };
  }

  const symbols = selectedSymbols.length === supportedSymbols.length ? undefined : selectedSymbols;
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
    return 'Choose at least one symbol, or select All symbols.';
  }

  if (request.type === 'INTRADAY_DATE') {
    if (!request.date) {
      return 'Intraday Date requires one trading date.';
    }
    return isDateOnly(request.date) ? null : 'Use YYYY-MM-DD for the trading date.';
  }

  if (request.type === 'DAILY_RANGE') {
    if (!request.startDate || !request.endDate) {
      return 'Daily Range requires a start date and end date.';
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
      return 'Daily Missing needs both dates, or no dates.';
    }
    if (!isDateOnly(request.startDate) || !isDateOnly(request.endDate)) {
      return 'Use YYYY-MM-DD for daily missing dates.';
    }
    return request.startDate <= request.endDate ? null : 'Start date must be on or before end date.';
  }

  return null;
}

function getSymbolError(message: string | null) {
  return message?.startsWith('Choose at least one symbol') ? message : null;
}

function getTradingDateError(type: AdminBackfillType, message: string | null) {
  if (type !== 'INTRADAY_DATE' || !message) {
    return null;
  }
  if (message.includes('trading date')) {
    return 'Choose a trading date.';
  }
  if (message.includes('YYYY-MM-DD')) {
    return 'Choose a valid trading date.';
  }
  return null;
}

function getStartDateError(type: AdminBackfillType, message: string | null, startDate: string) {
  if ((type !== 'DAILY_RANGE' && type !== 'DAILY_MISSING') || !message) {
    return null;
  }
  if (message.startsWith('Start date')) {
    return message;
  }
  if (message.includes('requires a start date') && !startDate) {
    return 'Start date is required.';
  }
  if (message.includes('needs both dates') && !startDate) {
    return 'Start date is required when an end date is used.';
  }
  if (message.includes('YYYY-MM-DD') && startDate && !isDateOnly(startDate)) {
    return 'Choose a valid start date.';
  }
  return null;
}

function getEndDateError(type: AdminBackfillType, message: string | null, endDate: string) {
  if ((type !== 'DAILY_RANGE' && type !== 'DAILY_MISSING') || !message) {
    return null;
  }
  if (message.includes('requires a start date') && !endDate) {
    return 'End date is required.';
  }
  if (message.includes('needs both dates') && !endDate) {
    return 'End date is required when a start date is used.';
  }
  if (message.includes('YYYY-MM-DD') && endDate && !isDateOnly(endDate)) {
    return 'Choose a valid end date.';
  }
  return null;
}

function formatBackfillMessage(message: string, result: BackfillResultDto) {
  const noRowsChanged = result.savedRows === 0 && result.skippedRows === 0 && result.deletedRows === 0;
  if (noRowsChanged && message.includes('max 2-symbol batches')) {
    return 'Twelve Data free-tier only allows data requests for up to 8 stocks per minute. Wait one minute, then try again.';
  }

  if (message.toLowerCase().includes('rate limit')) {
    return 'Twelve Data free-tier limit reached. Wait one minute, then try again.';
  }

  const lowerMessage = message.toLowerCase();
  if (lowerMessage.includes('failed') || lowerMessage.includes('error') || lowerMessage.includes('unable')) {
    return message;
  }

  return null;
}

function ResultMessages({ result }: { result: BackfillResultDto }) {
  const messages = result.messages
    .map((message) => formatBackfillMessage(message, result))
    .filter((message): message is string => Boolean(message));

  if (messages.length === 0) {
    return null;
  }

  return (
    <View style={styles.messageList}>
      {messages.map((message, index) => (
        <Text selectable key={`${index}-${message}`} style={styles.messageText}>
          {message}
        </Text>
      ))}
    </View>
  );
}

function FieldError({ message }: { message: string | null }) {
  return (
    <View style={styles.fieldErrorSlot}>
      {message ? (
        <Text selectable style={styles.fieldErrorText}>
          {message}
        </Text>
      ) : null}
    </View>
  );
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

function isHovered(state: unknown) {
  return Boolean((state as { hovered?: boolean }).hovered);
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
    backgroundColor: Colors.light.actionSecondarySoft,
    borderColor: Colors.light.actionSecondary,
  },
  modeCardHovered: {
    backgroundColor: '#F8FAFC',
  },
  modeTitle: {
    color: Colors.light.text,
    fontSize: 16,
    fontWeight: '800',
  },
  modeTextActive: {
    color: '#c07a1e',
  },
  modeDescription: {
    color: Colors.light.mutedText,
    fontSize: 13,
    lineHeight: 18,
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
  checkboxSmall: {
    height: 18,
    width: 18,
  },
  symbolGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  symbolStack: {
    gap: Spacing.sm,
  },
  allSymbolsRow: {
    alignItems: 'center',
    alignSelf: 'flex-start',
    flexDirection: 'row',
    gap: Spacing.xs,
    minHeight: 24,
  },
  allSymbolsRowHovered: {
    opacity: 0.72,
  },
  allSymbolsText: {
    color: Colors.light.text,
    fontSize: 13,
    fontWeight: '400',
  },
  symbolOption: {
    alignItems: 'center',
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    gap: Spacing.sm,
    minHeight: 40,
    minWidth: 120,
    paddingHorizontal: Spacing.sm,
  },
  symbolOptionActive: {
    backgroundColor: '#F8FAFC',
    borderColor: '#94A3B8',
  },
  symbolOptionHovered: {
    backgroundColor: '#F8FAFC',
  },
  symbolText: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '800',
  },
  dateRow: {
    flexDirection: 'row',
    gap: Spacing.sm,
    alignItems: 'flex-start',
  },
  dateField: {
    flex: 1,
    gap: Spacing.xs,
  },
  fieldErrorText: {
    color: Colors.light.destructive,
    fontSize: 13,
    fontWeight: '700',
    lineHeight: 18,
  },
  fieldErrorSlot: {
    minHeight: 20,  
    justifyContent: 'flex-start',
  },
  messageList: {
    gap: Spacing.sm,
  },
  messageText: {
    backgroundColor: '#FEF2F2',
    borderColor: '#FCA5A5',
    borderRadius: Radius.sm,
    borderWidth: 1,
    color: '#991B1B',
    fontSize: 14,
    fontWeight: '700',
    lineHeight: 20,
    padding: Spacing.md,
  },
  pressed: {
    opacity: 0.82,
  },
});
