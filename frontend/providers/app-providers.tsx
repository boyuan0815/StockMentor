import type { PropsWithChildren } from 'react';
import { SafeAreaProvider, initialWindowMetrics } from 'react-native-safe-area-context';

import { AuthSessionProvider } from '@/providers/auth-session-provider';
import { StockMentorThemeProvider } from '@/providers/stockmentor-theme-provider';
import { ToastProvider } from '@/providers/toast-provider';

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <SafeAreaProvider initialMetrics={initialWindowMetrics}>
      <AuthSessionProvider>
        <StockMentorThemeProvider>
          <ToastProvider>{children}</ToastProvider>
        </StockMentorThemeProvider>
      </AuthSessionProvider>
    </SafeAreaProvider>
  );
}
