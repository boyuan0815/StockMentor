import { Stack } from 'expo-router';

import { ProtectedRoute } from '@/utils/route-guards';

export default function OnboardingLayout() {
  return (
    <ProtectedRoute allowedRoles={['BEGINNER_INVESTOR']}>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="onboarding/index" />
        <Stack.Screen name="onboarding/result" />
      </Stack>
    </ProtectedRoute>
  );
}
