import { router, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { View } from 'react-native';

import { adminApi } from '@/api/admin';
import {
  AdminButton,
  AdminDataTable,
  AdminFieldText,
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
import type { AdminAiSuggestionBatchDetailResponse } from '@/types/admin';

export function AdminAiBatchDetailScreen() {
  const { batchId } = useLocalSearchParams<{ batchId?: string }>();
  const parsedBatchId = Number(batchId);
  const { adminToken, credentials, handleAdminError } = useAdminRequest();
  const [detail, setDetail] = useState<AdminAiSuggestionBatchDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadBatch = useCallback(async () => {
    if (!credentials || !adminToken || !Number.isFinite(parsedBatchId)) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setErrorMessage(null);

    try {
      setDetail(await adminApi.getBatch(credentials, adminToken, parsedBatchId));
    } catch (error) {
      setErrorMessage(handleAdminError(error));
    } finally {
      setLoading(false);
    }
  }, [adminToken, credentials, handleAdminError, parsedBatchId]);

  useEffect(() => {
    void loadBatch();
  }, [loadBatch]);

  return (
    <AdminPage
      title={detail ? `Batch #${detail.batchId}` : 'AI batch detail'}
      actions={
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <AdminButton label="Back to monitoring" onPress={() => router.push('/admin/ai-suggestions')} variant="ghost" />
          <AdminButton label="Refresh" onPress={loadBatch} variant="secondary" />
        </View>
      }>
      <AdminInlineError message={errorMessage} />

      <AdminSection title="Batch summary">
        {loading || !detail ? (
          <AdminMutedText>Loading batch detail...</AdminMutedText>
        ) : (
          <>
            <AdminMetricGrid>
              <AdminMetric label="Items" value={formatAdminNumber(detail.itemCount)} />
              <AdminMetric label="Tokens" value={formatAdminNumber(detail.totalTokens)} />
              <AdminMetric label="Status" tone={getStatusTone(detail.status)} value={formatAdminEnum(detail.status)} />
              <AdminMetric label="Fallback" tone={detail.fallbackUsed ? 'warn' : 'neutral'} value={detail.fallbackUsed ? 'Yes' : 'No'} />
            </AdminMetricGrid>
            <AdminKeyValueGrid
              rows={[
                ['Status', <AdminStatusPill tone={getStatusTone(detail.status)} value={detail.status ?? 'Unknown'} />],
                ['User', `${detail.userEmail} (${detail.username})`],
                ['User ID', `#${detail.userId}`],
                ['Trigger', formatAdminEnum(detail.triggerReason)],
                ['Analysis timeframe', detail.analysisTimeframe ?? 'Not available'],
                ['Model', detail.model ?? 'Not available'],
                ['Prompt version', detail.promptVersion ?? 'Not available'],
                ['Profile version', formatAdminNumber(detail.profileVersion)],
                ['Finish reason', detail.finishReason ?? 'Not available'],
                ['Prompt tokens', formatAdminNumber(detail.promptTokens)],
                ['Completion tokens', formatAdminNumber(detail.completionTokens)],
                ['Total tokens', formatAdminNumber(detail.totalTokens)],
                ['Symbols', detail.suggestedSymbols.join(', ') || 'None'],
                ['Created', formatAdminDate(detail.createdAt)],
                ['Expires', formatAdminDate(detail.expiresAt)],
                ['Error message', detail.errorMessage ?? 'Not available'],
              ]}
            />
          </>
        )}
      </AdminSection>

      <AdminSection title="Suggested items">
        <AdminDataTable
          loading={loading}
          rows={detail?.items ?? []}
          keyExtractor={(item) => String(item.itemId)}
          emptyTitle="No items"
          emptyDescription="This batch does not contain visible suggestion items."
          columns={[
            {
              key: 'rank',
              title: 'Rank',
              align: 'right',
              width: 90,
              sortValue: (item) => item.rankNo,
              render: (item) => <AdminFieldText>{formatAdminNumber(item.rankNo)}</AdminFieldText>,
            },
            {
              key: 'symbol',
              title: 'Symbol',
              width: 100,
              sortValue: (item) => item.symbol,
              render: (item) => <AdminFieldText>{item.symbol}</AdminFieldText>,
            },
            {
              key: 'label',
              title: 'Label',
              width: 190,
              sortValue: (item) => item.suggestionLabel,
              render: (item) => <AdminFieldText>{item.suggestionLabel ?? 'Not available'}</AdminFieldText>,
            },
            {
              key: 'score',
              title: 'Score',
              align: 'right',
              width: 100,
              sortValue: (item) => item.matchScore,
              render: (item) => <AdminFieldText>{formatAdminNumber(item.matchScore)}</AdminFieldText>,
            },
            {
              key: 'risk',
              title: 'Risk',
              width: 140,
              sortValue: (item) => item.riskLevel,
              render: (item) => <AdminFieldText>{formatAdminEnum(item.riskLevel)}</AdminFieldText>,
            },
            {
              key: 'status',
              title: 'Status',
              width: 160,
              sortValue: (item) => item.status,
              render: (item) => <AdminStatusPill tone={getStatusTone(item.status)} value={item.status ?? 'Unknown'} />,
            },
            {
              key: 'reason',
              title: 'Short reason',
              width: 320,
              sortValue: (item) => item.shortReason,
              render: (item) => <AdminFieldText>{item.shortReason ?? 'Not available'}</AdminFieldText>,
            },
            {
              key: 'snapshot',
              title: 'Snapshot',
              width: 120,
              sortValue: (item) => item.snapshotId,
              render: (item) => <AdminFieldText>{item.snapshotId ? `#${item.snapshotId}` : 'Not available'}</AdminFieldText>,
            },
            {
              key: 'created',
              title: 'Created',
              width: 190,
              sortValue: (item) => item.createdAt,
              render: (item) => <AdminFieldText>{formatAdminDate(item.createdAt)}</AdminFieldText>,
            },
          ]}
        />
      </AdminSection>
    </AdminPage>
  );
}
