import type { AuthUserResponse } from '@/types/auth';

export function getPostAuthRoute(user: AuthUserResponse) {
  if (user.role === 'ADMIN') {
    return '/admin' as const;
  }

  if (user.role === 'BEGINNER_INVESTOR' && user.mustCompleteOnboarding) {
    return '/onboarding' as const;
  }

  if (user.role === 'BEGINNER_INVESTOR') {
    return '/dashboard' as const;
  }

  return '/' as const;
}
