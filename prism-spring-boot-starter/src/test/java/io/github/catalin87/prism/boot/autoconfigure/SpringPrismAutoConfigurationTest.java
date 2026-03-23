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
package io.github.catalin87.prism.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.detector.universal.EmailDetector;
import io.github.catalin87.prism.core.ruleset.EuropeRulePack;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import io.github.catalin87.prism.langchain4j.PrismChatModel;
import io.github.catalin87.prism.langchain4j.PrismStreamingChatModel;
import io.github.catalin87.prism.mcp.PrismHttpMcpClient;
import io.github.catalin87.prism.mcp.PrismMcpClient;
import io.github.catalin87.prism.mcp.PrismMcpMetricsSink;
import io.github.catalin87.prism.mcp.PrismStdioMcpClient;
import io.github.catalin87.prism.spring.ai.advisor.PrismChatClientAdvisor;
import io.github.catalin87.prism.spring.ai.advisor.PrismMetricsSink;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Starter wiring tests for Spring Prism auto-configuration. */
class SpringPrismAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SpringPrismAutoConfiguration.class))
          .withPropertyValues("spring.prism.app-secret=test-secret-value");

  @Test
  void autoConfigurationCreatesDefaultBeans() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(PrismVault.class);
          assertThat(context).hasSingleBean(PrismChatClientAdvisor.class);
          assertThat(context).hasSingleBean(ObservationRegistry.class);
          assertThat(context).hasSingleBean(PrismRuntimeMetrics.class);
          assertThat(context).hasSingleBean(PrismMetricsSink.class);
          assertThat(context).hasSingleBean(PrismMcpMetricsSink.class);
          assertThat(context).hasSingleBean(PrismActuatorEndpoint.class);
          assertThat(context).doesNotHaveBean(MetricsController.class);
          assertThat(context).doesNotHaveBean(PrismMcpClient.class);

          List<PrismRulePack> rulePacks = getRulePacks(context);
          assertThat(rulePacks).hasSize(1);
          assertThat(rulePacks.get(0)).isInstanceOf(UniversalRulePack.class);
        });
  }

  @Test
  void disabledPropertySkipsAutoConfiguration() {
    contextRunner
        .withPropertyValues("spring.prism.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(PrismVault.class);
              assertThat(context).doesNotHaveBean(PrismChatClientAdvisor.class);
              assertThat(context).doesNotHaveBean(PrismActuatorEndpoint.class);
              assertThat(context).doesNotHaveBean(MetricsController.class);
              assertThat(context).doesNotHaveBean(PrismMcpClient.class);
            });
  }

  @Test
  void europeLocaleSelectsEuropeRulePack() {
    contextRunner
        .withPropertyValues("spring.prism.locales=RO")
        .run(
            context -> {
              List<PrismRulePack> rulePacks = getRulePacks(context);
              assertThat(rulePacks).hasSize(1);
              assertThat(rulePacks.get(0)).isInstanceOf(EuropeRulePack.class);
            });
  }

  @Test
  void disabledRulesAreFilteredFromResolvedPack() {
    contextRunner
        .withPropertyValues("spring.prism.disabled-rules=EMAIL,SSN")
        .run(
            context -> {
              List<PrismRulePack> rulePacks = getRulePacks(context);
              PrismRulePack pack = rulePacks.get(0);

              assertThat(pack.getDetectors())
                  .extracting(detector -> detector.getEntityType())
                  .doesNotContain("EMAIL", "SSN");
            });
  }

  @Test
  void propertiesBindingSupportsTtlAndStrictMode() {
    contextRunner
        .withPropertyValues(
            "spring.prism.security-strict-mode=true",
            "spring.prism.ttl=45m",
            "spring.prism.dashboard.audit-retention=20",
            "spring.prism.dashboard.history-retention=240",
            "spring.prism.dashboard.default-polling-seconds=45",
            "spring.prism.dashboard.alert-thresholds.scan-latency-warn-ms=40",
            "spring.prism.dashboard.alert-thresholds.token-backlog-critical=30")
        .run(
            context -> {
              SpringPrismProperties properties = context.getBean(SpringPrismProperties.class);
              assertThat(properties.isSecurityStrictMode()).isTrue();
              assertThat(properties.getTtl()).isEqualTo(Duration.ofMinutes(45));
              assertThat(properties.getDashboard().getAuditRetention()).isEqualTo(20);
              assertThat(properties.getDashboard().getHistoryRetention()).isEqualTo(240);
              assertThat(properties.getDashboard().getDefaultPollingSeconds()).isEqualTo(45);
              assertThat(properties.getDashboard().getAlertThresholds().getScanLatencyWarnMs())
                  .isEqualTo(40d);
              assertThat(properties.getDashboard().getAlertThresholds().getTokenBacklogCritical())
                  .isEqualTo(30L);
            });
  }

  @Test
  void invalidPropertyValuesFallBackToStarterDefaults() {
    contextRunner
        .withPropertyValues(
            "spring.prism.ttl=-15s", "spring.prism.app-secret=   ", "spring.prism.locales=")
        .run(
            context -> {
              SpringPrismProperties properties = context.getBean(SpringPrismProperties.class);

              assertThat(properties.getTtl()).isEqualTo(Duration.ofMinutes(30));
              assertThat(properties.getAppSecret()).isEqualTo("spring-prism-change-me");
              assertThat(properties.getLocales()).containsExactly("UNIVERSAL");
              assertThat(properties.getDashboard().getAuditRetention()).isEqualTo(12);
              assertThat(properties.getDashboard().getHistoryRetention()).isEqualTo(120);
              assertThat(context.getBean(PrismVault.class)).isInstanceOf(DefaultPrismVault.class);
            });
  }

  @Test
  void redisTemplateSwitchesVaultImplementation() {
    contextRunner
        .withUserConfiguration(RedisTemplateConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PrismVault.class);
              assertThat(context.getBean(PrismVault.class)).isInstanceOf(RedisPrismVault.class);
            });
  }

  @Test
  void defaultVaultRemainsLocalWhenRedisBeanIsAbsent() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(PrismVault.class);
          assertThat(context.getBean(PrismVault.class)).isInstanceOf(DefaultPrismVault.class);
        });
  }

  @Test
  void autoConfigurationStillLoadsWithoutRedisOnClasspath() {
    contextRunner
        .withClassLoader(new FilteredClassLoader("org.springframework.data.redis"))
        .run(
            context -> {
              assertThat(context).hasSingleBean(PrismVault.class);
              assertThat(context.getBean(PrismVault.class)).isInstanceOf(DefaultPrismVault.class);
            });
  }

  @Test
  void actuatorEndpointExposesRuntimeSnapshot() {
    contextRunner.run(
        context -> {
          PrismRuntimeMetrics runtimeMetrics = context.getBean(PrismRuntimeMetrics.class);
          runtimeMetrics.onDetected("UNIVERSAL", "EMAIL", 2);
          runtimeMetrics.onScanDuration("spring-ai", 15L);
          runtimeMetrics.onVaultTokenizeDuration("spring-ai", 30L);
          PrismActuatorEndpoint endpoint = context.getBean(PrismActuatorEndpoint.class);
          PrismMetricsSnapshot snapshot = endpoint.metrics();

          assertThat(snapshot.privacyScore()).isNotNull();
          assertThat(snapshot.privacyScore().score()).isBetween(0, 100);
          assertThat(snapshot.privacyScore().coverage().label()).isEqualTo("Coverage");
          assertThat(snapshot.activeRulePacks()).contains("UNIVERSAL");
          assertThat(snapshot.vaultType()).isNotBlank();
          assertThat(snapshot.durationMetrics())
              .containsKeys("spring-ai:scan", "spring-ai:vault-tokenize");
          assertThat(snapshot.rulePackMetrics())
              .extracting(RulePackMetric::name)
              .contains("UNIVERSAL");
          assertThat(snapshot.entityMetrics())
              .extracting(EntityMetric::entityType)
              .contains("EMAIL");
          assertThat(snapshot.integrationMetrics())
              .extracting(IntegrationMetric::name)
              .contains("spring-ai", "langchain4j");
          assertThat(snapshot.historyRollups())
              .extracting(HistoryRollup::key)
              .contains("recent", "5m", "15m", "1h");
          assertThat(snapshot.historySamples()).isNotEmpty();
          assertThat(snapshot.historyRetentionLimit()).isEqualTo(120);
          assertThat(snapshot.auditEvents()).isNotEmpty();
          assertThat(snapshot.auditRetentionLimit()).isEqualTo(12);
          assertThat(snapshot.tokenBacklog()).isGreaterThanOrEqualTo(0);
          assertThat(snapshot.dashboardConfiguration().defaultPollingSeconds()).isEqualTo(30);
        });
  }

  @Test
  void fallbackControllerLoadsWithoutActuatorOnClasspath() {
    contextRunner
        .withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate.endpoint"))
        .run(
            context -> {
              assertThat(context).hasSingleBean(MetricsController.class);
              assertThat(context).doesNotHaveBean(PrismActuatorEndpoint.class);

              PrismRuntimeMetrics runtimeMetrics = context.getBean(PrismRuntimeMetrics.class);
              runtimeMetrics.onScanDuration("spring-ai", 15L);
              PrismMetricsSnapshot snapshot = context.getBean(MetricsController.class).metrics();

              assertThat(snapshot.durationMetrics()).containsKey("spring-ai:scan");
              assertThat(snapshot.rulePackMetrics()).isNotEmpty();
              assertThat(snapshot.integrationMetrics())
                  .extracting(IntegrationMetric::name)
                  .contains("spring-ai");
              assertThat(snapshot.historySamples()).isNotEmpty();
              assertThat(snapshot.auditRetentionLimit()).isEqualTo(12);
            });
  }

  @Test
  void customRulesProduceCustomRulePack() {
    contextRunner
        .withPropertyValues(
            "spring.prism.custom-rules[0].name=INTERNAL_ID",
            "spring.prism.custom-rules[0].pattern=ID-\\d{5}")
        .run(
            context -> {
              List<PrismRulePack> rulePacks = getRulePacks(context);

              assertThat(rulePacks).hasSize(2);
              assertThat(rulePacks.get(1).getName()).isEqualTo("CUSTOM");
              assertThat(rulePacks.get(1).getDetectors())
                  .extracting(detector -> detector.getEntityType())
                  .containsExactly("INTERNAL_ID");
            });
  }

  @Test
  void mcpDefaultsToDisabledUntilExplicitlyEnabled() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(PrismMcpClient.class));
  }

  @Test
  void mcpHttpConfigurationCreatesHttpClient() {
    contextRunner
        .withPropertyValues(
            "spring.prism.mcp.enabled=true",
            "spring.prism.mcp.transport=streamable-http",
            "spring.prism.mcp.http.base-url=http://localhost:8181/mcp")
        .run(
            context -> {
              assertThat(context).hasSingleBean(PrismMcpClient.class);
              assertThat(context.getBean(PrismMcpClient.class))
                  .isInstanceOf(PrismHttpMcpClient.class);
            });
  }

  @Test
  void mcpStdioConfigurationCreatesStdioClient() {
    contextRunner
        .withPropertyValues(
            "spring.prism.mcp.enabled=true",
            "spring.prism.mcp.transport=stdio",
            "spring.prism.mcp.stdio.command=echo")
        .run(
            context -> {
              assertThat(context).hasSingleBean(PrismMcpClient.class);
              assertThat(context.getBean(PrismMcpClient.class))
                  .isInstanceOf(PrismStdioMcpClient.class);
            });
  }

  @Test
  void mcpOverridesStrictModeWhenConfigured() {
    contextRunner
        .withPropertyValues(
            "spring.prism.security-strict-mode=false",
            "spring.prism.mcp.enabled=true",
            "spring.prism.mcp.security-strict-mode=true",
            "spring.prism.mcp.transport=streamable-http",
            "spring.prism.mcp.http.base-url=http://localhost:8181/mcp")
        .run(
            context -> {
              SpringPrismProperties properties = context.getBean(SpringPrismProperties.class);
              assertThat(
                      properties
                          .getMcp()
                          .resolveSecurityStrictMode(properties.isSecurityStrictMode()))
                  .isTrue();
            });
  }

  @Test
  void incompleteCustomRulesAreIgnored() {
    contextRunner
        .withPropertyValues(
            "spring.prism.custom-rules[0].name=   ",
            "spring.prism.custom-rules[0].pattern=ID-\\d{5}",
            "spring.prism.custom-rules[1].name=INTERNAL_ID",
            "spring.prism.custom-rules[1].pattern=   ")
        .run(
            context -> {
              List<PrismRulePack> rulePacks = getRulePacks(context);
              assertThat(rulePacks).hasSize(1);
              assertThat(rulePacks.get(0)).isInstanceOf(UniversalRulePack.class);
            });
  }

  @Test
  void disabledRulesAlsoFilterCustomRules() {
    contextRunner
        .withPropertyValues(
            "spring.prism.custom-rules[0].name=EMAIL",
            "spring.prism.custom-rules[0].pattern=ID-\\d{5}",
            "spring.prism.disabled-rules=EMAIL")
        .run(
            context -> {
              List<PrismRulePack> rulePacks = getRulePacks(context);

              assertThat(rulePacks).isNotEmpty();
              assertThat(rulePacks.stream().flatMap(pack -> pack.getDetectors().stream()))
                  .extracting(detector -> detector.getEntityType())
                  .doesNotContain(new EmailDetector().getEntityType());
            });
  }

  @Test
  void userProvidedVaultBeanOverridesStarterDefault() {
    contextRunner
        .withUserConfiguration(CustomVaultConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PrismVault.class);
              assertThat(context.getBean(PrismVault.class))
                  .isSameAs(context.getBean("customPrismVault"));
            });
  }

  @Test
  void userProvidedRulePackBeanOverridesResolvedDefaults() {
    contextRunner
        .withUserConfiguration(CustomRulePackConfiguration.class)
        .run(
            context -> {
              List<PrismRulePack> rulePacks = getRulePacks(context);
              assertThat(rulePacks).hasSize(1);
              assertThat(rulePacks.get(0).getName()).isEqualTo("CUSTOM_OVERRIDE");
            });
  }

  @Test
  void singleLangChainChatModelIsWrappedAsPrimaryBean() {
    contextRunner
        .withUserConfiguration(LangChainChatModelConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PrismChatModel.class);
              assertThat(context.getBean(ChatModel.class)).isInstanceOf(PrismChatModel.class);
            });
  }

  @Test
  void singleLangChainStreamingChatModelIsWrappedAsPrimaryBean() {
    contextRunner
        .withUserConfiguration(LangChainStreamingChatModelConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PrismStreamingChatModel.class);
              assertThat(context.getBean(StreamingChatModel.class))
                  .isInstanceOf(PrismStreamingChatModel.class);
            });
  }

  @Test
  void multipleChatModelDelegatesWithoutPrimaryDoNotCreateWrapper() {
    contextRunner
        .withUserConfiguration(MultipleLangChainChatModelsConfiguration.class)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(PrismChatModel.class);
              assertThat(context.getBeansOfType(ChatModel.class)).hasSize(2);
            });
  }

  @Test
  void filteredLangChainClassesSkipLangChainAutoConfiguration() {
    contextRunner
        .withClassLoader(
            new FilteredClassLoader("dev.langchain4j", "io.github.catalin87.prism.langchain4j"))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean("prismChatModel");
              assertThat(context).doesNotHaveBean("prismStreamingChatModel");
            });
  }

  @Test
  void runtimeMetricsAlsoBackLangChainMetricsSink() {
    contextRunner
        .withUserConfiguration(LangChainChatModelConfiguration.class)
        .run(
            context -> {
              assertThat(context.getBean(PrismRuntimeMetrics.class))
                  .isSameAs(
                      context.getBean(
                          io.github.catalin87.prism.langchain4j.PrismMetricsSink.class));
            });
  }

  @SuppressWarnings("unchecked")
  private static List<PrismRulePack> getRulePacks(
      org.springframework.context.ApplicationContext context) {
    return (List<PrismRulePack>) context.getBean("springPrismRulePacks");
  }

  @Configuration(proxyBeanMethods = false)
  static class RedisTemplateConfiguration {

    @Bean
    StringRedisTemplate stringRedisTemplate() {
      return mock(StringRedisTemplate.class);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomVaultConfiguration {

    @Bean("customPrismVault")
    PrismVault customPrismVault() {
      return mock(PrismVault.class);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomRulePackConfiguration {

    @Bean("springPrismRulePacks")
    List<PrismRulePack> springPrismRulePacks() {
      return List.of(
          new PrismRulePack() {
            @Override
            public String getName() {
              return "CUSTOM_OVERRIDE";
            }

            @Override
            public List<io.github.catalin87.prism.core.PiiDetector> getDetectors() {
              return List.of();
            }
          });
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class LangChainChatModelConfiguration {

    @Bean("delegateChatModel")
    ChatModel delegateChatModel() {
      return new EchoChatModel();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class LangChainStreamingChatModelConfiguration {

    @Bean("delegateStreamingChatModel")
    StreamingChatModel delegateStreamingChatModel() {
      return new NoOpStreamingChatModel();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class MultipleLangChainChatModelsConfiguration {

    @Bean
    ChatModel firstChatModel() {
      return new EchoChatModel();
    }

    @Bean
    ChatModel secondChatModel() {
      return new EchoChatModel();
    }
  }

  static class EchoChatModel implements ChatModel {
    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
      return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
    }
  }

  static class NoOpStreamingChatModel implements StreamingChatModel {
    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
      handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());
    }
  }
}
