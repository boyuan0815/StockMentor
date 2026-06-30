import { router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';

import { adminApi } from '@/api/admin';
import {
  AdminButton,
  AdminDataTable,
  AdminFieldText,
  AdminInlineError,
  AdminMetric,
  AdminMetricGrid,
  AdminPage,
  AdminSection,
  AdminStatusPill,
  formatAdminDate,
  formatAdminEnum,
  formatAdminNumber,
  getStatusTone,
  useAdminRequest,
} from '@/components/admin/admin-ui';
import type {
  AdminAiSuggestionBatchRowResponse,
  AdminAiSuggestionUsageSummaryResponse,
  AiSuggestionRefreshJobResponse,
} from '@/types/admin';

export function AdminDashboardScreen() {
  const { adminToken, credentials, handleAdminError } = useAdminRequest();
  const [usage, setUsage] = useState<AdminAiSuggestionUsageSummaryResponse | null>(null);
  const [failures, setFailures] = useState<AdminAiSuggestionBatchRowResponse[]>([]);
  const [jobs, setJobs] = useState<AiSuggestionRefreshJobResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadDashboard = useCallback(async () => {
    if (!credentials || !adminToken) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setErrorMessage(null);

    try {
      const [nextUsage, nextFailures, nextJobs] = await Promise.all([
        adminApi.getUsageSummary(credentials, adminToken),
        adminApi.listFailures(credentials, adminToken, { page: 0, size: 5 }),
        adminApi.listRefreshJobs(credentials, adminToken, { page: 0, size: 5 }),
      ]);

      setUsage(nextUsage);
      setFailures(nextFailures.content);
      setJobs(nextJobs.content);
    } catch (error) {
      setErrorMessage(handleAdminError(error));
    } finally {
      setLoading(false);
    }
  }, [adminToken, credentials, handleAdminError]);

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  return (
    <AdminPage
      title="Dashboard"
      actions={<AdminButton label="Refresh" onPress={loadDashboard} variant="secondary" />}>
      <AdminInlineError message={errorMessage} />

      <AdminMetricGrid>
        <AdminMetric
          description="AI suggestion generation attempts."
          label="Total batches generated"
          value={formatAdminNumber(usage?.totalBatches)}
        />
        <AdminMetric
          description="Batches generated normally."
          label="Successful batches"
          tone="success"
          value={formatAdminNumber(usage?.successCount)}
        />
        <AdminMetric
          description="Batches that ended in FAILED status."
          label="Failed batches"
          tone="danger"
          value={formatAdminNumber(usage?.failedCount)}
        />
        <AdminMetric
          description="Batches served by cache or rules."
          label="Fallback batches"
          tone="warn"
          value={formatAdminNumber((usage?.fallbackCachedCount ?? 0) + (usage?.fallbackRuleBasedCount ?? 0))}
        />
        <AdminMetric
          description="Prompt plus completion tokens for AI stock suggestions."
          label="Total tokens used"
          value={formatAdminNumber(usage?.totalTokens)}
        />
      </AdminMetricGrid>

      <AdminSection
        title="Recent AI failures"
        description="Latest AI suggestion batches reported by the backend as failed. This can be empty even when fallback batches exist."
        action={
          <AdminButton
            label="View all failures"
            onPress={() => router.push('/admin/ai-suggestions?tab=failures')}
            variant="ghost"
          />
        }>
        <AdminDataTable
          loading={loading}
          rows={failures}
          keyExtractor={(item) => String(item.batchId)}
          emptyTitle="No recent failures"
          emptyDescription="AI suggestion failure rows will appear here when the backend reports them."
          columns={[
            {
              key: 'batch',
              title: 'Batch',
              width: 120,
              sortValue: (item) => item.batchId,
              render: (item) => <AdminFieldText>#{item.batchId}</AdminFieldText>,
            },
            {
              key: 'user',
              title: 'User email',
              width: 360,
              sortValue: (item) => item.userEmail || `User #${item.userId}`,
              render: (item) => <AdminFieldText>{item.userEmail || `User #${item.userId}`}</AdminFieldText>,
            },
            {
              key: 'trigger',
              title: 'Trigger',
              width: 240,
              sortValue: (item) => item.triggerReason,
              render: (item) => <AdminFieldText>{formatAdminEnum(item.triggerReason)}</AdminFieldText>,
            },
            {
              key: 'created',
              title: 'Created',
              width: 240,
              sortValue: (item) => item.createdAt,
              render: (item) => <AdminFieldText>{formatAdminDate(item.createdAt)}</AdminFieldText>,
            },
          ]}
          onRowPress={(item) => router.push(`/admin/ai-suggestions/batches/${item.batchId}`)}
        />
      </AdminSection>

      <AdminSection
        title="Refresh jobs"
        description="Scheduled or admin-triggered AI suggestion refresh work."
        action={
          <AdminButton
            label="View refresh jobs"
            onPress={() => router.push('/admin/ai-suggestions?tab=jobs')}
            variant="ghost"
          />
        }>
        <AdminDataTable
          loading={loading}
          rows={jobs}
          keyExtractor={(item) => String(item.jobId)}
          emptyTitle="No refresh jobs yet"
          emptyDescription="Scheduled or admin-triggered refresh jobs will appear after the backend creates them."
          columns={[
            {
              key: 'job',
              title: 'Job',
              width: 120,
              sortValue: (item) => item.jobId,
              render: (item) => <AdminFieldText>#{item.jobId}</AdminFieldText>,
            },
            {
              key: 'status',
              title: 'Status',
              width: 170,
              sortValue: (item) => item.status,
              render: (item) => <AdminStatusPill tone={getStatusTone(item.status)} value={item.status ?? 'Unknown'} />,
            },
            {
              key: 'triggered',
              title: 'Triggered by',
              width: 190,
              sortValue: (item) => item.triggeredBy,
              render: (item) => <AdminFieldText>{formatAdminEnum(item.triggeredBy)}</AdminFieldText>,
            },
            {
              key: 'processed',
              title: 'Processed',
              width: 120,
              sortValue: (item) => item.processedUsers,
              render: (item) => <AdminFieldText>{formatAdminNumber(item.processedUsers)}</AdminFieldText>,
            },
            {
              key: 'started',
              title: 'Started',
              width: 240,
              sortValue: (item) => item.startedAt,
              render: (item) => <AdminFieldText>{formatAdminDate(item.startedAt)}</AdminFieldText>,
            },
          ]}
          onRowPress={(item) => router.push(`/admin/ai-suggestions/jobs/${item.jobId}`)}
        />
      </AdminSection>
    </AdminPage>
  );
}
