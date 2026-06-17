export type ApiErrorCode =
  | 'HTTP_ERROR'
  | 'NETWORK_ERROR'
  | 'TIMEOUT'
  | 'ABORTED'
  | 'MISSING_BASE_URL'
  | 'UNKNOWN_ERROR';

export type NormalizedApiError = {
  status: number;
  message: string;
  code?: ApiErrorCode | string;
  field?: string;
  retryable: boolean;
};

export type BackendErrorBody = {
  status?: number;
  message?: string;
  code?: string;
  field?: string;
};
