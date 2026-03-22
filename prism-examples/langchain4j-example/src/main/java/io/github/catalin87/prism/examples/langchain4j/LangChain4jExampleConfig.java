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
package io.github.catalin87.prism.examples.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans for the LangChain4j example application. */
@Configuration(proxyBeanMethods = false)
class LangChain4jExampleConfig {

  @Bean("delegateChatModel")
  RecordingLangChainChatModel delegateChatModel() {
    return new RecordingLangChainChatModel();
  }

  static final class RecordingLangChainChatModel implements ChatModel {
    private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
      requests.add(chatRequest);
      return ChatResponse.builder()
          .aiMessage(
              AiMessage.from("LangChain4j reply: " + chatRequest.messages().getLast().toString()))
          .build();
    }

    List<ChatRequest> requests() {
      return List.copyOf(requests);
    }
  }
}
