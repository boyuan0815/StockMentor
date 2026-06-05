package net.boyuan.stockmentor.ai.dto;

public record OpenAiSuggestionResult(
        boolean success,
        String content,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String finishReason,
        String errorMessage
) {
    public static OpenAiSuggestionResult failure(String errorMessage) {
        return new OpenAiSuggestionResult(false, null, null, null, null, null, errorMessage);
    }
}
