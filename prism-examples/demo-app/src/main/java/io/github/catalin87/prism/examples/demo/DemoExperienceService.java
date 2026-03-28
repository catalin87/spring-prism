/*
 * Copyright (c) 2026 Catalin Dordea and Spring Prism Contributors
 *
 * Licensed under the EUPL, Version 1.2 or later (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package io.github.catalin87.prism.examples.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.github.catalin87.prism.boot.autoconfigure.PrismRuntimeMetrics;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.langchain4j.PrismChatModel;
import io.github.catalin87.prism.mcp.PrismMcpClient;
import io.github.catalin87.prism.mcp.PrismMcpClientBuilder;
import io.github.catalin87.prism.mcp.PrismMcpMetricsSink;
import io.github.catalin87.prism.mcp.PrismMcpTransport;
import io.github.catalin87.prism.spring.ai.advisor.PrismChatClientAdvisor;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Executes the supported integration flows while recording sanitize and restore traces. */
@Service
class DemoExperienceService {

  static final String MOCK_PREFIX = "Mock Prism AI :: ";
  static final String MOCK_SUFFIX = " :: lorem ipsum privacy trace.";

  private final List<PrismRulePack> availableRulePacks;
  private final PrismVault prismVault;
  private final ObservationRegistry observationRegistry;
  private final PrismRuntimeMetrics prismRuntimeMetrics;
  private final PrismMcpMetricsSink prismMcpMetricsSink;
  private final ObjectMapper objectMapper;
  private final McpMockRecorder mcpMockRecorder;

  DemoExperienceService(
      @Qualifier("springPrismRulePacks") List<PrismRulePack> availableRulePacks,
      PrismVault prismVault,
      ObservationRegistry observationRegistry,
      PrismRuntimeMetrics prismRuntimeMetrics,
      PrismMcpMetricsSink prismMcpMetricsSink,
      ObjectMapper objectMapper,
      McpMockRecorder mcpMockRecorder) {
    this.availableRulePacks = List.copyOf(availableRulePacks);
    this.prismVault = prismVault;
    this.observationRegistry = observationRegistry;
    this.prismRuntimeMetrics = prismRuntimeMetrics;
    this.prismMcpMetricsSink = prismMcpMetricsSink;
    this.objectMapper = objectMapper;
    this.mcpMockRecorder = mcpMockRecorder;
  }

  DemoOptionsResponse options() {
    return new DemoOptionsResponse(
        List.of("spring-ai", "langchain4j", "mcp"),
        availableRulePacks.stream().map(PrismRulePack::getName).toList(),
        "/prism/index.html",
        "/actuator/prism");
  }

  DemoRunResponse run(DemoRunRequest request, String baseUrl) {
    String integration =
        request.integration() == null
            ? "spring-ai"
            : request.integration().trim().toLowerCase(Locale.ROOT);
    String message = request.message() == null ? "" : request.message().trim();
    if (message.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must not be blank");
    }

    List<PrismRulePack> selectedRulePacks = selectedRulePacks(request.rulePacks());
    DemoTrace trace;
    if ("spring-ai".equals(integration)) {
      trace = runSpringAi(message, selectedRulePacks);
    } else if ("langchain4j".equals(integration)) {
      trace = runLangChain4j(message, selectedRulePacks);
    } else if ("mcp".equals(integration)) {
      trace = runMcp(message, selectedRulePacks, baseUrl);
    } else {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown integration");
    }

    return new DemoRunResponse(
        trace.integration(),
        trace.originalPrompt(),
        selectedRulePacks.stream().map(PrismRulePack::getName).toList(),
        trace.sanitizedOutbound(),
        trace.mockModelResponse(),
        trace.restoredResponse(),
        "/prism/index.html",
        "/actuator/prism");
  }

  private DemoTrace runSpringAi(String message, List<PrismRulePack> selectedRulePacks) {
    RecordingSpringAiChatModel model = new RecordingSpringAiChatModel();
    PrismChatClientAdvisor advisor =
        new PrismChatClientAdvisor(
            selectedRulePacks, prismVault, observationRegistry, prismRuntimeMetrics, false);
    ChatClient chatClient = ChatClient.builder(model).defaultAdvisors(advisor).build();
    String restoredResponse = chatClient.prompt().user(message).call().content();
    return new DemoTrace(
        "spring-ai",
        message,
        model.prompts().getLast(),
        model.rawResponses().getLast(),
        restoredResponse);
  }

