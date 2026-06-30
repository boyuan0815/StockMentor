import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import 'react-native-reanimated';

import { Colors } from '@/constants/theme';
import { AppProviders } from '@/providers/app-providers';

export const unstable_settings = {
  anchor: '(public)',
};

export default function RootLayout() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <AppProviders>
        <Stack screenOptions={{ headerShown: false }}>
          <Stack.Screen name="(public)" />
          <Stack.Screen name="(auth)" />
          <Stack.Screen name="(onboarding)" />
          <Stack.Screen name="(user)" />
          <Stack.Screen name="(admin)" />
        </Stack>
        <StatusBar backgroundColor={Colors.light.background} style="dark" translucent={false} />
      </AppProviders>
    </GestureHandlerRootView>
  );
}
