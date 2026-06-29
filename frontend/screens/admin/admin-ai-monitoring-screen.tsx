import { router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { adminApi } from '@/api/admin';
import { ActionButton } from '@/components/foundation/action-button';
import {
  AdminConfirmModal,
  AdminDataTable,
  AdminFieldText,
  AdminInlineError,
  AdminMetric,
  AdminMetricGrid,
  AdminMutedText,
  AdminPage,
  AdminSection,
  AdminStatusPill,
  AdminTabs,
  formatAdminDate,
  formatAdminEnum,
  formatAdminNumber,
  getStatusTone,
  useAdminRequest,
} from '@/components/admin/admin-ui';
import { Colors, Radius, Spacing } from '@/constants/theme';
import type {
  AdminAiBatchFilters,
  AdminAiFailureFilters,
  AdminAiRefreshJobFilters,
  AdminAiRefreshJobStatus,
  AdminAiRefreshTriggeredBy,
  AdminAiSuggestionBatchRowResponse,
  AdminAiSuggestionBatchStatus,
  AdminAiSuggestionGroupedCountResponse,
  AdminAiSuggestionTriggerReason,
  AdminAiSuggestionUsageSummaryResponse,
  AdminPageResponse,
  AiSuggestionRefreshJobResponse,
} from '@/types/admin';

type AdminAiTab = 'usage' | 'batches' | 'failures' | 'jobs';

const PAGE_SIZE = 20;
const tabs: Array<{ label: string; value: AdminAiTab }> = [
  { label: 'Usage', value: 'usage' },
  { label: 'Batches', value: 'batches' },
  { label: 'Failures', value: 'failures' },
  { label: 'Refresh jobs', value: 'jobs' },
];
const batchStatuses: Array<{ label: string; value: AdminAiSuggestionBatchStatus | '' }> = [
  { label: 'All statuses', value: '' },
  { label: 'Success', value: 'SUCCESS' },
  { label: 'Failed', value: 'FAILED' },
  { label: 'Fallback cached', value: 'FALLBACK_CACHED' },
  { label: 'Fallback rules', value: 'FALLBACK_RULE_BASED' },
];
const triggerReasons: Array<{ label: string; value: AdminAiSuggestionTriggerReason | '' }> = [
  { label: 'All triggers', value: '' },
  { label: 'Onboarding', value: 'ONBOARDING_COMPLETED' },
  { label: 'Retake', value: 'RETAKE_QUIZ' },
  { label: 'Manual', value: 'MANUAL_REFRESH' },
  { label: 'Scheduled', value: 'SCHEDULED_REFRESH' },
  { label: 'No active', value: 'NO_ACTIVE_SUGGESTION' },
];
const jobStatuses: Array<{ label: string; value: AdminAiRefreshJobStatus | '' }> = [
  { label: 'All statuses', value: '' },
  { label: 'Running', value: 'RUNNING' },
  { label: 'Success', value: 'SUCCESS' },
  { label: 'Partial', value: 'PARTIAL_SUCCESS' },
  { label: 'Failed', value: 'FAILED' },
];
const triggeredByOptions: Array<{ label: string; value: AdminAiRefreshTriggeredBy | '' }> = [
  { label: 'All sources', value: '' },
  { label: 'Scheduled', value: 'SCHEDULED' },
  { label: 'Admin manual', value: 'ADMIN_MANUAL' },
];

export function AdminAiMonitoringScreen() {
  const { adminToken, credentials, handleAdminError } = useAdminRequest();
  const [activeTab, setActiveTab] = useState<AdminAiTab>('usage');
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [confirmRefresh, setConfirmRefresh] = useState(false);
  const [refreshPending, setRefreshPending] = useState(false);

  const [usageFilters, setUsageFilters] = useState({ from: '', to: '' });
  const [batchFilters, setBatchFilters] = useState<AdminAiBatchFilters>({ page: 0, size: PAGE_SIZE });
  const [failureFilters, setFailureFilters] = useState<AdminAiFailureFilters>({ page: 0, size: PAGE_SIZE });
  const [jobFilters, setJobFilters] = useState<AdminAiRefreshJobFilters>({ page: 0, size: PAGE_SIZE });

  const [usage, setUsage] = useState<AdminAiSuggestionUsageSummaryResponse | null>(null);
  const [batches, setBatches] = useState<AdminPageResponse<AdminAiSuggestionBatchRowResponse> | null>(null);
  const [failures, setFailures] = useState<AdminPageResponse<AdminAiSuggestionBatchRowResponse> | null>(null);
  const [jobs, setJobs] = useState<AdminPageResponse<AiSuggestionRefreshJobResponse> | null>(null);

  const loadActiveTab = useCallback(async () => {
    if (!credentials || !adminToken) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setErrorMessage(null);

    try {
      if (activeTab === 'usage') {
        setUsage(await adminApi.getUsageSummary(credentials, adminToken, usageFilters));
      } else if (activeTab === 'batches') {
        setBatches(await adminApi.listBatches(credentials, adminToken, batchFilters));
      } else if (activeTab === 'failures') {
        setFailures(await adminApi.listFailures(credentials, adminToken, failureFilters));
      } else {
        setJobs(await adminApi.listRefreshJobs(credentials, adminToken, jobFilters));
      }
    } catch (error) {
      setErrorMessage(handleAdminError(error));
    } finally {
      setLoading(false);
    }
  }, [
    activeTab,
    adminToken,
    batchFilters,
    credentials,
    failureFilters,
    handleAdminError,
    jobFilters,
    usageFilters,
  ]);

  useEffect(() => {
    void loadActiveTab();
  }, [loadActiveTab]);

  const runScheduledRefresh = useCallback(async () => {
    if (!credentials || !adminToken || refreshPending) {
      return;
    }

    setRefreshPending(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const job = await adminApi.runScheduledRefresh(credentials, adminToken);
      setSuccessMessage(`Refresh job #${job.jobId} started with status ${formatAdminEnum(job.status)}.`);
      setConfirmRefresh(false);
      setActiveTab('jobs');
      setJobFilters((current) => ({ ...current, page: 0 }));
    } catch (error) {
      setErrorMessage(handleAdminError(error));
    } finally {
      setRefreshPending(false);
    }
  }, [adminToken, credentials, handleAdminError, refreshPending]);

  return (
    <AdminPage
      title="AI suggestion monitoring"
      actions={
        <View style={styles.actions}>
          <ActionButton label="Refresh tab" onPress={loadActiveTab} variant="secondary" />
          <ActionButton
            disabled={refreshPending}
            label={refreshPending ? 'Starting...' : 'Run scheduled refresh'}
            onPress={() => setConfirmRefresh(true)}
            variant="primary"
          />
        </View>
      }>
      <AdminTabs activeTab={activeTab} onSelect={setActiveTab} tabs={tabs} />
      <AdminInlineError message={errorMessage} />
      {successMessage ? <AdminMutedText>{successMessage}</AdminMutedText> : null}

      {activeTab === 'usage' ? (
        <UsagePanel
          filters={usageFilters}
          loading={loading}
          onChangeFilters={setUsageFilters}
          usage={usage}
        />
      ) : null}
      {activeTab === 'batches' ? (
        <BatchesPanel
          filters={batchFilters}
          loading={loading}
          onChangeFilters={setBatchFilters}
          response={batches}
        />
      ) : null}
      {activeTab === 'failures' ? (
        <FailuresPanel
          filters={failureFilters}
          loading={loading}
          onChangeFilters={setFailureFilters}
          response={failures}
        />
      ) : null}
      {activeTab === 'jobs' ? (
        <RefreshJobsPanel
          filters={jobFilters}
          loading={loading}
          onChangeFilters={setJobFilters}
          response={jobs}
        />
      ) : null}

      <AdminConfirmModal
        visible={confirmRefresh}
        title="Run scheduled refresh now?"
        message="This asks the backend to run the scheduled AI suggestion refresh workflow. It may call the backend AI provider integration and can take time for larger user sets."
        confirmLabel="Run refresh"
        pendingLabel="Starting..."
        pending={refreshPending}
        onCancel={() => (refreshPending ? undefined : setConfirmRefresh(false))}
        onConfirm={runScheduledRefresh}
      />
    </AdminPage>
  );
}

function UsagePanel({
  filters,
  loading,
  onChangeFilters,
  usage,
}: {
  filters: { from: string; to: string };
  loading: boolean;
  onChangeFilters: (filters: { from: string; to: string }) => void;
  usage: AdminAiSuggestionUsageSummaryResponse | null;
}) {
  return (
    <>
      <DateFilters
        from={filters.from}
        onChangeFrom={(from) => onChangeFilters({ ...filters, from })}
        onChangeTo={(to) => onChangeFilters({ ...filters, to })}
        to={filters.to}
      />
      <AdminSection title="Usage summary">
        <AdminMetricGrid>
          <AdminMetric label="Total batches" value={formatAdminNumber(usage?.totalBatches)} />
          <AdminMetric label="Successful" tone="success" value={formatAdminNumber(usage?.successCount)} />
          <AdminMetric label="Failed" tone="danger" value={formatAdminNumber(usage?.failedCount)} />
          <AdminMetric label="Cached fallback" tone="warn" value={formatAdminNumber(usage?.fallbackCachedCount)} />
          <AdminMetric label="Rule fallback" tone="warn" value={formatAdminNumber(usage?.fallbackRuleBasedCount)} />
          <AdminMetric label="Total tokens" value={formatAdminNumber(usage?.totalTokens)} />
        </AdminMetricGrid>
      </AdminSection>
      <AdminSection title="Grouped counts">
        <AdminDataTable
          loading={loading}
          rows={[
            ...(usage?.groupedByStatus ?? []).map((row) => ({ ...row, group: 'Status' })),
            ...(usage?.groupedByTriggerReason ?? []).map((row) => ({ ...row, group: 'Trigger' })),
          ]}
          keyExtractor={(item) => `${item.group}-${item.key}`}
          emptyTitle="No usage data"
          emptyDescription="Usage records will appear after AI suggestion batches exist."
          columns={groupedCountColumns}
        />
      </AdminSection>
    </>
  );
}

function BatchesPanel({
  filters,
  loading,
  onChangeFilters,
  response,
}: {
  filters: AdminAiBatchFilters;
  loading: boolean;
  onChangeFilters: (filters: AdminAiBatchFilters) => void;
  response: AdminPageResponse<AdminAiSuggestionBatchRowResponse> | null;
}) {
  return (
    <>
      <DateFilters
        from={filters.from ?? ''}
        onChangeFrom={(from) => onChangeFilters({ ...filters, from, page: 0 })}
        onChangeTo={(to) => onChangeFilters({ ...filters, to, page: 0 })}
        to={filters.to ?? ''}
      />
      <AdminSection title="Batch filters">
        <View style={styles.filterStack}>
          <TextInput
            accessibilityLabel="Filter batches by user email"
            autoCapitalize="none"
            autoCorrect={false}
            onChangeText={(email) => onChangeFilters({ ...filters, email, page: 0 })}
            placeholder="User email"
            style={styles.input}
            value={filters.email ?? ''}
          />
          <ChipGroup
            label="Status"
            options={batchStatuses}
            selectedValue={filters.status ?? ''}
            onSelect={(status) => onChangeFilters({ ...filters, status, page: 0 })}
          />
          <ChipGroup
            label="Trigger"
            options={triggerReasons}
            selectedValue={filters.triggerReason ?? ''}
            onSelect={(triggerReason) => onChangeFilters({ ...filters, triggerReason, page: 0 })}
          />
        </View>
      </AdminSection>
      <AdminSection title="Batches" action={<Pagination response={response} onChangeFilters={onChangeFilters} filters={filters} />}>
        <PageSummary response={response} label="batches" />
        <AdminDataTable
          loading={loading}
          rows={response?.content ?? []}
          keyExtractor={(item) => String(item.batchId)}
          emptyTitle="No batches found"
          emptyDescription="Try clearing filters or selecting a wider date range."
          columns={batchColumns}
          onRowPress={(item) => router.push(`/admin/ai-suggestions/batches/${item.batchId}`)}
        />
      </AdminSection>
    </>
  );
}

function FailuresPanel({
  filters,
  loading,
  onChangeFilters,
  response,
}: {
  filters: AdminAiFailureFilters;
  loading: boolean;
  onChangeFilters: (filters: AdminAiFailureFilters) => void;
  response: AdminPageResponse<AdminAiSuggestionBatchRowResponse> | null;
}) {
  return (
    <>
      <DateFilters
        from={filters.from ?? ''}
        onChangeFrom={(from) => onChangeFilters({ ...filters, from, page: 0 })}
        onChangeTo={(to) => onChangeFilters({ ...filters, to, page: 0 })}
        to={filters.to ?? ''}
      />
      <AdminSection title="Failure filters">
        <ChipGroup
          label="Trigger"
          options={triggerReasons}
          selectedValue={filters.triggerReason ?? ''}
          onSelect={(triggerReason) => onChangeFilters({ ...filters, triggerReason, page: 0 })}
        />
      </AdminSection>
      <AdminSection title="Failures" action={<Pagination response={response} onChangeFilters={onChangeFilters} filters={filters} />}>
        <PageSummary response={response} label="failures" />
        <AdminDataTable
          loading={loading}
          rows={response?.content ?? []}
          keyExtractor={(item) => String(item.batchId)}
          emptyTitle="No failures found"
          emptyDescription="AI suggestion failures will appear here when the backend reports them."
          columns={failureColumns}
          onRowPress={(item) => router.push(`/admin/ai-suggestions/batches/${item.batchId}`)}
        />
      </AdminSection>
    </>
  );
}

function RefreshJobsPanel({
  filters,
  loading,
  onChangeFilters,
  response,
}: {
  filters: AdminAiRefreshJobFilters;
  loading: boolean;
  onChangeFilters: (filters: AdminAiRefreshJobFilters) => void;
  response: AdminPageResponse<AiSuggestionRefreshJobResponse> | null;
}) {
  return (
    <>
      <DateFilters
        from={filters.from ?? ''}
        onChangeFrom={(from) => onChangeFilters({ ...filters, from, page: 0 })}
        onChangeTo={(to) => onChangeFilters({ ...filters, to, page: 0 })}
        to={filters.to ?? ''}
      />
      <AdminSection title="Job filters">
        <View style={styles.filterStack}>
          <ChipGroup
            label="Status"
            options={jobStatuses}
            selectedValue={filters.status ?? ''}
            onSelect={(status) => onChangeFilters({ ...filters, status, page: 0 })}
          />
          <ChipGroup
            label="Triggered by"
            options={triggeredByOptions}
            selectedValue={filters.triggeredBy ?? ''}
            onSelect={(triggeredBy) => onChangeFilters({ ...filters, triggeredBy, page: 0 })}
          />
        </View>
      </AdminSection>
      <AdminSection title="Refresh jobs" action={<Pagination response={response} onChangeFilters={onChangeFilters} filters={filters} />}>
        <PageSummary response={response} label="jobs" />
        <AdminDataTable
          loading={loading}
          rows={response?.content ?? []}
          keyExtractor={(item) => String(item.jobId)}
          emptyTitle="No refresh jobs found"
          emptyDescription="Scheduled and admin-triggered refresh jobs will appear after the backend creates them."
          columns={jobColumns}
          onRowPress={(item) => router.push(`/admin/ai-suggestions/jobs/${item.jobId}`)}
        />
      </AdminSection>
    </>
  );
}

function DateFilters({
  from,
  onChangeFrom,
  onChangeTo,
  to,
}: {
  from: string;
  onChangeFrom: (value: string) => void;
  onChangeTo: (value: string) => void;
  to: string;
}) {
  return (
    <AdminSection title="Date range">
      <View style={styles.dateRow}>
        <TextInput
          accessibilityLabel="From date"
          autoCapitalize="none"
          onChangeText={onChangeFrom}
          placeholder="From, YYYY-MM-DD"
          style={styles.input}
          value={from}
        />
        <TextInput
          accessibilityLabel="To date"
          autoCapitalize="none"
          onChangeText={onChangeTo}
          placeholder="To, YYYY-MM-DD"
          style={styles.input}
          value={to}
        />
      </View>
    </AdminSection>
  );
}

function ChipGroup<T extends string>({
  label,
  onSelect,
  options,
  selectedValue,
}: {
  label: string;
  onSelect: (value: T) => void;
  options: Array<{ label: string; value: T }>;
  selectedValue: T;
}) {
  return (
    <View style={styles.filterGroup}>
      <Text selectable style={styles.filterLabel}>
        {label}
      </Text>
      <View style={styles.chips}>
        {options.map((option) => (
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ selected: selectedValue === option.value }}
            key={option.label}
            onPress={() => onSelect(option.value)}
            style={({ pressed }) => [
              styles.chip,
              selectedValue === option.value ? styles.chipActive : undefined,
              pressed ? styles.pressed : undefined,
            ]}>
            <Text style={[styles.chipText, selectedValue === option.value ? styles.chipTextActive : undefined]}>
              {option.label}
            </Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

function Pagination<T, F extends { page?: number } & Record<string, unknown>>({
  filters,
  onChangeFilters,
  response,
}: {
  filters: F;
  onChangeFilters: (filters: F) => void;
  response: AdminPageResponse<T> | null;
}) {
  const canGoBack = (response?.page ?? 0) > 0;
  const canGoNext = response ? response.page + 1 < response.totalPages : false;

  return (
    <View style={styles.actions}>
      <ActionButton
        disabled={!canGoBack}
        label="Previous"
        onPress={() => onChangeFilters({ ...filters, page: Math.max((filters.page ?? 0) - 1, 0) })}
        variant="ghost"
      />
      <ActionButton
        disabled={!canGoNext}
        label="Next"
        onPress={() => onChangeFilters({ ...filters, page: (filters.page ?? 0) + 1 })}
        variant="ghost"
      />
    </View>
  );
}

function PageSummary<T>({ label, response }: { label: string; response: AdminPageResponse<T> | null }) {
  return (
    <AdminMutedText>
      {formatAdminNumber(response?.totalElements ?? 0)} {label}. Page {(response?.page ?? 0) + 1} of{' '}
      {Math.max(response?.totalPages ?? 1, 1)}.
    </AdminMutedText>
  );
}

const groupedCountColumns = [
  {
    key: 'group',
    title: 'Group',
    width: 160,
    render: (item: AdminAiSuggestionGroupedCountResponse & { group: string }) => (
      <AdminFieldText>{item.group}</AdminFieldText>
    ),
  },
  {
    key: 'key',
    title: 'Value',
    width: 220,
    render: (item: AdminAiSuggestionGroupedCountResponse & { group: string }) => (
      <AdminFieldText>{formatAdminEnum(item.key)}</AdminFieldText>
    ),
  },
  {
    key: 'count',
    title: 'Count',
    align: 'right' as const,
    width: 120,
    render: (item: AdminAiSuggestionGroupedCountResponse & { group: string }) => (
      <AdminFieldText>{formatAdminNumber(item.count)}</AdminFieldText>
    ),
  },
];

const batchColumns = [
  {
    key: 'batch',
    title: 'Batch',
    width: 100,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>#{item.batchId}</AdminFieldText>,
  },
  {
    key: 'user',
    title: 'User',
    width: 230,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>{item.userEmail}</AdminFieldText>,
  },
  {
    key: 'status',
    title: 'Status',
    width: 180,
    render: (item: AdminAiSuggestionBatchRowResponse) => (
      <AdminStatusPill tone={getStatusTone(item.status)} value={item.status ?? 'Unknown'} />
    ),
  },
  {
    key: 'trigger',
    title: 'Trigger',
    width: 180,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>{formatAdminEnum(item.triggerReason)}</AdminFieldText>,
  },
  {
    key: 'symbols',
    title: 'Symbols',
    width: 180,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>{item.suggestedSymbols.join(', ') || 'None'}</AdminFieldText>,
  },
  {
    key: 'tokens',
    title: 'Tokens',
    align: 'right' as const,
    width: 120,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>{formatAdminNumber(item.totalTokens)}</AdminFieldText>,
  },
  {
    key: 'created',
    title: 'Created',
    width: 190,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>{formatAdminDate(item.createdAt)}</AdminFieldText>,
  },
];

const failureColumns = [
  ...batchColumns.slice(0, 4),
  {
    key: 'error',
    title: 'Message',
    width: 320,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>{item.errorMessage ?? 'Not available'}</AdminFieldText>,
  },
  {
    key: 'created',
    title: 'Created',
    width: 190,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>{formatAdminDate(item.createdAt)}</AdminFieldText>,
  },
];

const jobColumns = [
  {
    key: 'job',
    title: 'Job',
    width: 100,
    render: (item: AiSuggestionRefreshJobResponse) => <AdminFieldText>#{item.jobId}</AdminFieldText>,
  },
  {
    key: 'status',
    title: 'Status',
    width: 170,
    render: (item: AiSuggestionRefreshJobResponse) => (
      <AdminStatusPill tone={getStatusTone(item.status)} value={item.status ?? 'Unknown'} />
    ),
  },
  {
    key: 'triggeredBy',
    title: 'Triggered by',
    width: 160,
    render: (item: AiSuggestionRefreshJobResponse) => <AdminFieldText>{formatAdminEnum(item.triggeredBy)}</AdminFieldText>,
  },
  {
    key: 'processed',
    title: 'Processed',
    align: 'right' as const,
    width: 120,
    render: (item: AiSuggestionRefreshJobResponse) => <AdminFieldText>{formatAdminNumber(item.processedUsers)}</AdminFieldText>,
  },
  {
    key: 'failed',
    title: 'Failed',
    align: 'right' as const,
    width: 110,
    render: (item: AiSuggestionRefreshJobResponse) => <AdminFieldText>{formatAdminNumber(item.failedCount)}</AdminFieldText>,
  },
  {
    key: 'started',
    title: 'Started',
    width: 190,
    render: (item: AiSuggestionRefreshJobResponse) => <AdminFieldText>{formatAdminDate(item.startedAt)}</AdminFieldText>,
  },
];

const styles = StyleSheet.create({
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  filterStack: {
    gap: Spacing.md,
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
  filterGroup: {
    gap: Spacing.sm,
  },
  filterLabel: {
    color: Colors.light.mutedText,
    fontSize: 12,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  chip: {
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    minHeight: 40,
    justifyContent: 'center',
    paddingHorizontal: Spacing.md,
  },
  chipActive: {
    backgroundColor: '#052344',
    borderColor: '#052344',
  },
  chipText: {
    color: Colors.light.text,
    fontSize: 14,
    fontWeight: '700',
  },
  chipTextActive: {
    color: Colors.light.surface,
  },
  pressed: {
    opacity: 0.82,
  },
});