  private DemoTrace runLangChain4j(String message, List<PrismRulePack> selectedRulePacks) {
    RecordingLangChainChatModel delegate = new RecordingLangChainChatModel();
    ChatModel prismChatModel =
        new PrismChatModel(
            delegate,
            selectedRulePacks,
            prismVault,
            observationRegistry,
            prismRuntimeMetrics,
            false);
    String restoredResponse = prismChatModel.chat(UserMessage.from(message)).aiMessage().text();
    return new DemoTrace(
        "langchain4j",
        message,
        delegate.sanitizedPrompts().getLast(),
        delegate.rawResponses().getLast(),
        restoredResponse);
  }

  private DemoTrace runMcp(String message, List<PrismRulePack> selectedRulePacks, String baseUrl) {
    String requestId = UUID.randomUUID().toString();
    PrismMcpClient prismMcpClient =
        PrismMcpClientBuilder.builder()
            .withTransport(PrismMcpTransport.STREAMABLE_HTTP)
            .withBaseUrl(baseUrl + "/demo-lab/api/mock-mcp")
            .withRulePacks(selectedRulePacks)
            .withVault(prismVault)
            .withObservationRegistry(observationRegistry)
            .withMetricsSink(prismMcpMetricsSink)
            .withStrictMode(false)
            .build();

    Map<String, Object> restored =
        prismMcpClient.exchange(
            Map.of(
                "jsonrpc",
                "2.0",
                "id",
                requestId,
                "method",
                "tools/call",
                "params",
                Map.of("prompt", message)));

    McpMockTrace mockTrace = mcpMockRecorder.get(requestId);
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) restored.get("result");
    return new DemoTrace(
        "mcp",
        message,
        prettyJson(mockTrace.requestPayload()),
        prettyJson(mockTrace.responsePayload()),
        String.valueOf(result.get("message")));
  }

  private List<PrismRulePack> selectedRulePacks(List<String> requestedRulePacks) {
    if (requestedRulePacks == null || requestedRulePacks.isEmpty()) {
      return availableRulePacks;
    }
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String requestedRulePack : requestedRulePacks) {
      if (requestedRulePack != null && !requestedRulePack.isBlank()) {
        normalized.add(requestedRulePack.trim().toUpperCase(Locale.ROOT));
      }
    }
    List<PrismRulePack> selected =
        availableRulePacks.stream()
            .filter(rulePack -> normalized.contains(rulePack.getName().toUpperCase(Locale.ROOT)))
            .toList();
    if (selected.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No matching rule packs selected");
    }
    return selected;
  }

  private String prettyJson(Map<String, Object> payload) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize payload", exception);
    }
  }

  static final class RecordingSpringAiChatModel
      implements org.springframework.ai.chat.model.ChatModel {
    private final List<String> prompts = new ArrayList<>();
    private final List<String> rawResponses = new ArrayList<>();

    @Override
    public org.springframework.ai.chat.model.ChatResponse call(Prompt prompt) {
      String content = prompt.getContents();
      prompts.add(content);
      String rawResponse = MOCK_PREFIX + content + MOCK_SUFFIX;
      rawResponses.add(rawResponse);
      return new org.springframework.ai.chat.model.ChatResponse(
          List.of(new Generation(new AssistantMessage(rawResponse))));
    }

    List<String> prompts() {
      return List.copyOf(prompts);
    }

    List<String> rawResponses() {
      return List.copyOf(rawResponses);
    }
  }

  static final class RecordingLangChainChatModel implements ChatModel {
    private final List<String> sanitizedPrompts = new ArrayList<>();
    private final List<String> rawResponses = new ArrayList<>();

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
      UserMessage promptMessage = (UserMessage) chatRequest.messages().getLast();
      String prompt = promptMessage.singleText();
      sanitizedPrompts.add(prompt);
      String rawResponse = MOCK_PREFIX + prompt + MOCK_SUFFIX;
      rawResponses.add(rawResponse);
      return ChatResponse.builder().aiMessage(AiMessage.from(rawResponse)).build();
    }

    List<String> sanitizedPrompts() {
      return List.copyOf(sanitizedPrompts);
    }

    List<String> rawResponses() {
      return List.copyOf(rawResponses);
    }
  }
}
