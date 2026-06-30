import { router, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { View } from 'react-native';

import { adminApi } from '@/api/admin';
import { EmptyState } from '@/components/foundation/empty-state';
import {
  AdminButton,
  AdminConfirmModal,
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
  formatAdminMoney,
  formatAdminNumber,
  getStatusTone,
  useAdminRequest,
} from '@/components/admin/admin-ui';
import type { AdminMutableUserStatus, AdminUserDetailResponse } from '@/types/admin';

export function AdminUserDetailScreen() {
  const { userId } = useLocalSearchParams<{ userId?: string }>();
  const parsedUserId = Number(userId);
  const { adminToken, credentials, handleAdminError } = useAdminRequest();
  const [detail, setDetail] = useState<AdminUserDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [confirmStatus, setConfirmStatus] = useState<AdminMutableUserStatus | null>(null);
  const [savingStatus, setSavingStatus] = useState(false);

  const loadUser = useCallback(async () => {
    if (!credentials || !adminToken || !Number.isFinite(parsedUserId)) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setErrorMessage(null);

    try {
      const nextDetail = await adminApi.getUser(credentials, adminToken, parsedUserId);
      setDetail(nextDetail);
    } catch (error) {
      setErrorMessage(handleAdminError(error));
    } finally {
      setLoading(false);
    }
  }, [adminToken, credentials, handleAdminError, parsedUserId]);

  useEffect(() => {
    void loadUser();
  }, [loadUser]);

  const targetStatus = useMemo<AdminMutableUserStatus | null>(() => {
    if (detail?.user.status === 'ACTIVE') {
      return 'INACTIVE';
    }
    if (detail?.user.status === 'INACTIVE') {
      return 'ACTIVE';
    }
    return null;
  }, [detail?.user.status]);

  const updateStatus = useCallback(async () => {
    if (!credentials || !adminToken || !detail || !confirmStatus || savingStatus) {
      return;
    }

    setSavingStatus(true);
    setErrorMessage(null);

    try {
      const updatedDetail = await adminApi.updateUserStatus(credentials, adminToken, detail.user.userId, {
        status: confirmStatus,
      });
      setDetail(updatedDetail);
      setConfirmStatus(null);
    } catch (error) {
      setErrorMessage(handleAdminError(error));
    } finally {
      setSavingStatus(false);
    }
  }, [adminToken, confirmStatus, credentials, detail, handleAdminError, savingStatus]);

  const user = detail?.user;

  return (
    <AdminPage
      title={user ? user.email : 'User detail'}
      actions={
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <AdminButton label="Back to users" onPress={() => router.push('/admin/users')} variant="ghost" />
          <AdminButton label="Refresh" onPress={loadUser} variant="secondary" />
        </View>
      }>
      <AdminInlineError message={errorMessage} />

      <AdminSection
        title="Account"
        action={
          targetStatus ? (
            <AdminButton
              disabled={loading || savingStatus}
              label={targetStatus === 'ACTIVE' ? 'Re-enable user' : 'Disable user'}
              onPress={() => setConfirmStatus(targetStatus)}
              variant={targetStatus === 'ACTIVE' ? 'secondary' : 'danger'}
            />
          ) : null
        }>
        {loading || !user ? (
          <AdminMutedText>Loading user detail...</AdminMutedText>
        ) : (
          <>
            <AdminMetricGrid>
              <AdminMetric label="User ID" value={`#${user.userId}`} />
              <AdminMetric label="Role" value={formatAdminEnum(user.role)} />
              <AdminMetric label="Status" tone={getStatusTone(user.status)} value={formatAdminEnum(user.status)} />
            </AdminMetricGrid>
            <AdminKeyValueGrid
              rows={[
                ['Email', user.email],
                ['Username', user.username],
                ['Status', <AdminStatusPill tone={getStatusTone(user.status)} value={user.status ?? 'Unknown'} />],
                ['Role', formatAdminEnum(user.role)],
                ['Deleted', user.isDeleted ? 'Yes' : 'No'],
                ['Onboarding completed', user.onboardingCompleted ? 'Yes' : 'No'],
                ['Has investment profile', user.hasInvestmentProfile ? 'Yes' : 'No'],
                ['Created', formatAdminDate(user.createdAt)],
                ['Updated', formatAdminDate(user.updatedAt)],
                ['Last login', formatAdminDate(user.lastLoginAt)],
              ]}
            />
          </>
        )}
      </AdminSection>

      <AdminSection title="Latest investment profile">
        {detail?.latestInvestmentProfile ? (
          <AdminKeyValueGrid
            rows={[
              ['Profile ID', `#${detail.latestInvestmentProfile.profileId}`],
              ['Version', formatAdminNumber(detail.latestInvestmentProfile.profileVersion)],
              ['Source', formatAdminEnum(detail.latestInvestmentProfile.profileSource)],
              ['Risk tolerance', formatAdminEnum(detail.latestInvestmentProfile.riskTolerance)],
              ['Investment goal', formatAdminEnum(detail.latestInvestmentProfile.investmentGoal)],
              ['Experience', formatAdminEnum(detail.latestInvestmentProfile.experienceLevel)],
              ['Volatility', formatAdminEnum(detail.latestInvestmentProfile.preferredVolatility)],
              ['Horizon', formatAdminEnum(detail.latestInvestmentProfile.preferredHorizon)],
              ['Risk score', formatAdminNumber(detail.latestInvestmentProfile.riskScore)],
              ['Goal score', formatAdminNumber(detail.latestInvestmentProfile.goalScore)],
              ['Experience score', formatAdminNumber(detail.latestInvestmentProfile.experienceScore)],
              ['Created', formatAdminDate(detail.latestInvestmentProfile.createdAt)],
              ['Updated', formatAdminDate(detail.latestInvestmentProfile.updatedAt)],
            ]}
          />
        ) : (
          <EmptyState title="Not available yet" description="This user does not have an investment profile summary." />
        )}
      </AdminSection>

      <AdminSection title="Behavior summary">
        {detail?.behaviorSummary ? (
          <AdminKeyValueGrid
            rows={[
              ['Behavior profile ID', `#${detail.behaviorSummary.behaviorProfileId}`],
              ['Confidence', formatAdminEnum(detail.behaviorSummary.behaviorConfidence)],
              ['Style', formatAdminEnum(detail.behaviorSummary.behaviorStyle)],
              ['Risk score', formatAdminNumber(detail.behaviorSummary.behaviorRiskScore)],
              ['Summary', detail.behaviorSummary.behaviorSummaryText ?? 'Not available yet'],
              ['Source note', detail.behaviorSummary.sourceNote ?? 'Not available yet'],
              ['Updated', formatAdminDate(detail.behaviorSummary.updatedAt)],
            ]}
          />
        ) : (
          <EmptyState title="Not available yet" description="No behavior summary is stored for this user." />
        )}
      </AdminSection>

      <AdminSection title="Paper trading summary">
        {detail?.paperTradingSummary ? (
          <AdminKeyValueGrid
            rows={[
              ['Account status', formatAdminEnum(detail.paperTradingSummary.accountStatus)],
              ['Cash balance', formatAdminMoney(detail.paperTradingSummary.cashBalance)],
              ['Realized P/L', formatAdminMoney(detail.paperTradingSummary.realizedProfitLoss)],
              ['Current session', formatAdminNumber(detail.paperTradingSummary.currentSessionNumber)],
              ['Positions', formatAdminNumber(detail.paperTradingSummary.positionCount)],
              ['Transactions', formatAdminNumber(detail.paperTradingSummary.transactionCount)],
              ['Last reset', formatAdminDate(detail.paperTradingSummary.lastResetAt)],
              ['Created', formatAdminDate(detail.paperTradingSummary.createdAt)],
              ['Updated', formatAdminDate(detail.paperTradingSummary.updatedAt)],
            ]}
          />
        ) : (
          <EmptyState title="Not available yet" description="No paper-trading account summary is stored for this user." />
        )}
      </AdminSection>

      <AdminFieldText>
        Status changes send only ACTIVE or INACTIVE to the backend. Deleted users and protected admin cases are enforced
        by the verified admin API.
      </AdminFieldText>

      <AdminConfirmModal
        visible={Boolean(confirmStatus)}
        title={confirmStatus === 'ACTIVE' ? 'Re-enable user?' : 'Disable user?'}
        message={
          confirmStatus === 'ACTIVE'
            ? 'This will restore sign-in access for the selected user if the backend accepts the change.'
            : 'This will block sign-in for the selected user. Existing user data is not deleted.'
        }
        confirmLabel={confirmStatus === 'ACTIVE' ? 'Re-enable user' : 'Disable user'}
        pendingLabel="Saving..."
        pending={savingStatus}
        danger={confirmStatus === 'INACTIVE'}
        onCancel={() => (savingStatus ? undefined : setConfirmStatus(null))}
        onConfirm={updateStatus}
      />
    </AdminPage>
  );
}
