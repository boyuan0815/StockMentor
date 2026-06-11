package net.boyuan.stockmentor.userprofile.dto;

import java.util.List;

public record OnboardingQuestionResponse(
        List<OnboardingQuestionDto> questions
) {
}
