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
package io.github.catalin87.prism.rulepack.fr.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.catalin87.prism.rulepack.fr.FranceRulePack;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PrismFranceRulePackAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PrismFranceRulePackAutoConfiguration.class));

  @Test
  void autoConfigurationRegistersFranceRulePack() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(FranceRulePack.class);
          FranceRulePack pack = context.getBean(FranceRulePack.class);
          assertThat(pack.getName()).isEqualTo("FR");
          assertThat(pack.getActivationAliases()).contains("FR", "FRA", "FRANCE");
          assertThat(pack.getDetectors()).hasSize(9);
        });
  }
}
