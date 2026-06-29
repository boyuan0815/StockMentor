package net.boyuan.stockmentor.ai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiChatCompletionResponseTests {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void parsesUsageWhenOpenAiReturnsAdditionalTokenDetailFields() throws Exception {
    String responseBody = """
        {
          "id": "chatcmpl-test",
          "object": "chat.completion",
          "created": 1710000000,
          "model": "gpt-4o-mini",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "{\\"batchSummary\\":\\"ok\\",\\"suggestedStocks\\":[]}",
                "extra_message_field": "ignored"
              },
              "finish_reason": "stop",
              "extra_choice_field": "ignored"
            }
          ],
          "usage": {
            "prompt_tokens": 100,
            "completion_tokens": 50,
            "total_tokens": 150,
            "prompt_tokens_details": {
              "cached_tokens": 0
            },
            "completion_tokens_details": {
              "reasoning_tokens": 0
            },
            "another_unknown_usage_field": "ignored"
          },
          "extra_top_level_field": "ignored"
        }
        """;

    OpenAiChatCompletionResponse response = objectMapper.readValue(responseBody, OpenAiChatCompletionResponse.class);

    assertEquals("gpt-4o-mini", response.model());
    assertEquals("stop", response.choices().get(0).finishReason());
    assertEquals(100, response.usage().promptTokens());
    assertEquals(50, response.usage().completionTokens());
    assertEquals(150, response.usage().totalTokens());
  }
}
