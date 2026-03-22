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

import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.TokenGenerator;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import io.github.catalin87.prism.spring.ai.advisor.PrismChatClientAdvisor;
import io.github.catalin87.prism.spring.ai.advisor.PrismMetricsSink;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Auto-configuration entry point for Spring Prism. */
@AutoConfiguration
@ConditionalOnClass(PrismChatClientAdvisor.class)
@ConditionalOnProperty(
    prefix = "spring.prism",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(SpringPrismProperties.class)
public class SpringPrismAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  RulePackRegistrar rulePackRegistrar() {
    return new RulePackRegistrar();
  }

  @Bean("springPrismRulePacks")
  @ConditionalOnMissingBean(name = "springPrismRulePacks")
  List<PrismRulePack> springPrismRulePacks(
      RulePackRegistrar registrar, SpringPrismProperties properties) {
    return registrar.resolve(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  TokenGenerator prismTokenGenerator() {
    return new HmacSha256TokenGenerator();
  }

  @Bean
  @ConditionalOnMissingBean
  PrismVault prismVault(TokenGenerator prismTokenGenerator, SpringPrismProperties properties) {
    long ttlSeconds = Math.max(1L, properties.getTtl().toSeconds());
    byte[] secret = secretKey(properties);
    return new DefaultPrismVault(prismTokenGenerator, secret, ttlSeconds);
  }

  @Bean
  @ConditionalOnMissingBean
  ObservationRegistry observationRegistry() {
    return ObservationRegistry.NOOP;
  }

  @Bean
  @ConditionalOnMissingBean
  PrismRuntimeMetrics prismRuntimeMetrics() {
    return new PrismRuntimeMetrics();
  }

  @Bean
  @ConditionalOnMissingBean
  PrismMetricsSink prismMetricsSink(PrismRuntimeMetrics prismRuntimeMetrics) {
    return prismRuntimeMetrics;
  }

  @Bean
  @ConditionalOnMissingBean
  PrismChatClientAdvisor prismChatClientAdvisor(
      @Qualifier("springPrismRulePacks") List<PrismRulePack> springPrismRulePacks,
      PrismVault prismVault,
      ObservationRegistry observationRegistry,
      PrismMetricsSink prismMetricsSink,
      SpringPrismProperties properties) {
    return new PrismChatClientAdvisor(
        springPrismRulePacks,
        prismVault,
        observationRegistry,
        prismMetricsSink,
        properties.isSecurityStrictMode());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(MetricsController.class)
  MetricsController metricsController(
      PrismRuntimeMetrics prismRuntimeMetrics,
      @Qualifier("springPrismRulePacks") List<PrismRulePack> springPrismRulePacks,
      PrismVault prismVault) {
    return new MetricsController(prismRuntimeMetrics, springPrismRulePacks, prismVault);
  }

  private static byte[] secretKey(SpringPrismProperties properties) {
    return properties.getAppSecret().getBytes(StandardCharsets.UTF_8);
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "dev.langchain4j.model.chat.ChatModel")
  static class LangChain4jConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(io.github.catalin87.prism.langchain4j.PrismChatModel.class)
    @ConditionalOnSingleCandidate(dev.langchain4j.model.chat.ChatModel.class)
    io.github.catalin87.prism.langchain4j.PrismChatModel prismChatModel(
        dev.langchain4j.model.chat.ChatModel delegateChatModel,
        @Qualifier("springPrismRulePacks") List<PrismRulePack> springPrismRulePacks,
        PrismVault prismVault,
        ObservationRegistry observationRegistry,
        PrismRuntimeMetrics prismRuntimeMetrics,
        SpringPrismProperties properties) {
      return new io.github.catalin87.prism.langchain4j.PrismChatModel(
          delegateChatModel,
          springPrismRulePacks,
          prismVault,
          observationRegistry,
          prismRuntimeMetrics,
          properties.isSecurityStrictMode());
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(io.github.catalin87.prism.langchain4j.PrismStreamingChatModel.class)
    @ConditionalOnSingleCandidate(dev.langchain4j.model.chat.StreamingChatModel.class)
    io.github.catalin87.prism.langchain4j.PrismStreamingChatModel prismStreamingChatModel(
        dev.langchain4j.model.chat.StreamingChatModel delegateStreamingChatModel,
        @Qualifier("springPrismRulePacks") List<PrismRulePack> springPrismRulePacks,
        PrismVault prismVault,
        ObservationRegistry observationRegistry,
        PrismRuntimeMetrics prismRuntimeMetrics,
        SpringPrismProperties properties) {
      return new io.github.catalin87.prism.langchain4j.PrismStreamingChatModel(
          delegateStreamingChatModel,
          springPrismRulePacks,
          prismVault,
          observationRegistry,
          prismRuntimeMetrics,
          properties.isSecurityStrictMode());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
  static class RedisConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(type = "org.springframework.data.redis.core.StringRedisTemplate")
    PrismVault redisPrismVault(
        TokenGenerator prismTokenGenerator,
        SpringPrismProperties properties,
        org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate) {
      Duration ttl = properties.getTtl();
      byte[] secret = secretKey(properties);
      return new RedisPrismVault(stringRedisTemplate, prismTokenGenerator, secret, ttl);
    }
  }
}
