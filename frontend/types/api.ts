export type ApiErrorCode =
  | 'HTTP_ERROR'
  | 'NETWORK_ERROR'
  | 'TIMEOUT'
  | 'ABORTED'
  | 'MISSING_BASE_URL'
  | 'INVALID_BASE_URL'
  | 'UNKNOWN_ERROR';

export type NormalizedApiError = {
  status: number;
  message: string;
  code?: ApiErrorCode | string;
  field?: string;
  fields?: Record<string, string>;
  retryable: boolean;
};

export type BackendErrorBody = {
  status?: number;
  message?: string;
  error?: string;
  detail?: string;
  title?: string;
  code?: string;
  field?: string;
  fields?: Record<string, string>;
};
