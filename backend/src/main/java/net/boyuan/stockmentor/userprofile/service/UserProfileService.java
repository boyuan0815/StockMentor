package net.boyuan.stockmentor.userprofile.service;

import net.boyuan.stockmentor.userprofile.dto.OnboardingQuestionResponse;
import net.boyuan.stockmentor.userprofile.dto.OnboardingSubmitRequest;
import net.boyuan.stockmentor.userprofile.dto.UserProfileResponse;

public interface UserProfileService {
    UserProfileResponse getCurrentUserProfile();

    OnboardingQuestionResponse getOnboardingQuestions();

    UserProfileResponse completeOnboarding(OnboardingSubmitRequest request);

    UserProfileResponse retakeOnboarding(OnboardingSubmitRequest request);
}
