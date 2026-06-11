package net.boyuan.stockmentor.userprofile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.userprofile.dto.OnboardingQuestionResponse;
import net.boyuan.stockmentor.userprofile.dto.OnboardingSubmitRequest;
import net.boyuan.stockmentor.userprofile.dto.UserProfileResponse;
import net.boyuan.stockmentor.userprofile.service.UserProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserProfileController {
    private final UserProfileService userProfileService;

    @GetMapping("/profile")
    public UserProfileResponse getProfile() {
        return userProfileService.getCurrentUserProfile();
    }

    @GetMapping("/onboarding/questions")
    public OnboardingQuestionResponse getOnboardingQuestions() {
        return userProfileService.getOnboardingQuestions();
    }

    @PostMapping("/onboarding")
    public UserProfileResponse completeOnboarding(@Valid @RequestBody OnboardingSubmitRequest request) {
        return userProfileService.completeOnboarding(request);
    }

    @PostMapping("/onboarding/retake")
    public UserProfileResponse retakeOnboarding(@Valid @RequestBody OnboardingSubmitRequest request) {
        return userProfileService.retakeOnboarding(request);
    }
}
