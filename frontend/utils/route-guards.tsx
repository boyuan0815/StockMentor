import { Redirect, type Href } from 'expo-router';
import type { PropsWithChildren } from 'react';

import { PlaceholderScreen } from '@/components/foundation/placeholder-screen';
import { useAuthSession } from '@/providers/auth-session-provider';
import type { AuthRole } from '@/types/auth';
import { getPostAuthRoute } from '@/utils/auth-routing';

type ProtectedRouteProps = PropsWithChildren<{
  allowedRoles?: AuthRole[];
  requireOnboarding?: boolean;
  requireCompletedOnboarding?: boolean;
}>;

export function PublicOnlyRoute({ children }: PropsWithChildren) {
  const { isAuthenticated, user, status } = useAuthSession();

  if (status === 'loading') {
    return <RouteLoadingScreen />;
  }

  if (isAuthenticated && user) {
    return <Redirect href={getPostAuthRoute(user)} />;
  }

  return <>{children}</>;
}

export function ProtectedRoute({
  allowedRoles,
  children,
  requireOnboarding = false,
  requireCompletedOnboarding = false,
}: ProtectedRouteProps) {
  const { isAuthenticated, user, status } = useAuthSession();

  if (status === 'loading') {
    return <RouteLoadingScreen />;
  }

  if (!isAuthenticated || !user) {
    return <Redirect href={'/login' as Href} />;
  }

  if (allowedRoles && (!user.role || !allowedRoles.includes(user.role))) {
    return <Redirect href={getPostAuthRoute(user)} />;
  }

  if (requireOnboarding && !user.mustCompleteOnboarding) {
    return <Redirect href={getPostAuthRoute(user)} />;
  }

  if (requireCompletedOnboarding && user.mustCompleteOnboarding) {
    return <Redirect href={'/onboarding' as Href} />;
  }

  return <>{children}</>;
}

function RouteLoadingScreen() {
  return (
    <PlaceholderScreen
      eyebrow="Checking session"
      title="Preparing StockMentor"
      description="StockMentor is checking the current in-memory session before opening this route."
    />
  );
}
