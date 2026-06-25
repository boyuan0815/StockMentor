import { useFocusEffect } from '@react-navigation/native';
import { useRouter } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { profileApi } from '@/api/profile';
import { ActionButton } from '@/components/foundation/action-button';
import { ConfirmationPanel } from '@/components/foundation/confirmation-panel';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { PaperHeader } from '@/components/paper-trading/paper-trading-ui';
import { ProfileSummaryCard } from '@/components/profile/profile-summary-card';
import { Colors, Radius, Spacing } from '@/constants/theme';
import { useAuthSession } from '@/providers/auth-session-provider';
import type { UserProfileResponse } from '@/types/profile';
import { getApiErrorMessage } from '@/utils/api-error-copy';
import { getPostAuthRoute } from '@/utils/auth-routing';

export function ProfileScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const {
    clearSession,
    credentials,
    refreshUser,
    startOnboardingRetake,
    user,
  } = useAuthSession();
  const [profile, setProfile] = useState<UserProfileResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isRefreshingUser, setIsRefreshingUser] = useState(false);
  const [isRetakeConfirming, setIsRetakeConfirming] = useState(false);
  const [isRetakeStarting, setIsRetakeStarting] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const scrollRef = useRef<ScrollView | null>(null);
  const hasLoadedRef = useRef(false);

  const loadProfile = useCallback(async (mode: 'soft' | 'refresh' = 'soft') => {
    if (!credentials) {
      setErrorMessage('Sign in again to load your profile.');
      setIsLoading(false);
      setIsRefreshing(false);
      return;
    }

    if (mode === 'refresh') {
      setIsRefreshing(true);
    } else if (!hasLoadedRef.current) {
      setIsLoading(true);
    }
    setErrorMessage(null);

    try {
      const response = await profileApi.getProfile(credentials);
      setProfile(response);
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error, 'profile'));
    } finally {
      hasLoadedRef.current = true;
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [credentials]);

  useFocusEffect(
    useCallback(() => {
      scrollRef.current?.scrollTo({ animated: false, y: 0 });
      void loadProfile('soft');
      return undefined;
    }, [loadProfile]),
  );

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

  const handleStartRetake = () => {
    if (isRetakeStarting) {
      return;
    }

    setIsRetakeStarting(true);
    startOnboardingRetake();
    router.push('/onboarding');
  };

  const handleLogout = () => {
    if (isLoggingOut) {
      return;
    }

    setIsLoggingOut(true);
    clearSession();
    router.replace('/');
  };

  const hasProfile = Boolean(profile?.investmentProfile);

  return (
    <View style={styles.container}>
      <View style={[styles.fixedHeader, { paddingTop: insets.top + 2 }]}>
        <PaperHeader brandIcon onRefresh={() => void loadProfile('refresh')} title="Profile" />
      </View>
      <ScrollView
        ref={scrollRef}
        contentContainerStyle={[
          styles.content,
          { paddingBottom: Math.max(Spacing.xxl, insets.bottom + Spacing.xl) },
        ]}
        contentInsetAdjustmentBehavior="never"
        overScrollMode="never"
        refreshControl={<RefreshControl onRefresh={() => void loadProfile('refresh')} refreshing={isRefreshing} />}
        style={styles.scroller}>
        <View style={styles.inner}>
        {errorMessage ? <ErrorBanner title="Profile needs attention" message={errorMessage} /> : null}

        <View style={styles.accountCard}>
          <Text selectable style={styles.accountLabel}>
            Signed in as
          </Text>
          <Text selectable style={styles.accountValue}>
            {user?.username || profile?.username || 'Unknown user'}
          </Text>
          <Text selectable style={styles.accountMeta}>
            {user?.email || profile?.email || 'Email unavailable'}
          </Text>
        </View>

        {isLoading ? (
          <EmptyState title="Loading profile" description="StockMentor is loading your saved profile." />
        ) : hasProfile ? (
          <ProfileSummaryCard
            behaviorSummary={profile?.behaviorSummary}
            investmentProfile={profile?.investmentProfile ?? null}
          />
        ) : (
          <View style={styles.stack}>
            <EmptyState
              title="Profile not found"
              description="The backend returned no investment profile. Refresh account state, then return to onboarding if StockMentor asks you to complete it."
            />
            <ActionButton
              disabled={isLoading || isRefreshingUser}
              label={isLoading ? 'Loading profile...' : 'Try loading profile again'}
              onPress={() => void loadProfile('refresh')}
              variant="secondary"
            />
            <ActionButton
              disabled={isRefreshingUser}
              label={isRefreshingUser ? 'Refreshing...' : 'Refresh account state'}
              onPress={handleRefreshUser}
              variant="ghost"
            />
          </View>
        )}

        {isRetakeConfirming ? (
          <ConfirmationPanel
            confirmLabel="Start retake"
            message="A retake will open the quiz again. StockMentor will only save a new profile version after you finish every question."
            onCancel={() => setIsRetakeConfirming(false)}
            onConfirm={handleStartRetake}
            pending={isRetakeStarting}
            pendingLabel="Opening quiz..."
            title="Retake onboarding?"
          />
        ) : (
          <View style={styles.actions}>
            <ActionButton
              disabled={!hasProfile || isRetakeStarting || isLoggingOut}
              label="Retake onboarding"
              onPress={() => setIsRetakeConfirming(true)}
              variant="secondary"
            />
            <ActionButton
              disabled={isLoggingOut}
              label={isLoggingOut ? 'Logging out...' : 'Log out'}
              onPress={handleLogout}
              variant="ghost"
            />
          </View>
        )}
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  content: {
    gap: 0,
    paddingHorizontal: 0,
    width: '100%',
  },
  fixedHeader: {
    backgroundColor: Colors.light.background,
    borderBottomColor: Colors.light.border,
    borderBottomWidth: 1,
  },
  scroller: {
    backgroundColor: Colors.light.background,
    flex: 1,
  },
  inner: {
    alignSelf: 'center',
    gap: Spacing.xl,
    maxWidth: 620,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
    width: '100%',
  },
  accountCard: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.xs,
    padding: Spacing.lg,
  },
  accountLabel: {
    color: Colors.light.secondaryTint,
    fontSize: 12,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  accountValue: {
    color: Colors.light.text,
    fontSize: 20,
    fontWeight: '800',
  },
  accountMeta: {
    color: Colors.light.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
  stack: {
    gap: Spacing.md,
  },
  actions: {
    gap: Spacing.sm,
  },
});
