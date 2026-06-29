import { router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { View } from 'react-native';

import { adminApi } from '@/api/admin';
import { ActionButton } from '@/components/foundation/action-button';
import {
  AdminDataTable,
  AdminFieldText,
  AdminInlineError,
  AdminMetric,
  AdminMetricGrid,
  AdminMutedText,
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
      actions={<ActionButton label="Refresh" onPress={loadDashboard} variant="secondary" />}>
      <AdminInlineError message={errorMessage} />

      <AdminMetricGrid>
        <AdminMetric label="Total batches" value={formatAdminNumber(usage?.totalBatches)} />
        <AdminMetric label="Successful" tone="success" value={formatAdminNumber(usage?.successCount)} />
        <AdminMetric label="Failed" tone="danger" value={formatAdminNumber(usage?.failedCount)} />
        <AdminMetric label="Fallback" tone="warn" value={formatAdminNumber((usage?.fallbackCachedCount ?? 0) + (usage?.fallbackRuleBasedCount ?? 0))} />
        <AdminMetric label="Total tokens" value={formatAdminNumber(usage?.totalTokens)} />
      </AdminMetricGrid>

      <AdminSection
        title="Recent AI failures"
        action={
          <ActionButton
            label="Open monitoring"
            onPress={() => router.push('/admin/ai-suggestions')}
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
              width: 100,
              render: (item) => <AdminFieldText>#{item.batchId}</AdminFieldText>,
            },
            {
              key: 'user',
              title: 'User',
              width: 220,
              render: (item) => <AdminFieldText>{item.userEmail || `User #${item.userId}`}</AdminFieldText>,
            },
            {
              key: 'trigger',
              title: 'Trigger',
              width: 180,
              render: (item) => <AdminFieldText>{formatAdminEnum(item.triggerReason)}</AdminFieldText>,
            },
            {
              key: 'created',
              title: 'Created',
              width: 190,
              render: (item) => <AdminFieldText>{formatAdminDate(item.createdAt)}</AdminFieldText>,
            },
          ]}
          onRowPress={(item) => router.push(`/admin/ai-suggestions/batches/${item.batchId}`)}
        />
      </AdminSection>

      <AdminSection
        title="Refresh jobs"
        action={
          <ActionButton
            label="View jobs"
            onPress={() => router.push('/admin/ai-suggestions')}
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
              width: 100,
              render: (item) => <AdminFieldText>#{item.jobId}</AdminFieldText>,
            },
            {
              key: 'status',
              title: 'Status',
              width: 170,
              render: (item) => <AdminStatusPill tone={getStatusTone(item.status)} value={item.status ?? 'Unknown'} />,
            },
            {
              key: 'triggered',
              title: 'Triggered by',
              width: 160,
              render: (item) => <AdminFieldText>{formatAdminEnum(item.triggeredBy)}</AdminFieldText>,
            },
            {
              key: 'processed',
              title: 'Processed',
              align: 'right',
              width: 120,
              render: (item) => <AdminFieldText>{formatAdminNumber(item.processedUsers)}</AdminFieldText>,
            },
            {
              key: 'started',
              title: 'Started',
              width: 190,
              render: (item) => <AdminFieldText>{formatAdminDate(item.startedAt)}</AdminFieldText>,
            },
          ]}
          onRowPress={(item) => router.push(`/admin/ai-suggestions/jobs/${item.jobId}`)}
        />
      </AdminSection>

      <View>
        <AdminMutedText>
          Admin API calls use the signed-in Basic Auth session plus the in-memory admin token.
        </AdminMutedText>
      </View>
    </AdminPage>
  );
}
