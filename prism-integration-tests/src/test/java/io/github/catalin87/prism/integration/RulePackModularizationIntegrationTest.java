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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.catalin87.prism.boot.autoconfigure.PrismActuatorEndpoint;
import io.github.catalin87.prism.boot.autoconfigure.PrismMetricsSnapshot;
import io.github.catalin87.prism.boot.autoconfigure.SpringPrismAutoConfiguration;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.rulepack.common.CommonRulePack;
import io.github.catalin87.prism.rulepack.common.autoconfigure.PrismCommonRulePackAutoConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RulePackModularizationIntegrationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  PrismCommonRulePackAutoConfiguration.class, SpringPrismAutoConfiguration.class))
          .withPropertyValues("spring.prism.app-secret=integration-secret-value");

  @Test
  void starterUsesModularCommonRulePackByDefaultWithoutChangingRuntimeStory() {
    contextRunner.run(
        context -> {
          List<PrismRulePack> rulePacks = context.getBean("springPrismRulePacks", List.class);

          assertThat(rulePacks).singleElement().isInstanceOf(CommonRulePack.class);
          assertThat(rulePacks).extracting(PrismRulePack::getName).containsExactly("UNIVERSAL");

          PrismMetricsSnapshot snapshot = context.getBean(PrismActuatorEndpoint.class).metrics();
          assertThat(snapshot.activeRulePacks()).containsExactly("UNIVERSAL");
          assertThat(snapshot.totalActiveRules()).isEqualTo(5);
        });
  }
}
