import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react';

import { authApi, type RegisterRequest } from '@/api/auth';
import { ApiError, normalizeUnknownApiError } from '@/api/errors';
import type {
  AuthUserResponse,
  BasicAuthCredentials,
  OnboardingMode,
} from '@/types/auth';
import type { UserProfileResponse } from '@/types/profile';

type AuthSessionStatus = 'idle' | 'loading' | 'authenticated' | 'unauthenticated' | 'error';

type AuthSessionContextValue = {
  credentials: BasicAuthCredentials | null;
  user: AuthUserResponse | null;
  adminToken: string | null;
  status: AuthSessionStatus;
  isBootstrapping: boolean;
  error: ApiError | null;
  isAuthenticated: boolean;
  signIn: (credentials: BasicAuthCredentials) => Promise<AuthUserResponse>;
  register: (request: RegisterRequest) => Promise<AuthUserResponse>;
  bootstrap: () => Promise<AuthUserResponse | null>;
  refreshUser: () => Promise<AuthUserResponse | null>;
  onboardingMode: OnboardingMode;
  latestOnboardingProfile: UserProfileResponse | null;
  startOnboardingRetake: () => void;
  finishOnboardingFlow: (profile?: UserProfileResponse | null) => void;
  clearLatestOnboardingProfile: () => void;
  setAdminToken: (token: string | null) => void;
  clearSession: () => void;
};

const AuthSessionContext = createContext<AuthSessionContextValue | null>(null);

export function AuthSessionProvider({ children }: PropsWithChildren) {
  const [credentials, setCredentials] = useState<BasicAuthCredentials | null>(null);
  const [user, setUser] = useState<AuthUserResponse | null>(null);
  const [adminToken, setAdminToken] = useState<string | null>(null);
  const [status, setStatus] = useState<AuthSessionStatus>('idle');
  const [isBootstrapping, setIsBootstrapping] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [onboardingMode, setOnboardingMode] = useState<OnboardingMode>('initial');
  const [latestOnboardingProfile, setLatestOnboardingProfile] =
    useState<UserProfileResponse | null>(null);

  const clearSession = useCallback(() => {
    setCredentials(null);
    setUser(null);
    setAdminToken(null);
    setOnboardingMode('initial');
    setLatestOnboardingProfile(null);
    setIsBootstrapping(false);
    setStatus('unauthenticated');
    setError(null);
  }, []);

  const signIn = useCallback(async (nextCredentials: BasicAuthCredentials) => {
    setError(null);

    try {
      const response = await authApi.login(nextCredentials);
      setCredentials(nextCredentials);
      setUser(response);
      setOnboardingMode('initial');
      setLatestOnboardingProfile(null);
      setStatus('authenticated');
      return response;
    } catch (caughtError) {
      const normalizedError = normalizeUnknownApiError(caughtError);
      setCredentials(null);
      setUser(null);
      setAdminToken(null);
      setOnboardingMode('initial');
      setLatestOnboardingProfile(null);
      setStatus('unauthenticated');
      setError(normalizedError);
      throw normalizedError;
    }
  }, []);

  const register = useCallback(async (request: RegisterRequest) => {
    setError(null);

    try {
      const response = await authApi.register(request);
      const canonicalUsername = response.email || response.username || request.email.trim();

      setCredentials({
        username: canonicalUsername,
        password: request.password,
      });
      setUser(response);
      setAdminToken(null);
      setOnboardingMode('initial');
      setLatestOnboardingProfile(null);
      setStatus('authenticated');
      return response;
    } catch (caughtError) {
      const normalizedError = normalizeUnknownApiError(caughtError);
      setCredentials(null);
      setUser(null);
      setAdminToken(null);
      setOnboardingMode('initial');
      setLatestOnboardingProfile(null);
      setStatus('unauthenticated');
      setError(normalizedError);
      throw normalizedError;
    }
  }, []);

  const bootstrap = useCallback(async () => {
    if (!credentials) {
      setStatus('unauthenticated');
      return null;
    }

    setIsBootstrapping(true);
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
    } finally {
      setIsBootstrapping(false);
    }
  }, [clearSession, credentials]);

  const refreshUser = useCallback(async () => {
    if (!credentials) {
      setStatus('unauthenticated');
      return null;
    }

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

  const startOnboardingRetake = useCallback(() => {
    setOnboardingMode('retake');
    setLatestOnboardingProfile(null);
  }, []);

  const finishOnboardingFlow = useCallback((profile?: UserProfileResponse | null) => {
    setOnboardingMode('initial');
    setLatestOnboardingProfile(profile ?? null);
  }, []);

  const clearLatestOnboardingProfile = useCallback(() => {
    setLatestOnboardingProfile(null);
  }, []);

  const value = useMemo<AuthSessionContextValue>(
    () => ({
      credentials,
      user,
      adminToken,
      status,
      isBootstrapping,
      error,
      isAuthenticated: Boolean(credentials && user),
      signIn,
      register,
      bootstrap,
      refreshUser,
      onboardingMode,
      latestOnboardingProfile,
      startOnboardingRetake,
      finishOnboardingFlow,
      clearLatestOnboardingProfile,
      setAdminToken,
      clearSession,
    }),
    [
      adminToken,
      bootstrap,
      clearLatestOnboardingProfile,
      clearSession,
      credentials,
      error,
      finishOnboardingFlow,
      isBootstrapping,
      latestOnboardingProfile,
      onboardingMode,
      refreshUser,
      register,
      signIn,
      startOnboardingRetake,
      status,
      user,
    ],
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
