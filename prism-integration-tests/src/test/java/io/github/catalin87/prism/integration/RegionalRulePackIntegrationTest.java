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
import io.github.catalin87.prism.rulepack.common.autoconfigure.PrismCommonRulePackAutoConfiguration;
import io.github.catalin87.prism.rulepack.de.GermanyRulePack;
import io.github.catalin87.prism.rulepack.de.autoconfigure.PrismGermanyRulePackAutoConfiguration;
import io.github.catalin87.prism.rulepack.fr.FranceRulePack;
import io.github.catalin87.prism.rulepack.fr.autoconfigure.PrismFranceRulePackAutoConfiguration;
import io.github.catalin87.prism.rulepack.gb.UnitedKingdomRulePack;
import io.github.catalin87.prism.rulepack.gb.autoconfigure.PrismUnitedKingdomRulePackAutoConfiguration;
import io.github.catalin87.prism.rulepack.nl.NetherlandsRulePack;
import io.github.catalin87.prism.rulepack.nl.autoconfigure.PrismNetherlandsRulePackAutoConfiguration;
import io.github.catalin87.prism.rulepack.pl.PolandRulePack;
import io.github.catalin87.prism.rulepack.pl.autoconfigure.PrismPolandRulePackAutoConfiguration;
import io.github.catalin87.prism.rulepack.ro.RomaniaRulePack;
import io.github.catalin87.prism.rulepack.ro.autoconfigure.PrismRomaniaRulePackAutoConfiguration;
import io.github.catalin87.prism.rulepack.us.UsRulePack;
import io.github.catalin87.prism.rulepack.us.autoconfigure.PrismUsRulePackAutoConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RegionalRulePackIntegrationTest {

  @Test
  void roLocaleUsesRomaniaRulePackAndPublishesRegionalSnapshot() {
    ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PrismCommonRulePackAutoConfiguration.class,
                    PrismRomaniaRulePackAutoConfiguration.class,
                    SpringPrismAutoConfiguration.class))
            .withPropertyValues(
                "spring.prism.app-secret=integration-secret-value", "spring.prism.locales=RO");

    contextRunner.run(
        context -> {
          @SuppressWarnings("unchecked")
          List<PrismRulePack> rulePacks =
              (List<PrismRulePack>) context.getBean("springPrismRulePacks", List.class);

          assertThat(rulePacks).singleElement().isInstanceOf(RomaniaRulePack.class);
          assertThat(rulePacks).extracting(PrismRulePack::getName).containsExactly("RO");

          PrismMetricsSnapshot snapshot = context.getBean(PrismActuatorEndpoint.class).metrics();
          assertThat(snapshot.activeRulePacks()).containsExactly("RO");
          assertThat(snapshot.totalActiveRules()).isEqualTo(8);
        });
  }

  @Test
  void usLocaleUsesUsRulePackAndPublishesRegionalSnapshot() {
    ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PrismCommonRulePackAutoConfiguration.class,
                    PrismUsRulePackAutoConfiguration.class,
                    SpringPrismAutoConfiguration.class))
            .withPropertyValues(
                "spring.prism.app-secret=integration-secret-value", "spring.prism.locales=US");

    contextRunner.run(
        context -> {
          @SuppressWarnings("unchecked")
          List<PrismRulePack> rulePacks =
              (List<PrismRulePack>) context.getBean("springPrismRulePacks", List.class);

          assertThat(rulePacks).singleElement().isInstanceOf(UsRulePack.class);
          assertThat(rulePacks).extracting(PrismRulePack::getName).containsExactly("US");

          PrismMetricsSnapshot snapshot = context.getBean(PrismActuatorEndpoint.class).metrics();
          assertThat(snapshot.activeRulePacks()).containsExactly("US");
          assertThat(snapshot.totalActiveRules()).isEqualTo(7);
        });
  }

  @Test
  void plLocaleUsesPolandRulePackAndPublishesRegionalSnapshot() {
    ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PrismCommonRulePackAutoConfiguration.class,
                    PrismPolandRulePackAutoConfiguration.class,
                    SpringPrismAutoConfiguration.class))
            .withPropertyValues(
                "spring.prism.app-secret=integration-secret-value", "spring.prism.locales=PL");

    contextRunner.run(
        context -> {
          @SuppressWarnings("unchecked")
          List<PrismRulePack> rulePacks =
              (List<PrismRulePack>) context.getBean("springPrismRulePacks", List.class);

          assertThat(rulePacks).singleElement().isInstanceOf(PolandRulePack.class);
          assertThat(rulePacks).extracting(PrismRulePack::getName).containsExactly("PL");

          PrismMetricsSnapshot snapshot = context.getBean(PrismActuatorEndpoint.class).metrics();
          assertThat(snapshot.activeRulePacks()).containsExactly("PL");
          assertThat(snapshot.totalActiveRules()).isEqualTo(8);
        });
  }

  @Test
  void nlLocaleUsesNetherlandsRulePackAndPublishesRegionalSnapshot() {
    ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PrismCommonRulePackAutoConfiguration.class,
                    PrismNetherlandsRulePackAutoConfiguration.class,
                    SpringPrismAutoConfiguration.class))
            .withPropertyValues(
                "spring.prism.app-secret=integration-secret-value", "spring.prism.locales=NL");

    contextRunner.run(
        context -> {
          @SuppressWarnings("unchecked")
          List<PrismRulePack> rulePacks =
              (List<PrismRulePack>) context.getBean("springPrismRulePacks", List.class);

          assertThat(rulePacks).singleElement().isInstanceOf(NetherlandsRulePack.class);
          assertThat(rulePacks).extracting(PrismRulePack::getName).containsExactly("NL");

          PrismMetricsSnapshot snapshot = context.getBean(PrismActuatorEndpoint.class).metrics();
          assertThat(snapshot.activeRulePacks()).containsExactly("NL");
          assertThat(snapshot.totalActiveRules()).isEqualTo(7);
        });
  }

  @Test
  void gbLocaleUsesUnitedKingdomRulePackAndPublishesRegionalSnapshot() {
    ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PrismCommonRulePackAutoConfiguration.class,
                    PrismUnitedKingdomRulePackAutoConfiguration.class,
                    SpringPrismAutoConfiguration.class))
            .withPropertyValues(
                "spring.prism.app-secret=integration-secret-value", "spring.prism.locales=GB");

    contextRunner.run(
        context -> {
          @SuppressWarnings("unchecked")
          List<PrismRulePack> rulePacks =
              (List<PrismRulePack>) context.getBean("springPrismRulePacks", List.class);

          assertThat(rulePacks).singleElement().isInstanceOf(UnitedKingdomRulePack.class);
          assertThat(rulePacks).extracting(PrismRulePack::getName).containsExactly("GB");

          PrismMetricsSnapshot snapshot = context.getBean(PrismActuatorEndpoint.class).metrics();
          assertThat(snapshot.activeRulePacks()).containsExactly("GB");
          assertThat(snapshot.totalActiveRules()).isEqualTo(8);
        });
  }

  @Test
  void frLocaleUsesFranceRulePackAndPublishesRegionalSnapshot() {
    ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PrismCommonRulePackAutoConfiguration.class,
                    PrismFranceRulePackAutoConfiguration.class,
                    SpringPrismAutoConfiguration.class))
            .withPropertyValues(
                "spring.prism.app-secret=integration-secret-value", "spring.prism.locales=FR");

    contextRunner.run(
        context -> {
          @SuppressWarnings("unchecked")
          List<PrismRulePack> rulePacks =
              (List<PrismRulePack>) context.getBean("springPrismRulePacks", List.class);

          assertThat(rulePacks).singleElement().isInstanceOf(FranceRulePack.class);
          assertThat(rulePacks).extracting(PrismRulePack::getName).containsExactly("FR");

          PrismMetricsSnapshot snapshot = context.getBean(PrismActuatorEndpoint.class).metrics();
          assertThat(snapshot.activeRulePacks()).containsExactly("FR");
          assertThat(snapshot.totalActiveRules()).isEqualTo(9);
        });
  }

  @Test
  void deLocaleUsesGermanyRulePackAndPublishesRegionalSnapshot() {
    ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    PrismCommonRulePackAutoConfiguration.class,
                    PrismGermanyRulePackAutoConfiguration.class,
                    SpringPrismAutoConfiguration.class))
            .withPropertyValues(
                "spring.prism.app-secret=integration-secret-value", "spring.prism.locales=DE");

    contextRunner.run(
        context -> {
          @SuppressWarnings("unchecked")
          List<PrismRulePack> rulePacks =
              (List<PrismRulePack>) context.getBean("springPrismRulePacks", List.class);

          assertThat(rulePacks).singleElement().isInstanceOf(GermanyRulePack.class);
          assertThat(rulePacks).extracting(PrismRulePack::getName).containsExactly("DE");

          PrismMetricsSnapshot snapshot = context.getBean(PrismActuatorEndpoint.class).metrics();
          assertThat(snapshot.activeRulePacks()).containsExactly("DE");
          assertThat(snapshot.totalActiveRules()).isEqualTo(7);
        });
  }
}
