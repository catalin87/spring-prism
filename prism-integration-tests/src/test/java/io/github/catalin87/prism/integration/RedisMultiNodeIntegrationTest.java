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
package io.github.catalin87.prism.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.catalin87.prism.boot.autoconfigure.PrismActuatorEndpoint;
import io.github.catalin87.prism.boot.autoconfigure.PrismMetricsSnapshot;
import io.github.catalin87.prism.boot.autoconfigure.SpringPrismAutoConfiguration;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RedisMultiNodeIntegrationTest {

  private static final String MODEL = "gpt-4o-mini";
  private static final String DEFAULT_SECRET = "cluster-shared-secret-32-bytes!!";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Pattern TOKEN_PATTERN = Pattern.compile("<PRISM_[A-Z0-9_-]+>");

  @Container
  @SuppressWarnings("resource")
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379);

  private static WireMockServer wireMockServer;

  private final List<IntegrationNode> nodes = new ArrayList<>();

  @BeforeAll
  static void startWireMock() {
    wireMockServer =
        new WireMockServer(
            WireMockConfiguration.wireMockConfig()
                .dynamicPort()
                .http2PlainDisabled(true)
                .http2TlsDisabled(true));
    wireMockServer.start();
  }

  @AfterEach
  void tearDown() {
    for (IntegrationNode node : nodes) {
      node.close();
    }
    nodes.clear();
    wireMockServer.resetAll();
  }

  @Test
  void deterministicTokenizationMatchesSupportedUniversalDetectors() throws Exception {
    IntegrationNode nodeA = startNode("node-a", DEFAULT_SECRET, Duration.ofMinutes(5));

    String prompt =
        "Contact user@example.com with SSN 123-45-6789 and card 4111 1111 1111 1111 today.";
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(chatCompletion("ACK"))));

    String response = nodeA.client().prompt().user(prompt).call().content();

    assertThat(response).isEqualTo("ACK");
    String outboundPrompt = nodeA.lastPromptContent();
    assertThat(outboundPrompt)
        .doesNotContain("user@example.com", "123-45-6789", "4111 1111 1111 1111");
    assertThat(outboundPrompt).contains("PRISM_EMAIL_", "PRISM_SSN_", "PRISM_CREDIT_CARD_");
    assertThat(extractTokens(outboundPrompt)).hasSize(3);
    assertThat(new UniversalRulePack().getName()).isEqualTo("UNIVERSAL");
  }

  @Test
  void restoresTokensAcrossNodesUsingSharedRedisVault() throws Exception {
    IntegrationNode nodeA = startNode("node-a", DEFAULT_SECRET, Duration.ofMinutes(5));
    IntegrationNode nodeB = startNode("node-b", DEFAULT_SECRET, Duration.ofMinutes(5));

    String prompt = "Email user@example.com and SSN 123-45-6789 require follow-up.";
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(chatCompletion("ACK"))));
    nodeA.client().prompt().user(prompt).call().content();

    String sanitizedPrompt = nodeA.lastPromptContent();
    assertThat(sanitizedPrompt).contains("PRISM_EMAIL_", "PRISM_SSN_");

    wireMockServer.resetRequests();
    String noisyResponse =
        "Lorem ipsum context. Original request: "
            + sanitizedPrompt
            + " Additional noise after restore.";
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(chatCompletion(noisyResponse))));

    String restored = nodeB.client().prompt().user("restore distributed response").call().content();

    assertThat(restored)
        .isEqualTo(
            "Lorem ipsum context. Original request: "
                + prompt
                + " Additional noise after restore.");

    PrismMetricsSnapshot snapshot = nodeB.actuatorEndpoint().metrics();
    assertThat(snapshot.configuredVaultMode()).isEqualTo("REDIS");
    assertThat(snapshot.distributedVault()).isTrue();
    assertThat(snapshot.sharedVaultReady()).isTrue();
    assertThat(snapshot.vaultReadinessStatus()).isEqualTo("READY");
    assertThat(snapshot.durationMetrics())
        .containsKeys("spring-ai:scan", "spring-ai:vault-detokenize");
  }

  @Test
  void expiresRedisTokensAccordingToConfiguredTtl() throws Exception {
    IntegrationNode nodeA = startNode("node-a", DEFAULT_SECRET, Duration.ofSeconds(1));
    IntegrationNode nodeB = startNode("node-b", DEFAULT_SECRET, Duration.ofSeconds(1));

    String prompt = "Send the note to user@example.com.";
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(chatCompletion("ACK"))));
    nodeA.client().prompt().user(prompt).call().content();

    String token = extractTokens(nodeA.lastPromptContent()).getFirst();
    Long ttlSeconds = nodeA.redisTemplate().getExpire(token);
    assertThat(ttlSeconds).isNotNull();
    assertThat(ttlSeconds).isPositive();

    awaitTokenExpiry(nodeA.redisTemplate(), token);

    wireMockServer.resetAll();
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(okJson(chatCompletion("Expired token check " + token))));

    String restored = nodeB.client().prompt().user("restore expired").call().content();

    assertThat(restored).contains(token).doesNotContain("user@example.com");
  }

  @Test
  void mismatchedSecretsPreventCrossNodeRestore() throws Exception {
    IntegrationNode nodeA = startNode("node-a", DEFAULT_SECRET, Duration.ofMinutes(5));
    IntegrationNode nodeB =
        startNode("node-b", "different-cluster-secret-32-byte", Duration.ofMinutes(5));

    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(chatCompletion("ACK"))));
    nodeA.client().prompt().user("Reach user@example.com now.").call().content();

    String token = extractTokens(nodeA.lastPromptContent()).getFirst();
    wireMockServer.resetAll();
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(okJson(chatCompletion("Secret mismatch " + token))));

    String restored = nodeB.client().prompt().user("restore mismatch").call().content();

    assertThat(restored).contains(token).doesNotContain("user@example.com");
  }

  @Test
  void handlesLargePayloadsAndPublishesObservabilityMetrics() throws Exception {
    IntegrationNode nodeA = startNode("node-a", DEFAULT_SECRET, Duration.ofMinutes(5));
    IntegrationNode nodeB = startNode("node-b", DEFAULT_SECRET, Duration.ofMinutes(5));

    String prompt = largePayload();
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(chatCompletion("ACK"))));

    long tokenizeStart = System.nanoTime();
    nodeA.client().prompt().user(prompt).call().content();
    long tokenizeNanos = System.nanoTime() - tokenizeStart;

    String sanitizedPrompt = nodeA.lastPromptContent();
    assertThat(extractTokens(sanitizedPrompt)).hasSizeGreaterThan(300);

    wireMockServer.resetRequests();
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(okJson(chatCompletion("Payload replay " + sanitizedPrompt))));

    long restoreStart = System.nanoTime();
    String restored = nodeB.client().prompt().user("restore payload").call().content();
    long restoreNanos = System.nanoTime() - restoreStart;

    assertThat(restored).contains("rag-user-00@example.com", "123-45-6700");
    assertThat(tokenizeNanos).isPositive();
    assertThat(restoreNanos).isPositive();

    PrismMetricsSnapshot nodeASnapshot = nodeA.actuatorEndpoint().metrics();
    PrismMetricsSnapshot nodeBSnapshot = nodeB.actuatorEndpoint().metrics();
    assertThat(nodeASnapshot.durationMetrics())
        .containsKeys("spring-ai:scan", "spring-ai:vault-tokenize");
    assertThat(nodeBSnapshot.durationMetrics())
        .containsKeys("spring-ai:scan", "spring-ai:vault-detokenize");
    assertThat(nodeASnapshot.tokenizedCount()).isPositive();
    assertThat(nodeBSnapshot.detokenizedCount()).isPositive();
  }

  @Test
  void failsClosedWhenRedisBecomesUnavailableDuringTokenizeAndRestore() throws Exception {
    IntegrationNode nodeA = startNode("node-a", DEFAULT_SECRET, Duration.ofMinutes(5));
    IntegrationNode nodeB = startNode("node-b", DEFAULT_SECRET, Duration.ofMinutes(5));

    String originalPrompt = "Reach user@example.com about SSN 123-45-6789.";
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(chatCompletion("ACK"))));
    nodeA.client().prompt().user(originalPrompt).call().content();

    String tokenizedPrompt = nodeA.lastPromptContent();
    assertThat(tokenizedPrompt).contains("PRISM_EMAIL_", "PRISM_SSN_");

    redis.stop();
    try {
      wireMockServer.resetAll();
      wireMockServer.stubFor(
          post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(chatCompletion("ACK"))));

      assertThatThrownBy(
              () ->
                  nodeA
                      .client()
                      .prompt()
                      .user("Outage tokenize for user-two@example.com and SSN 987-65-4321.")
                      .call()
                      .content())
          .isInstanceOf(Exception.class)
          .hasMessageContaining("Redis");

      wireMockServer.resetAll();
      wireMockServer.stubFor(
          post(urlEqualTo("/v1/chat/completions"))
              .willReturn(okJson(chatCompletion("Restore after outage " + tokenizedPrompt))));

      assertThatThrownBy(() -> nodeB.client().prompt().user("restore outage").call().content())
          .isInstanceOf(Exception.class)
          .hasMessageContaining("Redis");
    } finally {
      redis.start();
    }
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  private IntegrationNode startNode(String nodeName, String secret, Duration ttl) {
    ConfigurableApplicationContext context =
        new SpringApplicationBuilder(TestNodeConfiguration.class)
            .web(WebApplicationType.NONE)
            .properties(
                "spring.application.name=" + nodeName,
                "spring.main.banner-mode=off",
                "spring.prism.enabled=true",
                "spring.prism.app-secret=" + secret,
                "spring.prism.vault.type=redis",
                "spring.prism.ttl=" + ttl,
                "spring.data.redis.host=" + redis.getHost(),
                "spring.data.redis.port=" + redis.getMappedPort(6379))
            .run();
    IntegrationNode node = new IntegrationNode(context);
    nodes.add(node);
    return node;
  }

  private static String chatCompletion(String content) {
    return """
        {
          "id": "chatcmpl-test",
          "object": "chat.completion",
          "created": 1710000001,
          "model": "%s",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": %s
              },
              "finish_reason": "stop"
            }
          ]
        }
        """
        .formatted(MODEL, OBJECT_MAPPER.valueToTree(content).toString());
  }

  private static List<String> extractTokens(String text) {
    Matcher matcher = TOKEN_PATTERN.matcher(text);
    List<String> tokens = new ArrayList<>();
    while (matcher.find()) {
      tokens.add(matcher.group());
    }
    return tokens;
  }

  private static void awaitTokenExpiry(StringRedisTemplate redisTemplate, String token)
      throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      if (!Boolean.TRUE.equals(redisTemplate.hasKey(token))) {
        return;
      }
      Thread.sleep(100L);
    }
    assertThat(redisTemplate.hasKey(token)).isFalse();
  }

  private static String largePayload() {
    StringBuilder builder = new StringBuilder(16384);
    for (int i = 0; i < 120; i++) {
      builder
          .append("Retrieved record ")
          .append(i)
          .append(": email rag-user-")
          .append(String.format("%02d", i))
          .append("@example.com, SSN 123-45-")
          .append(String.format("%04d", 6700 + i))
          .append(", card 4111 1111 1111 1111, phone +40 712 345 ")
          .append(String.format("%03d", i))
          .append(". Retrieved context segment ")
          .append(i)
          .append(" remains privacy-sensitive. ");
    }
    return builder.toString();
  }

  @SpringBootTest
  @ImportAutoConfiguration(SpringPrismAutoConfiguration.class)
  @Configuration(proxyBeanMethods = false)
  @ConfigurationPropertiesScan
  static class TestNodeConfiguration {

    @Bean
    RedisConnectionFactory redisConnectionFactory(
        org.springframework.core.env.Environment environment) {
      String host = environment.getRequiredProperty("spring.data.redis.host");
      int port = environment.getRequiredProperty("spring.data.redis.port", Integer.class);
      LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(host, port);
      connectionFactory.afterPropertiesSet();
      return connectionFactory;
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
      return new StringRedisTemplate(connectionFactory);
    }
  }

  private record IntegrationNode(
      ConfigurableApplicationContext context,
      ChatClient client,
      PrismActuatorEndpoint actuatorEndpoint,
      StringRedisTemplate redisTemplate) {

    private IntegrationNode(ConfigurableApplicationContext context) {
      this(
          context,
          ChatClient.builder(
                  new OpenAiChatModel(
                      new OpenAiApi(wireMockServer.baseUrl(), "test-key"),
                      OpenAiChatOptions.builder().model(MODEL).temperature(0.0).build()))
              .defaultAdvisors(
                  context.getBean(
                      io.github.catalin87.prism.spring.ai.advisor.PrismChatClientAdvisor.class))
              .build(),
          context.getBean(PrismActuatorEndpoint.class),
          context.getBean(StringRedisTemplate.class));
    }

    private String lastPromptContent() throws Exception {
      List<com.github.tomakehurst.wiremock.stubbing.ServeEvent> events =
          wireMockServer.getAllServeEvents();
      assertThat(events).isNotEmpty();
      JsonNode body = OBJECT_MAPPER.readTree(events.getLast().getRequest().getBodyAsString());
      return body.path("messages").get(0).path("content").asText();
    }

    private void close() {
      context.close();
    }
  }
}
