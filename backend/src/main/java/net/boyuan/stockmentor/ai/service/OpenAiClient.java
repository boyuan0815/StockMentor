package net.boyuan.stockmentor.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.ai.dto.OpenAiExplanationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class OpenAiClient {
    private final WebClient webClient;

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    public OpenAiClient(@Qualifier("openAiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public String getModel() {
        return model;
    }

    public OpenAiExplanationResult generateExplanation(String systemContent, String userContent) {
        if (apiKey == null || apiKey.isBlank()) {
            return OpenAiExplanationResult.failure("OpenAI API key is not configured");
        }

        try {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "temperature", 0.4,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemContent),
                            Map.of("role", "user", "content", userContent)
                    )
            );

            JsonNode response = webClient.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

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
        } catch (Exception e) {
            log.error("OpenAI explanation generation failed", e);
            return OpenAiExplanationResult.failure(e.getMessage());
        }
    }
}
