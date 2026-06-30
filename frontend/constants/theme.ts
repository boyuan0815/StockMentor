/**
 * Below are the colors that are used in the app. The colors are defined in the light and dark mode.
 * There are many other ways to style your app. For example, [Nativewind](https://www.nativewind.dev/), [Tamagui](https://tamagui.dev/), [unistyles](https://reactnativeunistyles.vercel.app), etc.
 */

import { Platform } from 'react-native';

const tintColorLight = '#2563EB';
const tintColorDark = '#93C5FD';

export const Colors = {
  light: {
    text: '#0F172A',
    mutedText: '#64748B',
    background: '#FFFFFF',
    surface: '#FFFFFF',
    tint: tintColorLight,
    brandNavy: '#052344',
    actionSecondary: '#F59E0B',
    actionSecondarySoft: '#FFF7ED',
    actionSecondaryText: '#92400E',
    secondaryTint: '#0F766E',
    border: '#E2E8F0',
    icon: '#64748B',
    tabIconDefault: '#64748B',
    tabIconSelected: tintColorLight,
    success: '#15803D',
    caution: '#B45309',
    destructive: '#B91C1C',
    softBlue: '#DBEAFE',
    softTeal: '#CCFBF1',
  },
  dark: {
    text: '#F8FAFC',
    mutedText: '#CBD5E1',
    background: '#0F172A',
    surface: '#1E293B',
    tint: tintColorDark,
    brandNavy: '#F8FAFC',
    actionSecondary: '#FBBF24',
    actionSecondarySoft: '#451A03',
    actionSecondaryText: '#FCD34D',
    secondaryTint: '#5EEAD4',
    border: '#334155',
    icon: '#CBD5E1',
    tabIconDefault: '#94A3B8',
    tabIconSelected: tintColorDark,
    success: '#86EFAC',
    caution: '#FCD34D',
    destructive: '#FCA5A5',
    softBlue: '#1E3A8A',
    softTeal: '#134E4A',
  },
};

export const Spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
};

export const Radius = {
  sm: 6,
  md: 8,
};

export const Fonts = Platform.select({
  ios: {
    /** iOS `UIFontDescriptorSystemDesignDefault` */
    sans: 'system-ui',
    /** iOS `UIFontDescriptorSystemDesignSerif` */
    serif: 'ui-serif',
    /** iOS `UIFontDescriptorSystemDesignRounded` */
    rounded: 'ui-rounded',
    /** iOS `UIFontDescriptorSystemDesignMonospaced` */
    mono: 'ui-monospace',
  },
  default: {
    sans: 'normal',
    serif: 'serif',
    rounded: 'normal',
    mono: 'monospace',
  },
  web: {
    sans: "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
    serif: "Georgia, 'Times New Roman', serif",
    rounded: "'SF Pro Rounded', 'Hiragino Maru Gothic ProN', Meiryo, 'MS PGothic', sans-serif",
    mono: "SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace",
  },
});
