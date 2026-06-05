package net.boyuan.stockmentor.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChatCompletionResponse(
        String id,
        String object,
        Long created,
        String model,
        List<Choice> choices,
        Usage usage,
        @JsonProperty("service_tier")
        String serviceTier,
        @JsonProperty("system_fingerprint")
        String systemFingerprint
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            Integer index,
            Message message,
            Object logprobs,
            @JsonProperty("finish_reason")
            String finishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String role,
            String content,
            String refusal,
            List<Object> annotations
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens")
            Integer promptTokens,
            @JsonProperty("completion_tokens")
            Integer completionTokens,
            @JsonProperty("total_tokens")
            Integer totalTokens
    ) {
    }
}
