import { useEffect, useState } from 'react';

type AuthDiagnosticsRequest = {
  method: string;
  path: string;
  phase: 'pending' | 'success' | 'error';
  status?: number;
  errorCode?: string;
  message?: string;
};

type AuthDiagnosticsState = {
  apiBaseUrl: string;
  lastAuthRequest: AuthDiagnosticsRequest | null;
};

type Listener = (state: AuthDiagnosticsState) => void;

let diagnosticsState: AuthDiagnosticsState = {
  apiBaseUrl: getSanitizedApiBaseUrl(),
  lastAuthRequest: null,
};

const listeners = new Set<Listener>();

export function recordAuthRequestStart(method: string, path: string) {
  if (!__DEV__ || !isAuthPath(path)) {
    return;
  }

  updateDiagnostics({
    lastAuthRequest: {
      method,
      path,
      phase: 'pending',
    },
  });
}

export function recordAuthRequestSuccess(method: string, path: string, status: number) {
  if (!__DEV__ || !isAuthPath(path)) {
    return;
  }

  updateDiagnostics({
    lastAuthRequest: {
      method,
      path,
      phase: 'success',
      status,
    },
  });
}

export function recordAuthRequestError({
  errorCode,
  message,
  method,
  path,
  status,
}: {
  method: string;
  path: string;
  status?: number;
  errorCode?: string;
  message?: string;
}) {
  if (!__DEV__ || !isAuthPath(path)) {
    return;
  }

  updateDiagnostics({
    lastAuthRequest: {
      method,
      path,
      phase: 'error',
      status,
      errorCode,
      message,
    },
  });
}

export function useAuthDiagnostics() {
  const [state, setState] = useState(getAuthDiagnosticsSnapshot);

  useEffect(() => {
    if (!__DEV__) {
      return undefined;
    }

    listeners.add(setState);
    setState(getAuthDiagnosticsSnapshot());

    return () => {
      listeners.delete(setState);
    };
  }, []);

  return state;
}

function updateDiagnostics(nextState: Partial<AuthDiagnosticsState>) {
  diagnosticsState = {
    ...diagnosticsState,
    ...nextState,
    apiBaseUrl: getSanitizedApiBaseUrl(),
  };

  listeners.forEach((listener) => listener(diagnosticsState));
}

function getAuthDiagnosticsSnapshot() {
  return {
    ...diagnosticsState,
    apiBaseUrl: getSanitizedApiBaseUrl(),
  };
}

function isAuthPath(path: string) {
  return path.startsWith('/api/auth/');
}

function getSanitizedApiBaseUrl() {
  const rawBaseUrl = process.env.EXPO_PUBLIC_API_BASE_URL?.trim();

  if (!rawBaseUrl) {
    return 'not configured';
  }

  try {
    const url = new URL(rawBaseUrl);
    url.username = '';
    url.password = '';
    url.pathname = '';
    url.search = '';
    url.hash = '';
    return url.toString().replace(/\/$/, '');
  } catch {
    return 'invalid URL';
  }
}
