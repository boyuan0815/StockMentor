package net.boyuan.stockmentor.userprofile.dto;

import java.util.List;

public record OnboardingQuestionDto(
        String questionId,
        String text,
        boolean required,
        List<OnboardingOptionDto> options
) {
}
