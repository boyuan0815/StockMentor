import { ApiError } from '@/api/errors';

export const DEFAULT_API_TIMEOUT_MS = 15000;

export function getApiBaseUrl() {
  const baseUrl = process.env.EXPO_PUBLIC_API_BASE_URL?.trim();

  if (!baseUrl) {
    throw new ApiError({
      status: 0,
      message: 'EXPO_PUBLIC_API_BASE_URL is not configured.',
      code: 'MISSING_BASE_URL',
      retryable: false,
    });
  }

  return baseUrl.replace(/\/+$/, '');
}

export function buildApiUrl(path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;

  return `${getApiBaseUrl()}${normalizedPath}`;
}
