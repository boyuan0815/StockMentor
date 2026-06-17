import { Stack } from 'expo-router';

import { PublicOnlyRoute } from '@/utils/route-guards';

export default function AuthLayout() {
  return (
    <PublicOnlyRoute>
      <Stack>
        <Stack.Screen name="login" options={{ title: 'Sign in' }} />
        <Stack.Screen name="register" options={{ title: 'Create account' }} />
      </Stack>
    </PublicOnlyRoute>
  );
}
