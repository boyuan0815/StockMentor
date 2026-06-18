import { apiRequest } from '@/api/client';
import type { BasicAuthCredentials } from '@/types/auth';
import type {
  OnboardingQuestionResponse,
  OnboardingSubmitRequest,
  UserProfileResponse,
} from '@/types/profile';

export const profileApi = {
  getProfile(credentials: BasicAuthCredentials) {
    return apiRequest<UserProfileResponse>('/api/user/profile', {
      credentials,
    });
  },

  getOnboardingQuestions(credentials: BasicAuthCredentials) {
    return apiRequest<OnboardingQuestionResponse>('/api/user/onboarding/questions', {
      credentials,
    });
  },

  completeOnboarding(credentials: BasicAuthCredentials, request: OnboardingSubmitRequest) {
    return apiRequest<UserProfileResponse>('/api/user/onboarding', {
      method: 'POST',
      credentials,
      body: request,
    });
  },

  retakeOnboarding(credentials: BasicAuthCredentials, request: OnboardingSubmitRequest) {
    return apiRequest<UserProfileResponse>('/api/user/onboarding/retake', {
      method: 'POST',
      credentials,
      body: request,
    });
  },
};
