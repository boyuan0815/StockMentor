import type { BackendErrorBody, NormalizedApiError } from '@/types/api';

type ApiErrorInput = NormalizedApiError & {
  cause?: unknown;
};

export class ApiError extends Error implements NormalizedApiError {
  status: number;
  code?: NormalizedApiError['code'];
  field?: string;
  retryable: boolean;

  constructor({ status, message, code, field, retryable, cause }: ApiErrorInput) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.field = field;
    this.retryable = retryable;

    if (cause !== undefined) {
      (this as Error & { cause?: unknown }).cause = cause;
    }
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function isAbortError(error: unknown) {
  if (typeof DOMException !== 'undefined' && error instanceof DOMException) {
    return error.name === 'AbortError';
  }

  return error instanceof Error && error.name === 'AbortError';
}

export function createHttpApiError(status: number, body: BackendErrorBody | null) {
  const message = body?.message || `Request failed with status ${status}`;

  return new ApiError({
    status,
    message,
    code: body?.code || 'HTTP_ERROR',
    field: body?.field,
    retryable: status === 0 || status >= 500 || status === 408 || status === 429,
  });
}

export function normalizeUnknownApiError(error: unknown) {
  if (isApiError(error)) {
    return error;
  }

  if (isAbortError(error)) {
    return new ApiError({
      status: 0,
      message: 'Request was cancelled.',
      code: 'ABORTED',
      retryable: false,
      cause: error,
    });
  }

  return new ApiError({
    status: 0,
    message: 'Unable to reach the StockMentor backend. Check the backend URL and network connection.',
    code: 'NETWORK_ERROR',
    retryable: true,
    cause: error,
  });
}
