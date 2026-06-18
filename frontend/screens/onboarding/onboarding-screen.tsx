import { useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { profileApi } from '@/api/profile';
import { ActionButton } from '@/components/foundation/action-button';
import { EmptyState } from '@/components/foundation/empty-state';
import { ErrorBanner } from '@/components/foundation/error-banner';
import { PageHeader } from '@/components/foundation/page-header';
import { Screen } from '@/components/foundation/screen';
import { Colors, Radius, Spacing } from '@/constants/theme';
import { useAuthSession } from '@/providers/auth-session-provider';
import type { OnboardingQuestionDto } from '@/types/profile';
import { getApiErrorMessage } from '@/utils/api-error-copy';

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
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

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

  const handleSubmit = async () => {
    if (isSubmitting) {
      return;
    }

    if (!credentials) {
      setErrorMessage('Sign in again to save onboarding answers.');
      return;
    }

    const missingAnswers = questions.filter(
      (question) => !selectedOptionByQuestion[question.questionId],
    );

    if (questions.length === 0) {
      setErrorMessage('No onboarding questions are available yet. Try again.');
      return;
    }

    if (missingAnswers.length > 0) {
      setErrorMessage('Answer every onboarding question before saving your profile.');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);

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
      router.replace('/onboarding/result');
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error, 'onboarding'));
    } finally {
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

  return (
    <Screen contentStyle={styles.content}>
      <PageHeader
        eyebrow={isRetake ? 'Retake onboarding' : 'Onboarding'}
        title={isRetake ? 'Update your saved preferences' : 'Build your beginner profile'}
        description="Answer every question so the backend can save one consistent investment profile."
      />

      {errorMessage ? <ErrorBanner title="Onboarding needs attention" message={errorMessage} /> : null}

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
      ) : (
        <View style={styles.quiz}>
          <Text selectable style={styles.progress}>
            {answeredCount} of {questions.length} answered
          </Text>

          {questions.map((question, questionIndex) => (
            <View key={question.questionId} style={styles.questionCard}>
              <Text selectable style={styles.questionNumber}>
                Question {questionIndex + 1}
              </Text>
              <Text selectable style={styles.questionText}>
                {question.text}
              </Text>
              <View style={styles.options}>
                {question.options.map((option) => {
                  const isSelected = selectedOptionByQuestion[question.questionId] === option.optionId;

                  return (
                    <Pressable
                      accessibilityLabel={option.label}
                      accessibilityRole="button"
                      accessibilityState={{ disabled: isSubmitting, selected: isSelected }}
                      disabled={isSubmitting}
                      key={option.optionId}
                      onPress={() =>
                        setSelectedOptionByQuestion((current) => ({
                          ...current,
                          [question.questionId]: option.optionId,
                        }))
                      }
                      style={({ pressed }) => [
                        styles.option,
                        isSelected ? styles.optionSelected : undefined,
                        pressed && !isSubmitting ? styles.optionPressed : undefined,
                        isSubmitting ? styles.optionDisabled : undefined,
                      ]}>
                      <Text selectable style={styles.optionLabel}>
                        {option.label}
                      </Text>
                      {option.description ? (
                        <Text selectable style={styles.optionDescription}>
                          {option.description}
                        </Text>
                      ) : null}
                    </Pressable>
                  );
                })}
              </View>
            </View>
          ))}

          <View style={styles.actions}>
            <ActionButton
              accessibilityLabel={isSubmitting ? 'Saving onboarding answers' : 'Save onboarding answers'}
              disabled={isSubmitting}
              label={isSubmitting ? 'Saving answers...' : isRetake ? 'Save updated profile' : 'Finish onboarding'}
              onPress={handleSubmit}
            />
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
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: {
    gap: Spacing.xl,
  },
  emptyStack: {
    gap: Spacing.md,
  },
  quiz: {
    gap: Spacing.lg,
  },
  progress: {
    color: Colors.light.secondaryTint,
    fontSize: 14,
    fontWeight: '800',
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
  option: {
    backgroundColor: Colors.light.background,
    borderColor: Colors.light.border,
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.xs,
    minHeight: 48,
    padding: Spacing.md,
  },
  optionSelected: {
    backgroundColor: Colors.light.softBlue,
    borderColor: Colors.light.tint,
  },
  optionPressed: {
    opacity: 0.82,
  },
  optionDisabled: {
    opacity: 0.6,
  },
  optionLabel: {
    color: Colors.light.text,
    fontSize: 15,
    fontWeight: '800',
    lineHeight: 21,
  },
  optionDescription: {
    color: Colors.light.mutedText,
    fontSize: 14,
    lineHeight: 20,
  },
  actions: {
    gap: Spacing.sm,
  },
});
