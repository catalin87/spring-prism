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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.catalin87.prism.boot.autoconfigure.PrismRulePackRegistryEndpoint.RulePackRegistrySnapshot;
import io.github.catalin87.prism.boot.autoconfigure.PrismRulePackRegistryEndpoint.UnknownRulePackException;
import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismRulePackRegistry;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Validates the actuator endpoint contract: read operation returns the live registry, write
 * operation swaps to a named bean, and an unknown name rejects the request without mutating state.
 *
 * <p>This test uses an {@link ApplicationContextRunner} to stand up a real Spring context with
 * {@link PrismRulePack} beans, the {@link PrismRulePackRegistry}, and the endpoint wired in. We
 * exercise the {@code @ReadOperation} / {@code @WriteOperation} methods directly — the Actuator
 * framework dispatches HTTP requests to the same methods, so this fully covers the behaviour that
 * would be observed via {@code MockMvc} against {@code /actuator/prism-rules}.
 */
class PrismRulePackRegistryEndpointTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(EndpointTestConfiguration.class);

  @Test
  void readOperationReturnsActivePacksFromRegistry() {
    contextRunner.run(
        context -> {
          PrismRulePackRegistryEndpoint endpoint =
              context.getBean(PrismRulePackRegistryEndpoint.class);

          RulePackRegistrySnapshot snapshot = endpoint.rules();

          assertThat(snapshot.active()).extracting("name").containsExactly("UNIVERSAL");
          assertThat(snapshot.active().getFirst().detectorCount()).isPositive();
          assertThat(snapshot.availablePackBeanNames())
              .contains("universalRulePack", "alternateRulePack");
        });
  }

  @Test
  void readSingleReturnsMatchingActivePack() {
    contextRunner.run(
        context -> {
          PrismRulePackRegistryEndpoint endpoint =
              context.getBean(PrismRulePackRegistryEndpoint.class);

          assertThat(endpoint.rule("UNIVERSAL")).isNotNull();
          assertThat(endpoint.rule("UNIVERSAL").name()).isEqualTo("UNIVERSAL");
          assertThat(endpoint.rule("MISSING")).isNull();
        });
  }

  @Test
  void writeOperationSwapsActivePacksByBeanName() {
    contextRunner.run(
        context -> {
          PrismRulePackRegistryEndpoint endpoint =
              context.getBean(PrismRulePackRegistryEndpoint.class);
          PrismRulePackRegistry registry = context.getBean(PrismRulePackRegistry.class);

          RulePackRegistrySnapshot postSwap = endpoint.replace(List.of("alternateRulePack"));

          assertThat(postSwap.active()).extracting("name").containsExactly("ALTERNATE");
          assertThat(registry.snapshot()).extracting("name").containsExactly("ALTERNATE");
        });
  }

  @Test
  void writeOperationRejectsUnknownBeanNameAndDoesNotMutateRegistry() {
    contextRunner.run(
        context -> {
          PrismRulePackRegistryEndpoint endpoint =
              context.getBean(PrismRulePackRegistryEndpoint.class);
          PrismRulePackRegistry registry = context.getBean(PrismRulePackRegistry.class);

          assertThatThrownBy(() -> endpoint.replace(List.of("alternateRulePack", "doesNotExist")))
              .isInstanceOf(UnknownRulePackException.class)
              .satisfies(
                  exception -> {
                    UnknownRulePackException unknown = (UnknownRulePackException) exception;
                    assertThat(unknown.unknownNames()).containsExactly("doesNotExist");
                    assertThat(unknown.availableNames())
                        .contains("universalRulePack", "alternateRulePack");
                  });

          assertThat(registry.snapshot()).extracting("name").containsExactly("UNIVERSAL");
        });
  }

  @Test
  void writeOperationAcceptsEmptyListAndClearsRegistry() {
    contextRunner.run(
        context -> {
          PrismRulePackRegistryEndpoint endpoint =
              context.getBean(PrismRulePackRegistryEndpoint.class);
          PrismRulePackRegistry registry = context.getBean(PrismRulePackRegistry.class);

          RulePackRegistrySnapshot postSwap = endpoint.replace(List.of());

          assertThat(postSwap.active()).isEmpty();
          assertThat(registry.snapshot()).isEmpty();
        });
  }

  @Test
  void writeOperationPreservesOrderingFromRequest() {
    contextRunner.run(
        context -> {
          PrismRulePackRegistryEndpoint endpoint =
              context.getBean(PrismRulePackRegistryEndpoint.class);

          RulePackRegistrySnapshot postSwap =
              endpoint.replace(List.of("alternateRulePack", "universalRulePack"));

          assertThat(postSwap.active())
              .extracting("name")
              .containsExactly("ALTERNATE", "UNIVERSAL");
        });
  }

  @Configuration(proxyBeanMethods = false)
  static class EndpointTestConfiguration {

    @Bean
    PrismRulePack universalRulePack() {
      return new UniversalRulePack();
    }

    @Bean
    PrismRulePack alternateRulePack() {
      return new AlternateRulePack();
    }

    @Bean
    PrismRulePackRegistry prismRulePackRegistry(PrismRulePack universalRulePack) {
      return new PrismRulePackRegistry(List.of(universalRulePack));
    }

    @Bean
    PrismRulePackRegistryEndpoint prismRulePackRegistryEndpoint(
        PrismRulePackRegistry prismRulePackRegistry, ListableBeanFactory beanFactory) {
      return new PrismRulePackRegistryEndpoint(prismRulePackRegistry, beanFactory);
    }
  }

  /** Minimal alternate rule pack used to verify swap behaviour. */
  static final class AlternateRulePack implements PrismRulePack {

    @Override
    public String getName() {
      return "ALTERNATE";
    }

    @Override
    public List<PiiDetector> getDetectors() {
      return List.of();
    }
  }
}
