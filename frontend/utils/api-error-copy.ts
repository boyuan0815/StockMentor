import { normalizeUnknownApiError } from '@/api/errors';

type ErrorCopyContext = 'login' | 'register' | 'profile' | 'onboarding' | 'logout' | 'generic';

const CONNECTION_HINT =
  'Check EXPO_PUBLIC_API_BASE_URL and that the backend is reachable from this device.';

export function getApiErrorMessage(error: unknown, context: ErrorCopyContext = 'generic') {
  const apiError = normalizeUnknownApiError(error);

  if (apiError.code === 'MISSING_BASE_URL') {
    return `StockMentor does not have a backend address configured. ${CONNECTION_HINT}`;
  }

  if (apiError.code === 'TIMEOUT') {
    return `The backend did not respond in time. ${CONNECTION_HINT}`;
  }

  if (apiError.code === 'NETWORK_ERROR' || apiError.status === 0) {
    return `StockMentor could not reach the backend. ${CONNECTION_HINT}`;
  }

  if (apiError.status === 401) {
    if (context === 'login') {
      return 'The email, username, or password was not accepted. Check the details and try again.';
    }

    return 'Your session could not be verified. Sign in again to continue.';
  }

  if (apiError.status === 403) {
    return 'This account does not have access to that screen or action.';
  }

  if (apiError.status === 409) {
    if (context === 'register') {
      return 'That email or username is already registered. Use a different value or sign in instead.';
    }

    if (context === 'onboarding') {
      return 'The account state changed before StockMentor could save these answers. Refresh the account state and try again.';
    }
  }

  if (context === 'register' && apiError.status === 400) {
    return apiError.message || 'Check the account details and try again.';
  }

  return apiError.message;
}
