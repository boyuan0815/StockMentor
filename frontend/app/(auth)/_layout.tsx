import { Stack } from 'expo-router';

import { PublicOnlyRoute } from '@/utils/route-guards';

export default function AuthLayout() {
  return (
    <PublicOnlyRoute>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="login" />
        <Stack.Screen name="register" />
      </Stack>
    </PublicOnlyRoute>
  );
}
