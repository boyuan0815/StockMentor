package net.boyuan.stockmentor.userprofile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OnboardingAnswerRequest(
        String questionId,
        String optionId
) {
}
