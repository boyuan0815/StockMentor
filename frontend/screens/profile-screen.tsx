import { useFocusEffect } from '@react-navigation/native';
import { useRouter } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { profileApi } from '@/api/profile';
import { ActionButton } from '@/components/foundation/action-button';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { ConfirmOverlay, PaperHeader } from '@/components/paper-trading/paper-trading-ui';
import { ProfileSummaryCard } from '@/components/profile/profile-summary-card';
import { Colors, Spacing } from '@/constants/theme';
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
  const [isLogoutConfirming, setIsLogoutConfirming] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [profileAnimationKey, setProfileAnimationKey] = useState(0);
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
      setIsRetakeStarting(false);
      setProfileAnimationKey((current) => current + 1);
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

    setIsRetakeConfirming(false);
    setIsRetakeStarting(true);
    startOnboardingRetake();
    router.push('/onboarding');
  };

  const handleLogout = () => {
    if (isLoggingOut) {
      return;
    }

    setIsLogoutConfirming(false);
    setIsLoggingOut(true);
    clearSession();
    router.replace('/');
  };

  const hasProfile = Boolean(profile?.investmentProfile);
  const accountName = user?.username || profile?.username || 'Unknown user';
  const accountInitial = accountName.trim().charAt(0).toUpperCase() || 'S';

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
          <View style={styles.accountGlowLarge} />
          <View style={styles.accountGlowSmall} />
          <View style={styles.accountMark}>
            <Text selectable style={styles.accountInitial}>
              {accountInitial}
            </Text>
          </View>
          <View style={styles.accountDivider} />
          <View style={styles.accountCopy}>
            <View style={styles.accountStatusRow}>
              <Text selectable style={styles.accountLabel}>
                Signed in as
              </Text>
              <View style={styles.activeBadge}>
                <View style={styles.activeDot} />
                <Text selectable style={styles.activeText}>
                  Active
                </Text>
              </View>
            </View>
            <Text selectable style={styles.accountValue}>
              {accountName}
            </Text>
            <Text selectable style={styles.accountMeta}>
              {user?.email || profile?.email || 'Email unavailable'}
            </Text>
          </View>
        </View>

        {isLoading ? (
          <EmptyState title="Loading profile" description="StockMentor is loading your saved profile." />
        ) : hasProfile ? (
          <ProfileSummaryCard
            animationKey={profileAnimationKey}
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

        <View style={styles.actions}>
          <ActionButton
            disabled={!hasProfile || isLoggingOut || isRetakeStarting}
            label="Retake Quiz"
            onPress={() => setIsRetakeConfirming(true)}
          />
          <ActionButton
            disabled={isLoggingOut}
            label={isLoggingOut ? 'Logging out...' : 'Log out'}
            onPress={() => setIsLogoutConfirming(true)}
            variant="secondary"
          />
        </View>
        </View>
      </ScrollView>
      <ConfirmOverlay
        confirmLabel="Start quiz"
        onCancel={() => setIsRetakeConfirming(false)}
        onConfirm={handleStartRetake}
        pending={isRetakeStarting}
        pendingLabel="Opening quiz..."
        title="Retake quiz?"
        visible={isRetakeConfirming}
      />
      <ConfirmOverlay
        confirmLabel="Log out"
        onCancel={() => setIsLogoutConfirming(false)}
        onConfirm={handleLogout}
        pending={isLoggingOut}
        pendingLabel="Logging out..."
        title="Log out?"
        visible={isLogoutConfirming}
      />
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
    gap: Spacing.md,
    maxWidth: 620,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
    width: '100%',
  },
  accountCard: {
    alignItems: 'center',
    backgroundColor: '#13294B',
    borderRadius: 20,
    flexDirection: 'row',
    gap: Spacing.md,
    overflow: 'hidden',
    padding: Spacing.lg,
  },
  accountMark: {
    alignItems: 'center',
    backgroundColor: '#E9B872',
    borderRadius: 999,
    height: 54,
    justifyContent: 'center',
    shadowColor: '#000000',
    shadowOffset: { height: 4, width: 0 },
    shadowOpacity: 0.24,
    shadowRadius: 10,
    width: 54,
    zIndex: 1,
  },
  accountDivider: {
    alignSelf: 'stretch',
    backgroundColor: 'rgba(157,178,210,0.28)',
    marginVertical: 2,
    width: 1,
    zIndex: 1,
  },
  accountGlowLarge: {
    backgroundColor: 'rgba(255,255,255,0.17)',
    borderRadius: 999,
    height: 130,
    position: 'absolute',
    right: -30,
    top: -30,
    width: 130,
  },
  accountGlowSmall: {
    backgroundColor: 'rgba(255,255,255,0.07)',
    borderRadius: 999,
    bottom: -44,
    height: 90,
    position: 'absolute',
    right: 24,
    width: 90,
  },
  accountInitial: {
    color: '#13294B',
    fontSize: 23,
    fontWeight: '800',
  },
  accountCopy: {
    flex: 1,
    gap: 3,
    minWidth: 0,
    zIndex: 1,
  },
  accountLabel: {
    color: '#9DB2D2',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 1.4,
    textTransform: 'uppercase',
  },
  accountValue: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '800',
  },
  accountMeta: {
    color: '#9DB2D2',
    fontSize: 12.5,
    fontWeight: '500',
    lineHeight: 18,
  },
  accountStatusRow: {
    alignItems: 'center',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  activeBadge: {
    alignItems: 'center',
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(127,227,176,0.12)',
    borderRadius: 999,
    flexDirection: 'row',
    gap: 4,
    paddingHorizontal: 7,
    paddingVertical: 2,
  },
  activeDot: {
    backgroundColor: '#3FD089',
    borderRadius: 999,
    height: 5,
    width: 5,
  },
  activeText: {
    color: '#7FE3B0',
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  stack: {
    gap: Spacing.md,
  },
  actions: {
    gap: Spacing.sm,
  },
});
