import { apiRequest } from '@/api/client';
import type {
  AdminAiBatchFilters,
  AdminAiFailureFilters,
  AdminAiRefreshJobFilters,
  AdminBackfillRequest,
  AdminPageResponse,
  AdminUpdateUserStatusRequest,
  AdminUsageSummaryFilters,
  AdminUserDetailResponse,
  AdminUserListFilters,
  AdminUserListItemResponse,
  AdminAiSuggestionBatchDetailResponse,
  AdminAiSuggestionBatchRowResponse,
  AdminAiSuggestionUsageSummaryResponse,
  AiSuggestionRefreshJobResponse,
  BackfillResultDto,
} from '@/types/admin';
import type { BasicAuthCredentials } from '@/types/auth';

type QueryValue = string | number | boolean | null | undefined;

function withQuery(path: string, params: Record<string, QueryValue> = {}) {
  const query = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value === null || value === undefined || value === '') {
      return;
    }
    query.set(key, String(value));
  });

  const queryString = query.toString();
  return queryString ? `${path}?${queryString}` : path;
}

function adminOptions(credentials: BasicAuthCredentials, adminToken: string) {
  return {
    credentials,
    adminToken,
  };
}

export const adminApi = {
  listUsers(
    credentials: BasicAuthCredentials,
    adminToken: string,
    filters: AdminUserListFilters = {},
  ) {
    return apiRequest<AdminPageResponse<AdminUserListItemResponse>>(
      withQuery('/api/admin/users', filters),
      adminOptions(credentials, adminToken),
    );
  },

  getUser(credentials: BasicAuthCredentials, adminToken: string, userId: number | string) {
    return apiRequest<AdminUserDetailResponse>(
      `/api/admin/users/${userId}`,
      adminOptions(credentials, adminToken),
    );
  },

  updateUserStatus(
    credentials: BasicAuthCredentials,
    adminToken: string,
    userId: number | string,
    request: AdminUpdateUserStatusRequest,
  ) {
    return apiRequest<AdminUserDetailResponse>(`/api/admin/users/${userId}/status`, {
      ...adminOptions(credentials, adminToken),
      method: 'PATCH',
      body: request,
    });
  },

  listBatches(
    credentials: BasicAuthCredentials,
    adminToken: string,
    filters: AdminAiBatchFilters = {},
  ) {
    return apiRequest<AdminPageResponse<AdminAiSuggestionBatchRowResponse>>(
      withQuery('/api/admin/ai-suggestions/batches', filters),
      adminOptions(credentials, adminToken),
    );
  },

  getBatch(credentials: BasicAuthCredentials, adminToken: string, batchId: number | string) {
    return apiRequest<AdminAiSuggestionBatchDetailResponse>(
      `/api/admin/ai-suggestions/batches/${batchId}`,
      adminOptions(credentials, adminToken),
    );
  },

  listFailures(
    credentials: BasicAuthCredentials,
    adminToken: string,
    filters: AdminAiFailureFilters = {},
  ) {
    return apiRequest<AdminPageResponse<AdminAiSuggestionBatchRowResponse>>(
      withQuery('/api/admin/ai-suggestions/failures', filters),
      adminOptions(credentials, adminToken),
    );
  },

  getUsageSummary(
    credentials: BasicAuthCredentials,
    adminToken: string,
    filters: AdminUsageSummaryFilters = {},
  ) {
    return apiRequest<AdminAiSuggestionUsageSummaryResponse>(
      withQuery('/api/admin/ai-suggestions/usage-summary', filters),
      adminOptions(credentials, adminToken),
    );
  },

  runScheduledRefresh(credentials: BasicAuthCredentials, adminToken: string) {
    return apiRequest<AiSuggestionRefreshJobResponse>(
      '/api/admin/ai-suggestions/scheduled-refresh/run',
      {
        ...adminOptions(credentials, adminToken),
        method: 'POST',
      },
    );
  },

  listRefreshJobs(
    credentials: BasicAuthCredentials,
    adminToken: string,
    filters: AdminAiRefreshJobFilters = {},
  ) {
    return apiRequest<AdminPageResponse<AiSuggestionRefreshJobResponse>>(
      withQuery('/api/admin/ai-suggestions/refresh-jobs', filters),
      adminOptions(credentials, adminToken),
    );
  },

  getRefreshJob(credentials: BasicAuthCredentials, adminToken: string, jobId: number | string) {
    return apiRequest<AiSuggestionRefreshJobResponse>(
      `/api/admin/ai-suggestions/refresh-jobs/${jobId}`,
      adminOptions(credentials, adminToken),
    );
  },

  runBackfill(
    credentials: BasicAuthCredentials,
    adminToken: string,
    request: AdminBackfillRequest,
  ) {
    return apiRequest<BackfillResultDto>('/api/admin/stocks/backfill', {
      ...adminOptions(credentials, adminToken),
      method: 'POST',
      body: request,
    });
  },
};
