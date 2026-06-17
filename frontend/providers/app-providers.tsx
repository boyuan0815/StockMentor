import type { PropsWithChildren } from 'react';

import { AuthSessionProvider } from '@/providers/auth-session-provider';
import { StockMentorThemeProvider } from '@/providers/stockmentor-theme-provider';

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <AuthSessionProvider>
      <StockMentorThemeProvider>{children}</StockMentorThemeProvider>
    </AuthSessionProvider>
  );
}
