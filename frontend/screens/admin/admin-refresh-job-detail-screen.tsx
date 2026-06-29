import { router, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { View } from 'react-native';

import { adminApi } from '@/api/admin';
import { ActionButton } from '@/components/foundation/action-button';
import {
  AdminInlineError,
  AdminKeyValueGrid,
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
import type { AiSuggestionRefreshJobResponse } from '@/types/admin';

export function AdminRefreshJobDetailScreen() {
  const { jobId } = useLocalSearchParams<{ jobId?: string }>();
  const parsedJobId = Number(jobId);
  const { adminToken, credentials, handleAdminError } = useAdminRequest();
  const [job, setJob] = useState<AiSuggestionRefreshJobResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadJob = useCallback(async () => {
    if (!credentials || !adminToken || !Number.isFinite(parsedJobId)) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setErrorMessage(null);

    try {
      setJob(await adminApi.getRefreshJob(credentials, adminToken, parsedJobId));
    } catch (error) {
      setErrorMessage(handleAdminError(error));
    } finally {
      setLoading(false);
    }
  }, [adminToken, credentials, handleAdminError, parsedJobId]);

  useEffect(() => {
    void loadJob();
  }, [loadJob]);

  return (
    <AdminPage
      title={job ? `Refresh job #${job.jobId}` : 'Refresh job detail'}
      actions={
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <ActionButton label="Back to monitoring" onPress={() => router.push('/admin/ai-suggestions')} variant="ghost" />
          <ActionButton label="Refresh" onPress={loadJob} variant="secondary" />
        </View>
      }>
      <AdminInlineError message={errorMessage} />

      <AdminSection title="Job summary">
        {loading || !job ? (
          <AdminMutedText>Loading refresh job detail...</AdminMutedText>
        ) : (
          <>
            <AdminMetricGrid>
              <AdminMetric label="Processed" value={formatAdminNumber(job.processedUsers)} />
              <AdminMetric label="Success" tone="success" value={formatAdminNumber(job.successCount)} />
              <AdminMetric label="Fallback" tone="warn" value={formatAdminNumber(job.fallbackCount)} />
              <AdminMetric label="Failed" tone="danger" value={formatAdminNumber(job.failedCount)} />
            </AdminMetricGrid>
            <AdminKeyValueGrid
              rows={[
                ['Status', <AdminStatusPill tone={getStatusTone(job.status)} value={job.status ?? 'Unknown'} />],
                ['Triggered by', formatAdminEnum(job.triggeredBy)],
                ['Triggered by user', job.triggeredByUserId ? `#${job.triggeredByUserId}` : 'Not available'],
                ['Started', formatAdminDate(job.startedAt)],
                ['Finished', formatAdminDate(job.finishedAt)],
                ['Processed users', formatAdminNumber(job.processedUsers)],
                ['Skipped users', formatAdminNumber(job.skippedUsers)],
                ['Success count', formatAdminNumber(job.successCount)],
                ['Reused count', formatAdminNumber(job.reusedCount)],
                ['Fallback count', formatAdminNumber(job.fallbackCount)],
                ['Failed count', formatAdminNumber(job.failedCount)],
                ['Message', job.message ?? 'Not available'],
              ]}
            />
          </>
        )}
      </AdminSection>
    </AdminPage>
  );
}
