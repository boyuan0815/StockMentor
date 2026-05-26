package net.boyuan.stockmentor.ai.dto;

public record OpenAiExplanationResult(
        boolean success,
        String content,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String finishReason,
        String errorMessage
) {
    public static OpenAiExplanationResult failure(String errorMessage) {
        return new OpenAiExplanationResult(false, null, null, null, null, null, errorMessage);
    }
}
