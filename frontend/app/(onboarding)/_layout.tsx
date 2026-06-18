import { Stack } from 'expo-router';

import { ProtectedRoute } from '@/utils/route-guards';

export default function OnboardingLayout() {
  return (
    <ProtectedRoute allowedRoles={['BEGINNER_INVESTOR']}>
      <Stack>
        <Stack.Screen name="onboarding/index" options={{ title: 'Onboarding' }} />
        <Stack.Screen name="onboarding/result" options={{ title: 'Profile result' }} />
      </Stack>
    </ProtectedRoute>
  );
}
