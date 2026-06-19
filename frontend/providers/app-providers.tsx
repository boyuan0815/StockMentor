import type { PropsWithChildren } from 'react';
import { SafeAreaProvider, initialWindowMetrics } from 'react-native-safe-area-context';

import { AuthSessionProvider } from '@/providers/auth-session-provider';
import { StockMentorThemeProvider } from '@/providers/stockmentor-theme-provider';

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <SafeAreaProvider initialMetrics={initialWindowMetrics}>
      <AuthSessionProvider>
        <StockMentorThemeProvider>{children}</StockMentorThemeProvider>
      </AuthSessionProvider>
    </SafeAreaProvider>
  );
}
