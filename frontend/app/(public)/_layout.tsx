import { Stack } from 'expo-router';

import { PublicOnlyRoute } from '@/utils/route-guards';

export default function PublicLayout() {
  return (
    <PublicOnlyRoute>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="index" />
      </Stack>
    </PublicOnlyRoute>
  );
}
