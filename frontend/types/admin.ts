export type AdminPageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type AdminUserRole = 'BEGINNER_INVESTOR' | 'ADMIN';
export type AdminUserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
export type AdminMutableUserStatus = 'ACTIVE' | 'INACTIVE';

export type AdminUserListItemResponse = {
  userId: number;
  email: string;
  username: string;
  role: AdminUserRole | null;
  status: AdminUserStatus | null;
  isDeleted: boolean | null;
  onboardingCompleted: boolean | null;
  hasInvestmentProfile: boolean | null;
  createdAt: string | null;
  updatedAt: string | null;
  lastLoginAt: string | null;
};

export type AdminInvestmentProfileSummaryResponse = {
  profileId: number;
  profileVersion: number | null;
  profileSource: string | null;
  riskTolerance: string | null;
  investmentGoal: string | null;
  experienceLevel: string | null;
  preferredVolatility: string | null;
  preferredHorizon: string | null;
  riskScore: number | null;
  goalScore: number | null;
  experienceScore: number | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type AdminBehaviorProfileSummaryResponse = {
  behaviorProfileId: number;
  behaviorConfidence: string | null;
  behaviorStyle: string | null;
  behaviorRiskScore: number | null;
  behaviorSummaryText: string | null;
  sourceNote: string | null;
  updatedAt: string | null;
};

export type AdminPaperTradingSummaryResponse = {
  accountStatus: string | null;
  cashBalance: number | string | null;
  realizedProfitLoss: number | string | null;
  currentSessionNumber: number | null;
  positionCount: number;
  transactionCount: number;
  lastResetAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type AdminUserDetailResponse = {
  user: AdminUserListItemResponse;
  latestInvestmentProfile: AdminInvestmentProfileSummaryResponse | null;
  behaviorSummary: AdminBehaviorProfileSummaryResponse | null;
  paperTradingSummary: AdminPaperTradingSummaryResponse | null;
};

export type AdminUpdateUserStatusRequest = {
  status: AdminMutableUserStatus;
};

export type AdminAiSuggestionBatchStatus =
  | 'SUCCESS'
  | 'FAILED'
  | 'FALLBACK_CACHED'
  | 'FALLBACK_RULE_BASED';

export type AdminAiSuggestionTriggerReason =
  | 'ONBOARDING_COMPLETED'
  | 'RETAKE_QUIZ'
  | 'MANUAL_REFRESH'
  | 'SCHEDULED_REFRESH'
  | 'NO_ACTIVE_SUGGESTION';

export type AdminAiRefreshJobStatus = 'RUNNING' | 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED';
export type AdminAiRefreshTriggeredBy = 'SCHEDULED' | 'ADMIN_MANUAL';

export type AdminAiSuggestionBatchRowResponse = {
  batchId: number;
  userId: number;
  userEmail: string;
  status: AdminAiSuggestionBatchStatus | string | null;
  triggerReason: AdminAiSuggestionTriggerReason | string | null;
  analysisTimeframe: string | null;
  model: string | null;
  promptVersion: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  finishReason: string | null;
  fallbackUsed: boolean | null;
  errorMessage: string | null;
  createdAt: string | null;
  expiresAt: string | null;
  suggestedSymbols: string[];
  itemCount: number | null;
};

export type AdminAiSuggestionItemResponse = {
  itemId: number;
  symbol: string;
  rankNo: number | null;
  matchScore: number | null;
  riskLevel: string | null;
  suggestionLabel: string | null;
  shortReason: string | null;
  status: string | null;
  snapshotId: number | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type AdminAiSuggestionBatchDetailResponse = {
  batchId: number;
  userId: number;
  userEmail: string;
  username: string;
  status: AdminAiSuggestionBatchStatus | string | null;
  triggerReason: AdminAiSuggestionTriggerReason | string | null;
  analysisTimeframe: string | null;
  model: string | null;
  promptVersion: string | null;
  profileVersion: number | null;
  inputHash: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  finishReason: string | null;
  fallbackUsed: boolean | null;
  errorMessage: string | null;
  createdAt: string | null;
  expiresAt: string | null;
  suggestedSymbols: string[];
  itemCount: number | null;
  items: AdminAiSuggestionItemResponse[];
};

export type AdminAiSuggestionGroupedCountResponse = {
  key: string | null;
  count: number | null;
};

export type AdminAiSuggestionUsageSummaryResponse = {
  totalBatches: number | null;
  successCount: number | null;
  failedCount: number | null;
  fallbackCachedCount: number | null;
  fallbackRuleBasedCount: number | null;
  totalPromptTokens: number | null;
  totalCompletionTokens: number | null;
  totalTokens: number | null;
  groupedByTriggerReason: AdminAiSuggestionGroupedCountResponse[];
  groupedByStatus: AdminAiSuggestionGroupedCountResponse[];
};

export type AiSuggestionRefreshJobResponse = {
  jobId: number;
  status: AdminAiRefreshJobStatus | string | null;
  triggeredBy: AdminAiRefreshTriggeredBy | string | null;
  triggeredByUserId: number | null;
  startedAt: string | null;
  finishedAt: string | null;
  processedUsers: number | null;
  skippedUsers: number | null;
  successCount: number | null;
  reusedCount: number | null;
  fallbackCount: number | null;
  failedCount: number | null;
  message: string | null;
};

export type AdminBackfillType =
  | 'INTRADAY_DATE'
  | 'DAILY_RANGE'
  | 'DAILY_MISSING'
  | 'CLEANUP_1MIN';

export type AdminBackfillRequest = {
  type: AdminBackfillType;
  symbols?: string[];
  date?: string;
  startDate?: string;
  endDate?: string;
};

export type BackfillResultDto = {
  jobType: string | null;
  symbols: string[];
  startDate: string | null;
  endDate: string | null;
  savedRows: number;
  skippedRows: number;
  deletedRows: number;
  messages: string[];
};

export type AdminUserListFilters = {
  search?: string;
  email?: string;
  username?: string;
  role?: AdminUserRole | '';
  status?: AdminUserStatus | '';
  page?: number;
  size?: number;
};

export type AdminAiBatchFilters = {
  userId?: string;
  email?: string;
  status?: AdminAiSuggestionBatchStatus | '';
  triggerReason?: AdminAiSuggestionTriggerReason | '';
  from?: string;
  to?: string;
  page?: number;
  size?: number;
};

export type AdminAiFailureFilters = {
  from?: string;
  to?: string;
  triggerReason?: AdminAiSuggestionTriggerReason | '';
  page?: number;
  size?: number;
};

export type AdminAiRefreshJobFilters = {
  status?: AdminAiRefreshJobStatus | '';
  triggeredBy?: AdminAiRefreshTriggeredBy | '';
  from?: string;
  to?: string;
  page?: number;
  size?: number;
};

export type AdminUsageSummaryFilters = {
  from?: string;
  to?: string;
};
