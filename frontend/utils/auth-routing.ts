import type { Href } from 'expo-router';

import type { AuthUserResponse } from '@/types/auth';

export function getPostAuthRoute(user: AuthUserResponse): Href {
  if (user.role === 'ADMIN') {
    return '/admin' as Href;
  }

  if (user.role === 'BEGINNER_INVESTOR' && user.mustCompleteOnboarding) {
    return '/onboarding' as Href;
  }

  if (user.role === 'BEGINNER_INVESTOR') {
    return '/dashboard' as Href;
  }

  return '/' as Href;
}
