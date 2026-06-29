package net.boyuan.stockmentor.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.boyuan.stockmentor.ai.dto.OpenAiExplanationResult;
import net.boyuan.stockmentor.ai.dto.OpenAiSuggestionResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiClientTests {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void suggestionRequestUsesJsonSchemaResponseFormat() throws Exception {
    try (StubOpenAiServer server = new StubOpenAiServer()) {
      server.enqueue(200, validSuggestionResponse(true));
      OpenAiClient client = client(server);

      OpenAiSuggestionResult result = client.generateSuggestion("system", "user");

      assertTrue(result.success());
      assertEquals(12, result.promptTokens());
      assertEquals(20, result.completionTokens());
      assertEquals(32, result.totalTokens());
      String requestBody = server.requestBodies().get(0);
      assertTrue(requestBody.contains("\"response_format\""));
      assertTrue(requestBody.contains("\"json_schema\""));
      assertTrue(requestBody.contains("\"batchSummary\""));
      assertTrue(requestBody.contains("\"suggestedStocks\""));
    }
  }

  @Test
  void schemaRelatedFailureRetriesOnceWithoutJsonSchema() throws Exception {
    try (StubOpenAiServer server = new StubOpenAiServer()) {
      server.enqueue(400, """
          {"error":{"message":"Unsupported response_format json_schema for this request"}}
          """);
      server.enqueue(200, validSuggestionResponse(false));
      OpenAiClient client = client(server);

      OpenAiSuggestionResult result = client.generateSuggestion("system", "user");

      assertTrue(result.success());
      assertEquals(2, server.requestBodies().size());
      assertTrue(server.requestBodies().get(0).contains("\"response_format\""));
      assertFalse(server.requestBodies().get(1).contains("\"response_format\""));
    }
  }

  @Test
  void suggestionFailureHandlesMissingBlankAndRefusalResponses() throws Exception {
    try (StubOpenAiServer server = new StubOpenAiServer()) {
      server.enqueue(200, "{\"choices\":[]}");
      server.enqueue(200, "{\"choices\":[{\"message\":null}]}");
      server.enqueue(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}]}");
      server.enqueue(200,
          "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{}\",\"refusal\":\"I cannot comply\"}}]}");
      OpenAiClient client = client(server);

      assertFalse(client.generateSuggestion("system", "user").success());
      assertFalse(client.generateSuggestion("system", "user").success());
      assertFalse(client.generateSuggestion("system", "user").success());
      OpenAiSuggestionResult refusal = client.generateSuggestion("system", "user");

      assertFalse(refusal.success());
      assertTrue(refusal.errorMessage().contains("refused"));
    }
  }

  @Test
  void suggestionAllowsMissingUsageAndIgnoresUnknownFields() throws Exception {
    try (StubOpenAiServer server = new StubOpenAiServer()) {
      server.enqueue(200, """
          {
            "id": "chatcmpl-test",
            "choices": [
              {
                "message": {
                  "role": "assistant",
                  "content": "{\\"batchSummary\\":\\"ok\\",\\"suggestedStocks\\":[]}",
                  "extra": "ignored"
                },
                "finish_reason": "stop",
                "extra_choice_field": "ignored"
              }
            ],
            "unexpected": "ignored"
          }
          """);
      OpenAiClient client = client(server);

      OpenAiSuggestionResult result = client.generateSuggestion("system", "user");

      assertTrue(result.success());
      assertNull(result.promptTokens());
      assertNull(result.completionTokens());
      assertNull(result.totalTokens());
    }
  }

  @Test
  void explanationRequestUsesJsonSchemaResponseFormat() throws Exception {
    try (StubOpenAiServer server = new StubOpenAiServer()) {
      server.enqueue(200,
          """
              {
                "choices": [
                  {
                    "message": {
                      "role": "assistant",
                      "content": "{\\"explanation\\":\\"Educational explanation\\",\\"highlights\\":[{\\"phrase\\":\\"Educational\\",\\"style\\":\\"emphasis\\"}]}"
                    },
                    "finish_reason": "stop"
                  }
                ],
                "usage": {
                  "prompt_tokens": 5,
                  "completion_tokens": 6,
                  "total_tokens": 11
                }
              }
              """);
      OpenAiClient client = client(server);

      OpenAiExplanationResult result = client.generateExplanation("system", "user");

      assertTrue(result.success());
      assertTrue(result.content().contains("\"highlights\""));
      assertTrue(server.requestBodies().get(0).contains("\"response_format\""));
      assertTrue(server.requestBodies().get(0).contains("\"stock_explanation_response\""));
    }
  }

  private OpenAiClient client(StubOpenAiServer server) {
    OpenAiClient client = new OpenAiClient(
        WebClient.builder().baseUrl(server.baseUrl()).build(),
        objectMapper);
    ReflectionTestUtils.setField(client, "apiKey", "test-key");
    ReflectionTestUtils.setField(client, "model", "gpt-4o-mini");
    return client;
  }

  private String validSuggestionResponse(boolean includeUnknownUsageDetails) {
    String unknownUsageDetails = includeUnknownUsageDetails
        ? """
              ,
              "prompt_tokens_details": {"cached_tokens": 0},
              "completion_tokens_details": {"reasoning_tokens": 0}
            """
        : "";
    return """
        {
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "{\\"batchSummary\\":\\"ok\\",\\"suggestedStocks\\":[{\\"symbol\\":\\"MSFT\\",\\"rankNo\\":1,\\"matchScore\\":80,\\"riskLevel\\":\\"moderate\\",\\"suggestionLabel\\":\\"Balanced\\",\\"shortReason\\":\\"Microsoft matches the profile.\\",\\"detailReason\\":\\"Microsoft has moderate risk and complete data.\\",\\"shortReasonHighlights\\":[],\\"detailReasonHighlights\\":[]}]}"
              },
              "finish_reason": "stop"
            }
          ],
          "usage": {
            "prompt_tokens": 12,
            "completion_tokens": 20,
            "total_tokens": 32
            %s
          }
        }
        """
        .formatted(unknownUsageDetails);
  }

  private static class StubOpenAiServer implements AutoCloseable {
    private final HttpServer server;
    private final Queue<StubResponse> responses = new ArrayDeque<>();
    private final List<String> requestBodies = new ArrayList<>();

    StubOpenAiServer() throws IOException {
      server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext("/v1/chat/completions", this::handle);
      server.start();
    }

    String baseUrl() {
      return "http://localhost:" + server.getAddress().getPort();
    }

    void enqueue(int status, String body) {
      responses.add(new StubResponse(status, body));
    }

    List<String> requestBodies() {
      return requestBodies;
    }

    private void handle(HttpExchange exchange) throws IOException {
      requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      StubResponse response = responses.remove();
      byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(response.status(), bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    }

    @Override
    public void close() {
      server.stop(0);
    }

    private record StubResponse(int status, String body) {
    }
  }
}
