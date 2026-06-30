import { router, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { adminApi } from '@/api/admin';
import {
  AdminButton,
  AdminConfirmModal,
  AdminDataTable,
  AdminDateInput,
  AdminFieldText,
  AdminInlineError,
  AdminMetric,
  AdminMetricGrid,
  AdminMutedText,
  AdminPage,
  AdminPagination,
  AdminSection,
  AdminSearchInput,
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

const PAGE_SIZE = 15;
const tabs: Array<{ label: string; value: AdminAiTab }> = [
  { label: 'Usage', value: 'usage' },
  { label: 'Suggestion runs', value: 'batches' },
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
  { label: 'Onboarding Completed', value: 'ONBOARDING_COMPLETED' },
  { label: 'Retake Quiz', value: 'RETAKE_QUIZ' },
  { label: 'Manual Refresh', value: 'MANUAL_REFRESH' },
  { label: 'Scheduled Refresh', value: 'SCHEDULED_REFRESH' },
  { label: 'No Active Suggestion', value: 'NO_ACTIVE_SUGGESTION' },
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
  const { tab } = useLocalSearchParams<{ tab?: string }>();
  const { adminToken, credentials, handleAdminError } = useAdminRequest();
  const [activeTab, setActiveTab] = useState<AdminAiTab>(() => getRouteTab(tab) ?? 'usage');
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

  useEffect(() => {
    const nextTab = getRouteTab(tab);
    if (nextTab) {
      setActiveTab(nextTab);
    }
  }, [tab]);

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
          <AdminButton label="Refresh tab" onPress={loadActiveTab} variant="secondary" />
          <AdminButton
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

function getRouteTab(tab: string | string[] | undefined): AdminAiTab | null {
  const value = Array.isArray(tab) ? tab[0] : tab;
  return value === 'usage' || value === 'batches' || value === 'failures' || value === 'jobs' ? value : null;
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
      <AdminSection
        title="Usage summary"
        description="These totals are for AI stock suggestion generation only. They do not include AI explanation requests. A batch is one backend suggestion run for one user trigger.">
        <AdminMetricGrid>
          <AdminMetric
            description="All AI suggestion runs in the selected date range."
            label="Total batches generated"
            value={formatAdminNumber(usage?.totalBatches)}
          />
          <AdminMetric
            description="Runs that completed with model-backed suggestions."
            label="Successful batches"
            tone="success"
            value={formatAdminNumber(usage?.successCount)}
          />
          <AdminMetric
            description="Runs that ended in FAILED status."
            label="Failed batches"
            tone="danger"
            value={formatAdminNumber(usage?.failedCount)}
          />
          <AdminMetric
            description="Runs reused from earlier successful output."
            label="Cached fallback"
            tone="warn"
            value={formatAdminNumber(usage?.fallbackCachedCount)}
          />
          <AdminMetric
            description="Runs served by rule-based backend fallback."
            label="Rule fallback"
            tone="warn"
            value={formatAdminNumber(usage?.fallbackRuleBasedCount)}
          />
          <AdminMetric
            description="Prompt plus completion tokens for suggestions."
            label="Total tokens used"
            value={formatAdminNumber(usage?.totalTokens)}
          />
        </AdminMetricGrid>
      </AdminSection>
      <AdminSection
        title="Usage breakdown"
        description="The table splits the same usage summary by backend status and trigger reason, so admins can see what caused the total counts.">
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
      <AdminSection
        title="Search and filters"
        description="Filter AI suggestion runs by user email, backend status, trigger reason, or date range. Email search updates as you type.">
        <View style={styles.filterStack}>
          <AdminSearchInput
            accessibilityLabel="Filter batches by user email"
            onChangeText={(email) => onChangeFilters({ ...filters, email, page: 0 })}
            placeholder="User email"
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
      <AdminSection title="AI suggestion run history">
        <AdminDataTable
          loading={loading}
          rows={response?.content ?? []}
          keyExtractor={(item) => String(item.batchId)}
          emptyTitle="No batches found"
          emptyDescription="Try clearing filters or selecting a wider date range."
          columns={batchColumns}
          onRowPress={(item) => router.push(`/admin/ai-suggestions/batches/${item.batchId}`)}
        />
        <Pagination response={response} onChangeFilters={onChangeFilters} filters={filters} label="runs" />
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
      <AdminSection
        title="Failure filters"
        description="Failures are backend-reported AI suggestion runs that did not complete successfully. This is different from fallback batches, which completed with cached or rule-based output.">
        <ChipGroup
          label="Trigger"
          options={triggerReasons}
          selectedValue={filters.triggerReason ?? ''}
          onSelect={(triggerReason) => onChangeFilters({ ...filters, triggerReason, page: 0 })}
        />
      </AdminSection>
      <AdminSection title="Failed AI suggestion runs">
        <AdminDataTable
          loading={loading}
          rows={response?.content ?? []}
          keyExtractor={(item) => String(item.batchId)}
          emptyTitle="No failures found"
          emptyDescription="AI suggestion failures will appear here when the backend reports them."
          columns={failureColumns}
          onRowPress={(item) => router.push(`/admin/ai-suggestions/batches/${item.batchId}`)}
        />
        <Pagination response={response} onChangeFilters={onChangeFilters} filters={filters} label="failures" />
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
      <AdminSection title="Job filters" description="Filter scheduled or admin-triggered refresh jobs by status, source, or date range.">
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
      <AdminSection title="Refresh job history">
        <AdminDataTable
          loading={loading}
          rows={response?.content ?? []}
          keyExtractor={(item) => String(item.jobId)}
          emptyTitle="No refresh jobs found"
          emptyDescription="Scheduled and admin-triggered refresh jobs will appear after the backend creates them."
          columns={jobColumns}
          onRowPress={(item) => router.push(`/admin/ai-suggestions/jobs/${item.jobId}`)}
        />
        <Pagination response={response} onChangeFilters={onChangeFilters} filters={filters} label="jobs" />
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
        <AdminDateInput
          accessibilityLabel="From date"
          onChangeText={onChangeFrom}
          placeholder="From date"
          value={from}
        />
        <AdminDateInput
          accessibilityLabel="To date"
          onChangeText={onChangeTo}
          placeholder="To date"
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
            style={(state) => [
              styles.chip,
              selectedValue === option.value ? styles.chipActive : undefined,
              selectedValue !== option.value && isHovered(state) ? styles.chipHovered : undefined,
              state.pressed ? styles.pressed : undefined,
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

function Pagination<T, F extends { page?: number; size?: number } & Record<string, unknown>>({
  filters,
  label,
  onChangeFilters,
  response,
}: {
  filters: F;
  label: string;
  onChangeFilters: (filters: F) => void;
  response: AdminPageResponse<T> | null;
}) {
  const page = response?.page ?? filters.page ?? 0;
  const size = response?.size ?? filters.size ?? PAGE_SIZE;
  const totalElements = response?.totalElements ?? 0;
  const totalPages = Math.max(response?.totalPages ?? 1, 1);

  return (
    <AdminPagination
      itemLabel={label}
      onPageChange={(nextPage) => onChangeFilters({ ...filters, page: nextPage })}
      onSizeChange={(nextSize) => onChangeFilters({ ...filters, page: 0, size: nextSize })}
      page={page}
      size={size}
      totalElements={totalElements}
      totalPages={totalPages}
    />
  );
}

const groupedCountColumns = [
  {
    key: 'group',
    title: 'Group',
    width: 160,
    sortValue: (item: AdminAiSuggestionGroupedCountResponse & { group: string }) => item.group,
    render: (item: AdminAiSuggestionGroupedCountResponse & { group: string }) => (
      <AdminFieldText>{item.group}</AdminFieldText>
    ),
  },
  {
    key: 'key',
    title: 'Value',
    width: 220,
    sortValue: (item: AdminAiSuggestionGroupedCountResponse & { group: string }) => item.key,
    render: (item: AdminAiSuggestionGroupedCountResponse & { group: string }) => (
      <AdminFieldText>{formatAdminEnum(item.key)}</AdminFieldText>
    ),
  },
  {
    key: 'count',
    title: 'Count',
    width: 120,
    sortValue: (item: AdminAiSuggestionGroupedCountResponse & { group: string }) => item.count,
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
    sortValue: (item: AdminAiSuggestionBatchRowResponse) => item.batchId,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>#{item.batchId}</AdminFieldText>,
  },
  {
    key: 'user',
    title: 'User email',
    width: 360,
    sortValue: (item: AdminAiSuggestionBatchRowResponse) => item.userEmail,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>{item.userEmail}</AdminFieldText>,
  },
  {
    key: 'status',
    title: 'Status',
    width: 180,
    sortValue: (item: AdminAiSuggestionBatchRowResponse) => item.status,
    render: (item: AdminAiSuggestionBatchRowResponse) => (
      <AdminStatusPill tone={getStatusTone(item.status)} value={item.status ?? 'Unknown'} />
    ),
  },
  {
    key: 'trigger',
    title: 'Trigger',
    width: 180,
    sortValue: (item: AdminAiSuggestionBatchRowResponse) => item.triggerReason,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>{formatAdminEnum(item.triggerReason)}</AdminFieldText>,
  },
  {
    key: 'symbols',
    title: 'Symbols',
    width: 180,
    sortValue: (item: AdminAiSuggestionBatchRowResponse) => item.suggestedSymbols.join(', '),
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>{item.suggestedSymbols.join(', ') || 'None'}</AdminFieldText>,
  },
  {
    key: 'tokens',
    title: 'Tokens',
    align: 'right' as const,
    width: 120,
    sortValue: (item: AdminAiSuggestionBatchRowResponse) => item.totalTokens,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>{formatAdminNumber(item.totalTokens)}</AdminFieldText>,
  },
  {
    key: 'created',
    title: 'Created',
    width: 190,
    sortValue: (item: AdminAiSuggestionBatchRowResponse) => item.createdAt,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>{formatAdminDate(item.createdAt)}</AdminFieldText>,
  },
];

const failureColumns = [
  ...batchColumns.slice(0, 4),
  {
    key: 'error',
    title: 'Message',
    width: 620,
    sortValue: (item: AdminAiSuggestionBatchRowResponse) => item.errorMessage,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>{item.errorMessage ?? 'Not available'}</AdminFieldText>,
  },
  {
    key: 'created',
    title: 'Created',
    width: 190,
    sortValue: (item: AdminAiSuggestionBatchRowResponse) => item.createdAt,
    render: (item: AdminAiSuggestionBatchRowResponse) => <AdminFieldText>{formatAdminDate(item.createdAt)}</AdminFieldText>,
  },
];

const jobColumns = [
  {
    key: 'job',
    title: 'Job',
    width: 100,
    sortValue: (item: AiSuggestionRefreshJobResponse) => item.jobId,
    render: (item: AiSuggestionRefreshJobResponse) => <AdminFieldText>#{item.jobId}</AdminFieldText>,
  },
  {
    key: 'status',
    title: 'Status',
    width: 170,
    sortValue: (item: AiSuggestionRefreshJobResponse) => item.status,
    render: (item: AiSuggestionRefreshJobResponse) => (
      <AdminStatusPill tone={getStatusTone(item.status)} value={item.status ?? 'Unknown'} />
    ),
  },
  {
    key: 'triggeredBy',
    title: 'Triggered by',
    width: 160,
    sortValue: (item: AiSuggestionRefreshJobResponse) => item.triggeredBy,
    render: (item: AiSuggestionRefreshJobResponse) => <AdminFieldText>{formatAdminEnum(item.triggeredBy)}</AdminFieldText>,
  },
  {
    key: 'processed',
    title: 'Processed',
    width: 120,
    sortValue: (item: AiSuggestionRefreshJobResponse) => item.processedUsers,
    render: (item: AiSuggestionRefreshJobResponse) => <AdminFieldText>{formatAdminNumber(item.processedUsers)}</AdminFieldText>,
  },
  {
    key: 'failed',
    title: 'Failed',
    width: 110,
    sortValue: (item: AiSuggestionRefreshJobResponse) => item.failedCount,
    render: (item: AiSuggestionRefreshJobResponse) => <AdminFieldText>{formatAdminNumber(item.failedCount)}</AdminFieldText>,
  },
  {
    key: 'started',
    title: 'Started',
    width: 190,
    sortValue: (item: AiSuggestionRefreshJobResponse) => item.startedAt,
    render: (item: AiSuggestionRefreshJobResponse) => <AdminFieldText>{formatAdminDate(item.startedAt)}</AdminFieldText>,
  },
];

function isHovered(state: unknown) {
  return Boolean((state as { hovered?: boolean }).hovered);
}

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
  chipHovered: {
    backgroundColor: '#F1F5F9',
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
