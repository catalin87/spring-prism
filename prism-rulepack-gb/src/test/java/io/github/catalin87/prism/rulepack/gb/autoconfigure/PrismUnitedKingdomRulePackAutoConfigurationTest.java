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
package io.github.catalin87.prism.rulepack.gb.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.catalin87.prism.rulepack.gb.UnitedKingdomRulePack;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PrismUnitedKingdomRulePackAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(PrismUnitedKingdomRulePackAutoConfiguration.class));

  @Test
  void autoConfigurationRegistersUnitedKingdomRulePack() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(UnitedKingdomRulePack.class);
          UnitedKingdomRulePack pack = context.getBean(UnitedKingdomRulePack.class);
          assertThat(pack.getName()).isEqualTo("GB");
          assertThat(pack.getActivationAliases()).contains("GB", "UK", "GBR", "UNITED_KINGDOM");
          assertThat(pack.getDetectors()).hasSize(8);
        });
  }
}
