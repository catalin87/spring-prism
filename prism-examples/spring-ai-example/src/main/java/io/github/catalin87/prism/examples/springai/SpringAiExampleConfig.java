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
package io.github.catalin87.prism.examples.springai;

import io.github.catalin87.prism.spring.ai.advisor.PrismChatClientAdvisor;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans for the Spring AI example application. */
@Configuration(proxyBeanMethods = false)
class SpringAiExampleConfig {

  @Bean
  RecordingSpringAiChatModel recordingSpringAiChatModel() {
    return new RecordingSpringAiChatModel();
  }

  @Bean
  ChatClient exampleChatClient(
      RecordingSpringAiChatModel recordingSpringAiChatModel,
      PrismChatClientAdvisor prismChatClientAdvisor) {
    return ChatClient.builder(recordingSpringAiChatModel)
        .defaultAdvisors(prismChatClientAdvisor)
        .build();
  }

  static final class RecordingSpringAiChatModel implements ChatModel {
    private final List<String> prompts = new CopyOnWriteArrayList<>();

    @Override
    public @NonNull ChatResponse call(@NonNull Prompt prompt) {
      String content = prompt.getContents();
      prompts.add(content);
      return new ChatResponse(
          List.of(new Generation(new AssistantMessage("Spring AI reply: " + content))));
    }

    List<String> prompts() {
      return List.copyOf(prompts);
    }
  }
}
