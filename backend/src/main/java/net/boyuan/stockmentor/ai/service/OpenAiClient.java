package net.boyuan.stockmentor.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.boyuan.stockmentor.ai.dto.OpenAiChatCompletionResponse;
import net.boyuan.stockmentor.ai.dto.OpenAiExplanationResult;
import net.boyuan.stockmentor.ai.dto.OpenAiSuggestionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class OpenAiClient {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.model:gpt-5-mini}")
    private String model;

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    public OpenAiClient(
            @Qualifier("openAiWebClient") WebClient webClient,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public String getModel() {
        return model;
    }

    public OpenAiExplanationResult generateExplanation(String systemContent, String userContent) {
        if (apiKey == null || apiKey.isBlank()) {
            return OpenAiExplanationResult.failure("OpenAI API key is not configured");
        }

        try {
            return executeExplanationRequest(systemContent, userContent, true);
        } catch (Exception e) {
            if (isSchemaRelatedFailure(e)) {
                log.warn("OpenAI explanation schema request failed; retrying without response_format: {}", e.getMessage());
                try {
                    return executeExplanationRequest(systemContent, userContent, false);
                } catch (Exception fallbackException) {
                    log.error("OpenAI explanation generation failed after schema fallback", fallbackException);
                    return OpenAiExplanationResult.failure(fallbackException.getMessage());
                }
            }
            log.error("OpenAI explanation generation failed", e);
            return OpenAiExplanationResult.failure(e.getMessage());
        }
    }

    private OpenAiExplanationResult executeExplanationRequest(String systemContent, String userContent, boolean useJsonSchema) throws Exception {
        Map<String, Object> request = explanationRequest(systemContent, userContent, useJsonSchema);

        String responseBody = webClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        JsonNode response = objectMapper.readTree(responseBody);

        if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
            return OpenAiExplanationResult.failure("OpenAI returned no choices");
        }

        JsonNode firstChoice = response.get("choices").get(0);
        String content = firstChoice.path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            return OpenAiExplanationResult.failure("OpenAI returned an empty explanation");
        }

        JsonNode usage = response.path("usage");
        return new OpenAiExplanationResult(
                true,
                content,
                usage.path("prompt_tokens").isMissingNode() ? null : usage.path("prompt_tokens").asInt(),
                usage.path("completion_tokens").isMissingNode() ? null : usage.path("completion_tokens").asInt(),
                usage.path("total_tokens").isMissingNode() ? null : usage.path("total_tokens").asInt(),
                firstChoice.path("finish_reason").asText(null),
                null
        );
    }

    private Map<String, Object> explanationRequest(String systemContent, String userContent, boolean useJsonSchema) {
        Map<String, Object> baseRequest = Map.of(
                "model", model,
                // "temperature", 0.4,
                "messages", List.of(
                        Map.of("role", "system", "content", systemContent),
                        Map.of("role", "user", "content", userContent)
                )
        );
        if (!useJsonSchema) {
            return baseRequest;
        }

        return Map.of(
                "model", model,
                // "temperature", 0.4,
                "messages", baseRequest.get("messages"),
                "response_format", explanationResponseFormat()
        );
    }

    public OpenAiSuggestionResult generateSuggestion(String systemContent, String userContent) {
        if (apiKey == null || apiKey.isBlank()) {
            return OpenAiSuggestionResult.failure("OpenAI API key is not configured");
        }

        try {
            return executeSuggestionRequest(systemContent, userContent, true);
        } catch (Exception e) {
            if (isSchemaRelatedFailure(e)) {
                log.warn("OpenAI suggestion schema request failed; retrying without response_format: {}", e.getMessage());
                try {
                    return executeSuggestionRequest(systemContent, userContent, false);
                } catch (Exception fallbackException) {
                    log.error("OpenAI suggestion generation failed after schema fallback", fallbackException);
                    return OpenAiSuggestionResult.failure(fallbackException.getMessage());
                }
            }
            log.error("OpenAI suggestion generation failed", e);
            return OpenAiSuggestionResult.failure(e.getMessage());
        }
    }

    private OpenAiSuggestionResult executeSuggestionRequest(String systemContent, String userContent, boolean useJsonSchema) throws Exception {
        Map<String, Object> request = suggestionRequest(systemContent, userContent, useJsonSchema);

        String responseBody = webClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        OpenAiChatCompletionResponse response = objectMapper.readValue(responseBody, OpenAiChatCompletionResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return OpenAiSuggestionResult.failure("OpenAI returned no choices");
        }

        OpenAiChatCompletionResponse.Choice firstChoice = response.choices().get(0);
        if (firstChoice.message() == null) {
            return OpenAiSuggestionResult.failure("OpenAI returned no suggestion message");
        }
        if (firstChoice.message().refusal() != null && !firstChoice.message().refusal().isBlank()) {
            return OpenAiSuggestionResult.failure("OpenAI refused suggestion response");
        }

        String content = firstChoice.message().content();
        if (content == null || content.isBlank()) {
            return OpenAiSuggestionResult.failure("OpenAI returned an empty suggestion response");
        }

        OpenAiChatCompletionResponse.Usage usage = response.usage();
        return new OpenAiSuggestionResult(
                true,
                content,
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.completionTokens(),
                usage == null ? null : usage.totalTokens(),
                firstChoice.finishReason(),
                null
        );
    }

    private Map<String, Object> suggestionRequest(String systemContent, String userContent, boolean useJsonSchema) {
        Map<String, Object> baseRequest = Map.of(
                "model", model,
                // "temperature", 0.1,
                "messages", List.of(
                        Map.of("role", "system", "content", systemContent),
                        Map.of("role", "user", "content", userContent)
                )
        );
        if (!useJsonSchema) {
            return baseRequest;
        }

        return Map.of(
                "model", model,
                // "temperature", 0.1,
                "messages", baseRequest.get("messages"),
                "response_format", suggestionResponseFormat()
        );
    }

    private Map<String, Object> suggestionResponseFormat() {
        Map<String, Object> highlightSchema = highlightPhraseSchema();
        Map<String, Object> suggestionItemSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "symbol", Map.of(
                                "type", "string",
                                "description", "One supported stock symbol from the provided stockSnapshots only."
                        ),
                        "rankNo", Map.of(
                                "type", "integer",
                                "description", "Sequential rank starting from 1. Rank 1 must be the strongest suggestion."
                        ),
                        "matchScore", Map.of(
                                "type", "integer",
                                "description", "Educational suitability score from 0 to 100. Scores must follow rank order."
                        ),
                        "riskLevel", Map.of(
                                "type", "string",
                                "description", "Must exactly match the selected stock snapshot riskCategory."
                        ),
                        "suggestionLabel", Map.of(
                                "type", "string",
                                "description", "Short educational label, not a company name and not investment advice."
                        ),
                        "shortReason", Map.of(
                                "type", "string",
                                "description", "One beginner-friendly sentence explaining the educational fit."
                        ),
                        "detailReason", Map.of(
                                "type", "string",
                                "description", "A 40 to 70 word beginner-friendly explanation grounded in at least two provided factors such as risk, volatility, trend, price consistency, behavior confidence, volume, or data quality."
                        ),
                        "shortReasonHighlights", Map.of(
                                "type", "array",
                                "description", "Up to three exact phrases from shortReason to highlight for beginner readability.",
                                "items", highlightSchema,
                                "maxItems", 3
                        ),
                        "detailReasonHighlights", Map.of(
                                "type", "array",
                                "description", "Up to three exact phrases from detailReason to highlight for beginner readability.",
                                "items", highlightSchema,
                                "maxItems", 3
                        )
                ),
                "required", List.of(
                        "symbol",
                        "rankNo",
                        "matchScore",
                        "riskLevel",
                        "suggestionLabel",
                        "shortReason",
                        "detailReason",
                        "shortReasonHighlights",
                        "detailReasonHighlights"
                ),
                "additionalProperties", false
        );

        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "batchSummary", Map.of("type", "string"),
                        "suggestedStocks", Map.of(
                                "type", "array",
                                "items", suggestionItemSchema,
                                "minItems", 1,
                                "maxItems", 3
                        )
                ),
                "required", List.of("batchSummary", "suggestedStocks"),
                "additionalProperties", false
        );

        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "stock_suggestions_response",
                        "strict", true,
                        "schema", schema
                )
        );
    }

    private Map<String, Object> explanationResponseFormat() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "explanation", Map.of(
                                "type", "string",
                                "description", "The complete beginner-friendly explanation, exactly two short paragraphs."
                        ),
                        "highlights", Map.of(
                                "type", "array",
                                "description", "Up to three exact phrases from explanation to highlight.",
                                "items", highlightPhraseSchema(),
                                "maxItems", 3
                        )
                ),
                "required", List.of("explanation", "highlights"),
                "additionalProperties", false
        );

        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "stock_explanation_response",
                        "strict", true,
                        "schema", schema
                )
        );
    }

    private Map<String, Object> highlightPhraseSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "phrase", Map.of(
                                "type", "string",
                                "description", "Exact phrase copied from the final text."
                        ),
                        "style", Map.of(
                                "type", "string",
                                "enum", List.of("positive", "negative", "emphasis")
                        )
                ),
                "required", List.of("phrase", "style"),
                "additionalProperties", false
        );
    }

    private boolean isSchemaRelatedFailure(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (e instanceof WebClientResponseException responseException) {
            message = message + " " + responseException.getResponseBodyAsString().toLowerCase();
        }
        return message.contains("response_format")
                || message.contains("json_schema")
                || message.contains("schema")
                || message.contains("structured output");
    }
}
