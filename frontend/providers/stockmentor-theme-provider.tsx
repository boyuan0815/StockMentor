import { DefaultTheme, ThemeProvider } from '@react-navigation/native';
import type { PropsWithChildren } from 'react';

import { Colors } from '@/constants/theme';

const navigationTheme = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    primary: Colors.light.tint,
    background: Colors.light.background,
    card: Colors.light.surface,
    text: Colors.light.text,
    border: Colors.light.border,
    notification: Colors.light.caution,
  },
};

export function StockMentorThemeProvider({ children }: PropsWithChildren) {
  return <ThemeProvider value={navigationTheme}>{children}</ThemeProvider>;
}
