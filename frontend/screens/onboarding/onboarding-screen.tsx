import { useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Animated,
  Easing,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  type DimensionValue,
} from 'react-native';

import { profileApi } from '@/api/profile';
import { ActionButton } from '@/components/foundation/action-button';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { PageHeader } from '@/components/foundation/page-header';
import { Screen } from '@/components/foundation/screen';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors, Radius, Spacing } from '@/constants/theme';
import { useAuthSession } from '@/providers/auth-session-provider';
import type { OnboardingQuestionDto } from '@/types/profile';
import { getApiErrorMessage } from '@/utils/api-error-copy';
import { getPostAuthRoute } from '@/utils/auth-routing';

export function OnboardingScreen() {
  const router = useRouter();
  const {
    credentials,
    finishOnboardingFlow,
    onboardingMode,
    refreshUser,
    user,
  } = useAuthSession();
  const [questions, setQuestions] = useState<OnboardingQuestionDto[]>([]);
  const [selectedOptionByQuestion, setSelectedOptionByQuestion] = useState<Record<string, string>>({});
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isRefreshingAccount, setIsRefreshingAccount] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [questionError, setQuestionError] = useState<string | null>(null);
  const [transitionDirection, setTransitionDirection] = useState(1);
  const questionTransition = useRef(new Animated.Value(1)).current;
  const submitProgress = useRef(new Animated.Value(0)).current;

  const isRetake = onboardingMode === 'retake';
  const isManualCompletedVisit = user && !user.mustCompleteOnboarding && !isRetake;

  const loadQuestions = useCallback(async () => {
    if (!credentials) {
      setErrorMessage('Sign in again to load onboarding questions.');
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);

    try {
      const response = await profileApi.getOnboardingQuestions(credentials);
      setQuestions(response.questions ?? []);
      setSelectedOptionByQuestion({});
      setCurrentQuestionIndex(0);
      setQuestionError(null);
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error, 'onboarding'));
    } finally {
      setIsLoading(false);
    }
  }, [credentials]);

  useEffect(() => {
    if (isManualCompletedVisit) {
      router.replace('/dashboard');
      return;
    }

    void loadQuestions();
  }, [isManualCompletedVisit, loadQuestions, router]);

  const answeredCount = useMemo(
    () => questions.filter((question) => selectedOptionByQuestion[question.questionId]).length,
    [questions, selectedOptionByQuestion],
  );
  const currentQuestion = questions[currentQuestionIndex];
  const isLastQuestion = questions.length > 0 && currentQuestionIndex === questions.length - 1;
  const progressPercent: DimensionValue =
    questions.length > 0
      ? `${Math.round(((currentQuestionIndex + 1) / questions.length) * 100)}%`
      : '0%';
  const submitProgressWidth = submitProgress.interpolate({
    inputRange: [0, 1],
    outputRange: ['0%', '100%'],
  });
  const questionAnimatedStyle = {
    opacity: questionTransition,
    transform: [
      {
        translateX: questionTransition.interpolate({
          inputRange: [0, 1],
          outputRange: [transitionDirection * 22, 0],
        }),
      },
    ],
  };
  const primaryActionLabel = isSubmitting
    ? 'Saving answers...'
    : isLastQuestion
      ? isRetake
        ? 'Save updated profile'
        : 'Finish onboarding'
      : 'Next';
  const primaryActionAccessibilityLabel = isSubmitting
    ? 'Saving onboarding answers'
    : isLastQuestion
      ? 'Save onboarding answers'
      : 'Next question';

  const handleSubmit = async () => {
    if (isSubmitting) {
      return;
    }

    if (!credentials) {
      setErrorMessage('Sign in again to save onboarding answers.');
      return;
    }

    setQuestionError(null);

    const missingAnswers = questions.filter(
      (question) => !selectedOptionByQuestion[question.questionId],
    );

    if (questions.length === 0) {
      setErrorMessage('No onboarding questions are available yet. Try again.');
      return;
    }

    if (missingAnswers.length > 0) {
      const firstMissingIndex = questions.findIndex(
        (question) => !selectedOptionByQuestion[question.questionId],
      );

      if (firstMissingIndex >= 0) {
        setCurrentQuestionIndex(firstMissingIndex);
      }

      setQuestionError('Choose an answer before saving your profile.');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);
    submitProgress.setValue(0);
    Animated.sequence([
      Animated.timing(submitProgress, {
        toValue: 0.9,
        duration: 500,
        easing: Easing.out(Easing.quad),
        useNativeDriver: false,
      }),
      Animated.timing(submitProgress, {
        toValue: 0.98,
        duration: 1500,
        easing: Easing.inOut(Easing.quad),
        useNativeDriver: false,
      }),
    ]).start();

    const request = {
      answers: questions.map((question) => ({
        questionId: question.questionId,
        optionId: selectedOptionByQuestion[question.questionId],
      })),
    };

    try {
      const profile = isRetake
        ? await profileApi.retakeOnboarding(credentials, request)
        : await profileApi.completeOnboarding(credentials, request);

      finishOnboardingFlow(profile);
      await refreshUser();
      submitProgress.stopAnimation();
      await animateSubmitProgressTo(1, 260);
      router.replace('/onboarding/result');
    } catch (error) {
      submitProgress.stopAnimation();
      submitProgress.setValue(0);
      setErrorMessage(getApiErrorMessage(error, 'onboarding'));
      setIsSubmitting(false);
    }
  };

  const handleCancelRetake = () => {
    if (isSubmitting) {
      return;
    }

    finishOnboardingFlow(null);
    router.replace('/profile');
  };

  const handleRefreshAccountState = async () => {
    if (isRefreshingAccount || isSubmitting) {
      return;
    }

    setIsRefreshingAccount(true);
    setQuestionError(null);

    try {
      const refreshedUser = await refreshUser();
      if (refreshedUser && !refreshedUser.mustCompleteOnboarding) {
        finishOnboardingFlow(null);
        router.replace(isRetake ? '/profile' : getPostAuthRoute(refreshedUser));
        return;
      }

      setErrorMessage('StockMentor refreshed your account. This quiz still needs to be completed.');
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error, 'onboarding'));
    } finally {
      setIsRefreshingAccount(false);
    }
  };

  const handleSelectOption = (questionId: string, optionId: string) => {
    if (isSubmitting) {
      return;
    }

    setSelectedOptionByQuestion((current) => ({
      ...current,
      [questionId]: optionId,
    }));
    setErrorMessage(null);
    setQuestionError(null);
  };

  const handleNext = () => {
    if (isSubmitting || !currentQuestion) {
      return;
    }

    if (!selectedOptionByQuestion[currentQuestion.questionId]) {
      setQuestionError('Choose an answer before continuing.');
      return;
    }

    setErrorMessage(null);
    setQuestionError(null);
    goToQuestion(Math.min(currentQuestionIndex + 1, questions.length - 1), 1);
  };

  const handleBack = () => {
    if (isSubmitting) {
      return;
    }

    setErrorMessage(null);
    setQuestionError(null);
    goToQuestion(Math.max(currentQuestionIndex - 1, 0), -1);
  };

  const goToQuestion = (nextIndex: number, direction: number) => {
    setTransitionDirection(direction);
    questionTransition.setValue(0);
    setCurrentQuestionIndex(nextIndex);
  };

  const animateSubmitProgressTo = useCallback(
    (toValue: number, duration: number) =>
      new Promise<void>((resolve) => {
        Animated.timing(submitProgress, {
          toValue,
          duration,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: false,
        }).start(() => resolve());
      }),
    [submitProgress],
  );

  useEffect(() => {
    Animated.timing(questionTransition, {
      toValue: 1,
      duration: 180,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [currentQuestionIndex, questionTransition]);

  if (isManualCompletedVisit) {
    return (
      <Screen>
        <PageHeader
          eyebrow="Profile already ready"
          title="Opening your dashboard"
          description="Retakes start from your profile after a confirmation."
        />
      </Screen>
    );
  }

  if (isSubmitting) {
    return (
      <Screen scroll={false} contentStyle={[styles.content, styles.processingContent]}>
        <View style={styles.processingCard}>
          <Text selectable style={styles.processingTitle}>
            Preparing your beginner profile...
          </Text>
          <Text selectable style={styles.processingMessage}>
            StockMentor is saving your answers and refreshing your account state.
          </Text>
          <View
            accessibilityLabel="Saving profile"
            accessibilityRole="progressbar"
            accessibilityValue={{ min: 0, max: 100 }}
            style={styles.processingTrack}>
            <Animated.View style={[styles.processingFill, { width: submitProgressWidth }]} />
          </View>
        </View>
      </Screen>
    );
  }

  return (
    <Screen scroll={false} contentStyle={styles.content}>
      <View style={styles.headerStack}>
        <PageHeader
          eyebrow={isRetake ? 'Retake onboarding' : 'Onboarding'}
          title={isRetake ? 'Update your saved preferences' : 'Build your beginner profile'}
          description="Move through one question at a time. Your profile is saved after all questions have an answer."
        />

        {errorMessage ? <ErrorBanner title="Onboarding needs attention" message={errorMessage} /> : null}
        {errorMessage && credentials ? (
          <View style={styles.recoveryActions}>
            <ActionButton
              accessibilityLabel={
                isRefreshingAccount ? 'Refreshing account state' : 'Refresh account state'
              }
              disabled={isRefreshingAccount || isSubmitting}
              label={isRefreshingAccount ? 'Refreshing account...' : 'Refresh account state'}
              onPress={handleRefreshAccountState}
              variant="secondary"
            />
          </View>
        ) : null}
      </View>

      {isLoading ? (
        <EmptyState title="Loading questions" description="StockMentor is loading the backend-owned quiz." />
      ) : questions.length === 0 ? (
        <View style={styles.emptyStack}>
          <EmptyState
            title="No questions available"
            description="The backend did not return onboarding questions. Try loading them again."
          />
          <ActionButton label="Retry" onPress={loadQuestions} variant="secondary" />
        </View>
      ) : currentQuestion ? (
        <View style={styles.quiz}>
          <ScrollView
            alwaysBounceVertical={false}
            bounces={false}
            contentContainerStyle={styles.questionScrollContent}
            keyboardShouldPersistTaps="handled"
            overScrollMode="never"
            showsVerticalScrollIndicator={false}
            style={styles.questionScroll}>
            <View style={styles.progressStack}>
              <View style={styles.progressRow}>
                <Text selectable style={styles.progress}>
                  Question {currentQuestionIndex + 1} of {questions.length}
                </Text>
                <Text selectable style={styles.progressMeta}>
                  {answeredCount} answered
                </Text>
              </View>
              <View
                accessibilityLabel={`Question ${currentQuestionIndex + 1} of ${questions.length}`}
                accessibilityRole="progressbar"
                accessibilityValue={{
                  min: 1,
                  max: questions.length,
                  now: currentQuestionIndex + 1,
                }}
                style={styles.progressTrack}>
                <View style={[styles.progressFill, { width: progressPercent }]} />
              </View>
            </View>

            <Animated.View style={[styles.questionCard, questionAnimatedStyle]}>
              <Text selectable style={styles.questionNumber}>
                Question {currentQuestionIndex + 1}
              </Text>
              <Text selectable style={styles.questionText}>
                {currentQuestion.text}
              </Text>
              {questionError ? (
                <View
                  accessibilityLiveRegion="assertive"
                  accessibilityRole="alert"
                  style={styles.questionErrorBox}>
                  <Text selectable style={styles.questionError}>
                    {questionError}
                  </Text>
                </View>
              ) : null}
              <View style={styles.options}>
                {currentQuestion.options.map((option) => {
                  const isSelected =
                    selectedOptionByQuestion[currentQuestion.questionId] === option.optionId;
                  const showDescription = hasUsefulDescription(option.label, option.description);

                  return (
                    <Pressable
                      accessibilityHint={
                        isSelected ? 'Selected answer' : 'Double tap to choose this answer'
                      }
                      accessibilityLabel={option.label}
                      accessibilityRole="radio"
                      accessibilityState={{ checked: isSelected, disabled: isSubmitting }}
                      disabled={isSubmitting}
                      key={option.optionId}
                      onPress={() => handleSelectOption(currentQuestion.questionId, option.optionId)}
                      style={({ pressed }) => [
                        styles.option,
                        isSelected ? styles.optionSelected : undefined,
                        pressed && !isSubmitting ? styles.optionPressed : undefined,
                      ]}>
                      <View style={styles.optionHeader}>
                        <View style={[styles.optionMarker, isSelected ? styles.optionMarkerSelected : undefined]}>
                          {isSelected ? (
                            <IconSymbol color={Colors.light.surface} name="checkmark" size={11} />
                          ) : null}
                        </View>
                        <Text selectable style={styles.optionLabel}>
                          {option.label}
                        </Text>
                      </View>
                      {showDescription ? (
                        <Text selectable style={styles.optionDescription}>
                          {option.description}
                        </Text>
                      ) : null}
                    </Pressable>
                  );
                })}
              </View>
            </Animated.View>
          </ScrollView>

          <View style={styles.actions}>
            <View style={styles.navigationActions}>
              <ActionButton
                disabled={currentQuestionIndex === 0 || isSubmitting}
                label="Back"
                onPress={handleBack}
                style={styles.action}
                variant="ghost"
              />
              <ActionButton
                accessibilityLabel={primaryActionAccessibilityLabel}
                disabled={isSubmitting}
                label={primaryActionLabel}
                onPress={isLastQuestion ? handleSubmit : handleNext}
                style={styles.action}
              />
            </View>
            {isRetake ? (
              <ActionButton
                disabled={isSubmitting}
                label="Cancel retake"
                onPress={handleCancelRetake}
                variant="ghost"
              />
            ) : null}
          </View>
        </View>
      ) : (
        <EmptyState title="Question unavailable" description="Try loading the onboarding questions again." />
      )}
    </Screen>
  );
}

function hasUsefulDescription(label: string, description?: string | null) {
  if (!description?.trim()) {
    return false;
  }

  return description.trim() !== label.trim();
}

const styles = StyleSheet.create({
  content: {
    alignSelf: 'center',
    flex: 1,
    gap: Spacing.xl,
    maxWidth: 620,
    width: '100%',
  },
  headerStack: {
    gap: Spacing.lg,
  },
  emptyStack: {
    gap: Spacing.md,
  },
  recoveryActions: {
    gap: Spacing.sm,
  },
  quiz: {
    flex: 1,
    gap: Spacing.lg,
    minHeight: 0,
  },
  questionScroll: {
    flex: 1,
  },
  questionScrollContent: {
    gap: Spacing.lg,
    paddingBottom: Spacing.sm,
  },
  progressStack: {
    gap: Spacing.sm,
  },
  progressRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  progress: {
    color: Colors.light.secondaryTint,
    fontSize: 14,
    fontWeight: '800',
  },
  progressMeta: {
    color: Colors.light.mutedText,
    fontSize: 13,
    fontVariant: ['tabular-nums'],
    lineHeight: 18,
  },
  progressTrack: {
    backgroundColor: Colors.light.border,
    borderRadius: Radius.sm,
    height: 8,
    overflow: 'hidden',
  },
  progressFill: {
    backgroundColor: Colors.light.secondaryTint,
    borderRadius: Radius.sm,
    height: 8,
  },
  questionCard: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.md,
    padding: Spacing.lg,
  },
  questionNumber: {
    color: Colors.light.secondaryTint,
    fontSize: 12,
    fontWeight: '800',
    textTransform: 'uppercase',
  },
  questionText: {
    color: Colors.light.text,
    fontSize: 17,
    fontWeight: '800',
    lineHeight: 24,
  },
  options: {
    gap: Spacing.sm,
  },
  optionHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  option: {
    backgroundColor: '#F1F5F9',
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.xs,
    minHeight: 48,
    padding: Spacing.md,
  },
  optionSelected: {
    backgroundColor: Colors.light.softTeal,
    borderColor: Colors.light.secondaryTint,
  },
  optionPressed: {
    opacity: 0.82,
  },
  optionMarker: {
    alignItems: 'center',
    borderColor: Colors.light.border,
    borderRadius: 9,
    borderWidth: 1,
    height: 20,
    justifyContent: 'center',
    width: 20,
  },
  optionMarkerSelected: {
    backgroundColor: Colors.light.secondaryTint,
    borderColor: Colors.light.secondaryTint,
  },
  optionLabel: {
    color: Colors.light.text,
    fontSize: 15,
    fontWeight: '500',
    flex: 1,
    lineHeight: 21,
  },
  questionErrorBox: {
    backgroundColor: '#FEF2F2',
    borderColor: '#FECACA',
    borderRadius: Radius.md,
    borderWidth: 1,
    padding: Spacing.md,
  },
  questionError: {
    color: Colors.light.destructive,
    fontSize: 14,
    fontWeight: '700',
    lineHeight: 20,
  },
  optionDescription: {
    color: Colors.light.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
  actions: {
    backgroundColor: Colors.light.background,
    borderColor: Colors.light.border,
    borderTopWidth: 1,
    gap: Spacing.sm,
    paddingTop: Spacing.md,
  },
  navigationActions: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  action: {
    flex: 1,
  },
  processingContent: {
    justifyContent: 'center',
  },
  processingCard: {
    backgroundColor: Colors.light.surface,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.md,
    padding: Spacing.xl,
  },
  processingTitle: {
    color: Colors.light.text,
    fontSize: 20,
    fontWeight: '800',
    lineHeight: 26,
  },
  processingMessage: {
    color: Colors.light.mutedText,
    fontSize: 15,
    lineHeight: 22,
  },
  processingTrack: {
    backgroundColor: Colors.light.border,
    borderRadius: Radius.sm,
    height: 8,
    overflow: 'hidden',
  },
  processingFill: {
    backgroundColor: Colors.light.secondaryTint,
    borderRadius: Radius.sm,
    height: 8,
  },
});
