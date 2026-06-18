import { Link, type Href, useRouter } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { profileApi } from '@/api/profile';
import { ActionButton } from '@/components/foundation/action-button';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { PageHeader } from '@/components/foundation/page-header';
import { Screen } from '@/components/foundation/screen';
import { ProfileSummaryCard } from '@/components/profile/profile-summary-card';
import { Spacing } from '@/constants/theme';
import { useAuthSession } from '@/providers/auth-session-provider';
import type { UserProfileResponse } from '@/types/profile';
import { getApiErrorMessage } from '@/utils/api-error-copy';
import { getPostAuthRoute } from '@/utils/auth-routing';

export function OnboardingResultScreen() {
  const router = useRouter();
  const { credentials, latestOnboardingProfile, refreshUser, user } = useAuthSession();
  const [profile, setProfile] = useState<UserProfileResponse | null>(latestOnboardingProfile);
  const [isLoading, setIsLoading] = useState(!latestOnboardingProfile);
  const [isRefreshingUser, setIsRefreshingUser] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadProfile = useCallback(async () => {
    if (!credentials) {
      setErrorMessage('Sign in again to load your saved profile.');
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);

    try {
      const response = await profileApi.getProfile(credentials);
      setProfile(response);
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error, 'profile'));
    } finally {
      setIsLoading(false);
    }
  }, [credentials]);

  useEffect(() => {
    if (!latestOnboardingProfile) {
      void loadProfile();
    }
  }, [latestOnboardingProfile, loadProfile]);

  const handleRefreshUser = async () => {
    if (isRefreshingUser) {
      return;
    }

    setIsRefreshingUser(true);
    setErrorMessage(null);

    try {
      const refreshedUser = await refreshUser();
      if (refreshedUser) {
        router.replace(getPostAuthRoute(refreshedUser));
      }
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error, 'profile'));
    } finally {
      setIsRefreshingUser(false);
    }
  };

  const hasProfile = Boolean(profile?.investmentProfile);

  return (
    <Screen contentStyle={styles.content}>
      <PageHeader
        eyebrow="Profile saved"
        title="Your beginner profile is ready"
        description="StockMentor saved the backend-generated profile from your onboarding answers."
      />

      {errorMessage ? <ErrorBanner title="Profile could not load" message={errorMessage} /> : null}

      {isLoading ? (
        <EmptyState title="Loading profile" description="StockMentor is reading your saved profile." />
      ) : hasProfile ? (
        <ProfileSummaryCard
          behaviorSummary={profile?.behaviorSummary}
          investmentProfile={profile?.investmentProfile ?? null}
        />
      ) : (
        <View style={styles.stack}>
          <EmptyState
            title="Profile needs a refresh"
            description="The backend did not return an investment profile for this account yet."
          />
          <ActionButton
            disabled={isRefreshingUser}
            label={isRefreshingUser ? 'Refreshing...' : 'Refresh account state'}
            onPress={handleRefreshUser}
            variant="secondary"
          />
          {user?.mustCompleteOnboarding ? (
            <Link href={'/onboarding' as Href} asChild>
              <ActionButton label="Return to onboarding" />
            </Link>
          ) : null}
        </View>
      )}

      <View style={styles.actions}>
        <Link href={'/dashboard' as Href} asChild>
          <ActionButton disabled={!hasProfile} label="Go to dashboard" />
        </Link>
        <Link href={'/profile' as Href} asChild>
          <ActionButton label="View full profile" variant="secondary" />
        </Link>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: {
    gap: Spacing.xl,
  },
  stack: {
    gap: Spacing.md,
  },
  actions: {
    gap: Spacing.sm,
  },
});
