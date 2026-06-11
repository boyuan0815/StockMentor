package net.boyuan.stockmentor.userprofile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OnboardingSubmitRequest(
        @Valid
        List<OnboardingAnswerRequest> answers
) {
}
