import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react';

import { authApi } from '@/api/auth';
import { ApiError, normalizeUnknownApiError } from '@/api/errors';
import type { AuthUserResponse, BasicAuthCredentials } from '@/types/auth';

type AuthSessionStatus = 'idle' | 'loading' | 'authenticated' | 'unauthenticated' | 'error';

type AuthSessionContextValue = {
  credentials: BasicAuthCredentials | null;
  user: AuthUserResponse | null;
  adminToken: string | null;
  status: AuthSessionStatus;
  error: ApiError | null;
  isAuthenticated: boolean;
  signIn: (credentials: BasicAuthCredentials) => Promise<AuthUserResponse>;
  bootstrap: () => Promise<AuthUserResponse | null>;
  setAdminToken: (token: string | null) => void;
  clearSession: () => void;
};

const AuthSessionContext = createContext<AuthSessionContextValue | null>(null);

export function AuthSessionProvider({ children }: PropsWithChildren) {
  const [credentials, setCredentials] = useState<BasicAuthCredentials | null>(null);
  const [user, setUser] = useState<AuthUserResponse | null>(null);
  const [adminToken, setAdminToken] = useState<string | null>(null);
  const [status, setStatus] = useState<AuthSessionStatus>('idle');
  const [error, setError] = useState<ApiError | null>(null);

  const clearSession = useCallback(() => {
    setCredentials(null);
    setUser(null);
    setAdminToken(null);
    setStatus('unauthenticated');
    setError(null);
  }, []);

  const signIn = useCallback(async (nextCredentials: BasicAuthCredentials) => {
    setStatus('loading');
    setError(null);

    try {
      const response = await authApi.login(nextCredentials);
      setCredentials(nextCredentials);
      setUser(response);
      setStatus('authenticated');
      return response;
    } catch (caughtError) {
      const normalizedError = normalizeUnknownApiError(caughtError);
      setCredentials(null);
      setUser(null);
      setAdminToken(null);
      setStatus('error');
      setError(normalizedError);
      throw normalizedError;
    }
  }, []);

  const bootstrap = useCallback(async () => {
    if (!credentials) {
      setStatus('unauthenticated');
      return null;
    }

    setStatus('loading');
    setError(null);

    try {
      const response = await authApi.me(credentials);
      setUser(response);
      setStatus('authenticated');
      return response;
    } catch (caughtError) {
      const normalizedError = normalizeUnknownApiError(caughtError);
      clearSession();
      setStatus('error');
      setError(normalizedError);
      throw normalizedError;
    }
  }, [clearSession, credentials]);

  const value = useMemo<AuthSessionContextValue>(
    () => ({
      credentials,
      user,
      adminToken,
      status,
      error,
      isAuthenticated: Boolean(credentials && user),
      signIn,
      bootstrap,
      setAdminToken,
      clearSession,
    }),
    [adminToken, bootstrap, clearSession, credentials, error, signIn, status, user],
  );

  return <AuthSessionContext.Provider value={value}>{children}</AuthSessionContext.Provider>;
}

export function useAuthSession() {
  const value = useContext(AuthSessionContext);

  if (!value) {
    throw new Error('useAuthSession must be used within AuthSessionProvider');
  }

  return value;
}
