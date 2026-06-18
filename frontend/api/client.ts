import { DEFAULT_API_TIMEOUT_MS, buildApiUrl } from '@/api/config';
import {
  recordAuthRequestError,
  recordAuthRequestStart,
  recordAuthRequestSuccess,
} from '@/api/diagnostics';
import { ApiError, createHttpApiError, isApiError, normalizeUnknownApiError } from '@/api/errors';
import { applyAuthHeaders } from '@/api/headers';
import type { BackendErrorBody } from '@/types/api';
import type { BasicAuthCredentials } from '@/types/auth';

type ApiRequestOptions = {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  credentials?: BasicAuthCredentials | null;
  adminToken?: string | null;
  headers?: HeadersInit;
  signal?: AbortSignal;
  timeoutMs?: number;
};

type RequestSignal = {
  signal: AbortSignal;
  cleanup: () => void;
  didTimeout: () => boolean;
};

function createRequestSignal(timeoutMs: number, externalSignal?: AbortSignal): RequestSignal {
  const controller = new AbortController();
  let timedOut = false;

  const timeoutId = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);

  const abortFromExternalSignal = () => controller.abort();
  externalSignal?.addEventListener('abort', abortFromExternalSignal);
  if (externalSignal?.aborted) {
    controller.abort();
  }

  return {
    signal: controller.signal,
    cleanup: () => {
      clearTimeout(timeoutId);
      externalSignal?.removeEventListener('abort', abortFromExternalSignal);
    },
    didTimeout: () => timedOut,
  };
}

async function parseResponseBody(response: Response) {
  if (response.status === 204) {
    return undefined;
  }

  const text = await response.text();

  if (!text) {
    return undefined;
  }

  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const requestSignal = createRequestSignal(options.timeoutMs ?? DEFAULT_API_TIMEOUT_MS, options.signal);
  const method = options.method ?? 'GET';

  headers.set('Accept', 'application/json');
  applyAuthHeaders(headers, options.credentials, options.adminToken);

  const init: RequestInit = {
    method,
    headers,
    signal: requestSignal.signal,
  };

  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json');
    init.body = JSON.stringify(options.body);
  }

  recordAuthRequestStart(method, path);

  try {
    const response = await fetch(buildApiUrl(path), init);
    const responseBody = await parseResponseBody(response);

    if (!response.ok) {
      const httpError = createHttpApiError(response.status, responseBody as BackendErrorBody | string | null);
      throw httpError;
    }

    recordAuthRequestSuccess(method, path, response.status);
    return responseBody as T;
  } catch (error) {
    if (requestSignal.didTimeout()) {
      const timeoutError = new ApiError({
        status: 0,
        message: 'The StockMentor backend did not respond in time.',
        code: 'TIMEOUT',
        retryable: true,
        cause: error,
      });
      recordAuthRequestError({
        method,
        path,
        status: timeoutError.status,
        errorCode: timeoutError.code,
        message: timeoutError.message,
      });
      throw timeoutError;
    }

    if (isApiError(error)) {
      recordAuthRequestError({
        method,
        path,
        status: error.status,
        errorCode: error.code,
        message: error.message,
      });
      throw error;
    }

    const normalizedError = normalizeUnknownApiError(error);
    recordAuthRequestError({
      method,
      path,
      status: normalizedError.status,
      errorCode: normalizedError.code,
      message: normalizedError.message,
    });
    throw normalizedError;
  } finally {
    requestSignal.cleanup();
  }
}
