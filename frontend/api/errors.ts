import type { BackendErrorBody, NormalizedApiError } from '@/types/api';

type ApiErrorInput = NormalizedApiError & {
  cause?: unknown;
};

export class ApiError extends Error implements NormalizedApiError {
  status: number;
  code?: NormalizedApiError['code'];
  field?: string;
  fields?: Record<string, string>;
  retryable: boolean;

  constructor({ status, message, code, field, fields, retryable, cause }: ApiErrorInput) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.field = field;
    this.fields = fields;
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

export function createHttpApiError(status: number, body: BackendErrorBody | string | null) {
  const parsedBody = typeof body === 'string' ? { message: body } : body;
  const message =
    parsedBody?.message ||
    parsedBody?.detail ||
    parsedBody?.error ||
    parsedBody?.title ||
    getDefaultHttpErrorMessage(status);

  return new ApiError({
    status,
    message,
    code: parsedBody?.code || 'HTTP_ERROR',
    field: parsedBody?.field,
    fields: parsedBody?.fields,
    retryable: status === 0 || status >= 500 || status === 408 || status === 429,
  });
}

function getDefaultHttpErrorMessage(status: number) {
  switch (status) {
    case 400:
      return 'The request could not be accepted. Check the details and try again.';
    case 401:
      return 'The sign-in details were not accepted.';
    case 403:
      return 'This account does not have access to that action.';
    case 404:
      return 'The requested StockMentor resource was not found.';
    case 409:
      return 'That change conflicts with the current account state.';
    case 429:
      return 'Too many requests were sent. Wait a moment and try again.';
    default:
      if (status >= 500) {
        return 'The StockMentor backend is unavailable right now.';
      }

      return `Request failed with status ${status}`;
  }
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
