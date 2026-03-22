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

import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.ruleset.EuropeRulePack;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import io.github.catalin87.prism.spring.ai.advisor.PrismChatClientAdvisor;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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

          List<PrismRulePack> rulePacks = getRulePacks(context);
          assertThat(rulePacks).hasSize(1);
          assertThat(rulePacks.get(0)).isInstanceOf(UniversalRulePack.class);
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
        .withPropertyValues("spring.prism.security-strict-mode=true", "spring.prism.ttl=45m")
        .run(
            context -> {
              SpringPrismProperties properties = context.getBean(SpringPrismProperties.class);
              assertThat(properties.isSecurityStrictMode()).isTrue();
              assertThat(properties.getTtl()).isEqualTo(Duration.ofMinutes(45));
            });
  }

  @SuppressWarnings("unchecked")
  private static List<PrismRulePack> getRulePacks(
      org.springframework.context.ApplicationContext context) {
    return (List<PrismRulePack>) context.getBean("springPrismRulePacks");
  }
}
