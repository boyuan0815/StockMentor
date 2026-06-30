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
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Service
public class OpenAiClient {
        private final WebClient webClient;
        private final ObjectMapper objectMapper;

        @Value("${openai.api.key:}")
        private String apiKey;

        @Value("${openai.model:gpt-4o-mini}")
        private String model;

        private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
        private static final int TRANSIENT_MAX_ATTEMPTS = 2;

        public OpenAiClient(
                        @Qualifier("openAiWebClient") WebClient webClient,
                        ObjectMapper objectMapper) {
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
                        return executeWithTransientRetry(
                                        () -> executeExplanationRequest(systemContent, userContent, true),
                                        "explanation");
                } catch (Exception e) {
                        if (isSchemaRelatedFailure(e)) {
                                log.warn("OpenAI explanation schema request failed; retrying without response_format: {}",
                                                e.getMessage());
                                try {
                                        return executeWithTransientRetry(
                                                        () -> executeExplanationRequest(systemContent, userContent, false),
                                                        "explanation schema fallback");
                                } catch (Exception fallbackException) {
                                        logOpenAiFailure("OpenAI explanation generation failed after schema fallback",
                                                        fallbackException);
                                        return OpenAiExplanationResult.failure(failureMessage(fallbackException));
                                }
                        }
                        logOpenAiFailure("OpenAI explanation generation failed", e);
                        return OpenAiExplanationResult.failure(failureMessage(e));
                }
        }

        private OpenAiExplanationResult executeExplanationRequest(String systemContent, String userContent,
                        boolean useJsonSchema) throws Exception {
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
                                usage.path("prompt_tokens").isMissingNode() ? null
                                                : usage.path("prompt_tokens").asInt(),
                                usage.path("completion_tokens").isMissingNode() ? null
                                                : usage.path("completion_tokens").asInt(),
                                usage.path("total_tokens").isMissingNode() ? null : usage.path("total_tokens").asInt(),
                                firstChoice.path("finish_reason").asText(null),
                                null);
        }

        private Map<String, Object> explanationRequest(String systemContent, String userContent,
                        boolean useJsonSchema) {
                Map<String, Object> baseRequest = Map.of(
                                "model", model,
                                "temperature", 0.3,
                                "messages", List.of(
                                                Map.of("role", "system", "content", systemContent),
                                                Map.of("role", "user", "content", userContent)));
                if (!useJsonSchema) {
                        return baseRequest;
                }

                return Map.of(
                                "model", model,
                                "temperature", 0.3,
                                "messages", baseRequest.get("messages"),
                                "response_format", explanationResponseFormat());
        }

        public OpenAiSuggestionResult generateSuggestion(String systemContent, String userContent) {
                if (apiKey == null || apiKey.isBlank()) {
                        return OpenAiSuggestionResult.failure("OpenAI API key is not configured");
                }

                try {
                        return executeWithTransientRetry(
                                        () -> executeSuggestionRequest(systemContent, userContent, true),
                                        "suggestion");
                } catch (Exception e) {
                        if (isSchemaRelatedFailure(e)) {
                                log.warn("OpenAI suggestion schema request failed; retrying without response_format: {}",
                                                e.getMessage());
                                try {
                                        return executeWithTransientRetry(
                                                        () -> executeSuggestionRequest(systemContent, userContent, false),
                                                        "suggestion schema fallback");
                                } catch (Exception fallbackException) {
                                        logOpenAiFailure("OpenAI suggestion generation failed after schema fallback",
                                                        fallbackException);
                                        return OpenAiSuggestionResult.failure(failureMessage(fallbackException));
                                }
                        }
                        logOpenAiFailure("OpenAI suggestion generation failed", e);
                        return OpenAiSuggestionResult.failure(failureMessage(e));
                }
        }

        private OpenAiSuggestionResult executeSuggestionRequest(String systemContent, String userContent,
                        boolean useJsonSchema) throws Exception {
                Map<String, Object> request = suggestionRequest(systemContent, userContent, useJsonSchema);

                String responseBody = webClient.post()
                                .uri("/v1/chat/completions")
                                .header("Authorization", "Bearer " + apiKey)
                                .header("Content-Type", "application/json")
                                .bodyValue(request)
                                .retrieve()
                                .bodyToMono(String.class)
                                .block();

                OpenAiChatCompletionResponse response = objectMapper.readValue(responseBody,
                                OpenAiChatCompletionResponse.class);
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
                                null);
        }

        private Map<String, Object> suggestionRequest(String systemContent, String userContent, boolean useJsonSchema) {
                Map<String, Object> baseRequest = Map.of(
                                "model", model,
                                "temperature", 0.1,
                                "messages", List.of(
                                                Map.of("role", "system", "content", systemContent),
                                                Map.of("role", "user", "content", userContent)));
                if (!useJsonSchema) {
                        return baseRequest;
                }

                return Map.of(
                                "model", model,
                                "temperature", 0.1,
                                "messages", baseRequest.get("messages"),
                                "response_format", suggestionResponseFormat());
        }

        private Map<String, Object> suggestionResponseFormat() {
                Map<String, Object> highlightSchema = highlightPhraseSchema();
                Map<String, Object> suggestionItemSchema = Map.of(
                                "type", "object",
                                "properties", Map.of(
                                                "symbol", Map.of(
                                                                "type", "string",
                                                                "description",
                                                                "One supported stock symbol from the provided stockSnapshots only."),
                                                "rankNo", Map.of(
                                                                "type", "integer",
                                                                "description",
                                                                "Sequential rank starting from 1. Rank 1 must be the strongest suggestion."),
                                                "matchScore", Map.of(
                                                                "type", "integer",
                                                                "description",
                                                                "Educational suitability score from 0 to 100. Scores must follow rank order."),
                                                "riskLevel", Map.of(
                                                                "type", "string",
                                                                "description",
                                                                "Must exactly match the selected stock snapshot riskCategory."),
                                                "suggestionLabel", Map.of(
                                                                "type", "string",
                                                                "description",
                                                                "Short educational label, not a company name and not investment advice."),
                                                "shortReason", Map.of(
                                                                "type", "string",
                                                                "description",
                                                                "One beginner-friendly sentence explaining the educational fit."),
                                                "detailReason", Map.of(
                                                                "type", "string",
                                                                "description",
                                                                "A 40 to 70 word beginner-friendly explanation grounded in at least two provided factors such as risk, volatility, trend, price consistency, behavior confidence, volume, or data quality."),
                                                "detailReasonHighlights", Map.of(
                                                                "type", "array",
                                                                "description",
                                                                "Up to three exact phrases from detailReason to highlight for beginner readability.",
                                                                "items", highlightSchema,
                                                                "maxItems", 3)),
                                "required", List.of(
                                                "symbol",
                                                "rankNo",
                                                "matchScore",
                                                "riskLevel",
                                                "suggestionLabel",
                                                "shortReason",
                                                "detailReason",
                                                "detailReasonHighlights"),
                                "additionalProperties", false);

                Map<String, Object> schema = Map.of(
                                "type", "object",
                                "properties", Map.of(
                                                "batchSummary", Map.of("type", "string"),
                                                "suggestedStocks", Map.of(
                                                                "type", "array",
                                                                "items", suggestionItemSchema,
                                                                "minItems", 1,
                                                                "maxItems", 3)),
                                "required", List.of("batchSummary", "suggestedStocks"),
                                "additionalProperties", false);

                return Map.of(
                                "type", "json_schema",
                                "json_schema", Map.of(
                                                "name", "stock_suggestions_response",
                                                "strict", true,
                                                "schema", schema));
        }

        private Map<String, Object> explanationResponseFormat() {
                Map<String, Object> schema = Map.of(
                                "type", "object",
                                "properties", Map.of(
                                                "explanation", Map.of(
                                                                "type", "string",
                                                                "description",
                                                                "The complete beginner-friendly explanation. Keep it concise for mobile reading."),
                                                "highlights", Map.of(
                                                                "type", "array",
                                                                "description",
                                                                "Up to three exact phrases from explanation to highlight.",
                                                                "items", highlightPhraseSchema(),
                                                                "maxItems", 3)),
                                "required", List.of("explanation", "highlights"),
                                "additionalProperties", false);

                return Map.of(
                                "type", "json_schema",
                                "json_schema", Map.of(
                                                "name", "stock_explanation_response",
                                                "strict", true,
                                                "schema", schema));
        }

        private Map<String, Object> highlightPhraseSchema() {
                return Map.of(
                                "type", "object",
                                "properties", Map.of(
                                                "phrase", Map.of(
                                                                "type", "string",
                                                                "description",
                                                                "Exact phrase copied from the final text."),
                                                "style", Map.of(
                                                                "type", "string",
                                                                "enum", List.of("positive", "negative", "emphasis"))),
                                "required", List.of("phrase", "style"),
                                "additionalProperties", false);
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

        private <T> T executeWithTransientRetry(ThrowingSupplier<T> supplier, String requestType) throws Exception {
                for (int attempt = 1; attempt <= TRANSIENT_MAX_ATTEMPTS; attempt++) {
                        try {
                                return supplier.get();
                        } catch (Exception exception) {
                                if (!isTransientFailure(exception) || attempt == TRANSIENT_MAX_ATTEMPTS) {
                                        throw exception;
                                }
                                log.warn("OpenAI {} request transient failure on attempt {}/{}: {}",
                                                requestType,
                                                attempt,
                                                TRANSIENT_MAX_ATTEMPTS,
                                                safeFailureMessage(exception));
                        }
                }
                throw new IllegalStateException("OpenAI retry loop exited unexpectedly");
        }

        private boolean isTransientFailure(Throwable throwable) {
                Throwable current = throwable;
                while (current != null) {
                        if (current instanceof WebClientResponseException responseException
                                        && isTransientHttpStatus(responseException)) {
                                return true;
                        }
                        if (current instanceof WebClientRequestException
                                        || current instanceof SocketException
                                        || current instanceof SocketTimeoutException
                                        || current instanceof TimeoutException) {
                                return true;
                        }
                        current = current.getCause();
                }
                return false;
        }

        private boolean isTransientHttpStatus(WebClientResponseException exception) {
                int statusCode = exception.getStatusCode().value();
                return statusCode == 408 || statusCode == 429 || statusCode >= 500;
        }

        private void logOpenAiFailure(String message, Exception exception) {
                if (isTransientFailure(exception)) {
                        log.warn("{}: {}", message, safeFailureMessage(exception));
                        return;
                }
                log.error(message, exception);
        }

        private String failureMessage(Exception exception) {
                if (isTransientFailure(exception)) {
                        return "OpenAI request failed after a temporary connection problem";
                }
                return exception.getMessage();
        }

        private String safeFailureMessage(Throwable throwable) {
                String message = throwable.getMessage();
                return message == null || message.isBlank()
                                ? throwable.getClass().getSimpleName()
                                : message;
        }

        @FunctionalInterface
        private interface ThrowingSupplier<T> {
                T get() throws Exception;
        }
}
