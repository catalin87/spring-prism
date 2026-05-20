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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import io.github.catalin87.prism.boot.autoconfigure.SpringPrismProperties.ActiveDefense;
import org.junit.jupiter.api.Test;

class SpringPrismActiveDefensePropertiesTest {

  @Test
  void defaultsApplyWhenNoConfigurationProvided() {
    SpringPrismProperties properties = new SpringPrismProperties();

    ActiveDefense defense = properties.getActiveDefense();

    assertNotNull(defense);
    assertEquals(
        ActiveDefense.DEFAULT_MAX_RESTORED_TOKENS, defense.getMaxRestoredTokensPerResponse());
  }

  @Test
  void unlimitedSentinelIsPreservedVerbatim() {
    ActiveDefense defense = new ActiveDefense();

    defense.setMaxRestoredTokensPerResponse(ActiveDefense.UNLIMITED);

    assertEquals(ActiveDefense.UNLIMITED, defense.getMaxRestoredTokensPerResponse());
  }

  @Test
  void positiveValuePassesThrough() {
    ActiveDefense defense = new ActiveDefense();

    defense.setMaxRestoredTokensPerResponse(250);

    assertEquals(250, defense.getMaxRestoredTokensPerResponse());
  }

  @Test
  void zeroCoercedToDefaultToAvoidAccidentalLockout() {
    ActiveDefense defense = new ActiveDefense();
    defense.setMaxRestoredTokensPerResponse(500);

    defense.setMaxRestoredTokensPerResponse(0);

    assertEquals(
        ActiveDefense.DEFAULT_MAX_RESTORED_TOKENS, defense.getMaxRestoredTokensPerResponse());
  }

  @Test
  void invalidNegativeValueCoercedToDefault() {
    ActiveDefense defense = new ActiveDefense();
    defense.setMaxRestoredTokensPerResponse(500);

    defense.setMaxRestoredTokensPerResponse(-42);

    assertEquals(
        ActiveDefense.DEFAULT_MAX_RESTORED_TOKENS, defense.getMaxRestoredTokensPerResponse());
  }

  @Test
  void setterReplacesActiveDefenseInstance() {
    SpringPrismProperties properties = new SpringPrismProperties();
    ActiveDefense original = properties.getActiveDefense();
    ActiveDefense replacement = new ActiveDefense();
    replacement.setMaxRestoredTokensPerResponse(7);

    properties.setActiveDefense(replacement);

    assertNotSame(original, properties.getActiveDefense());
    assertEquals(7, properties.getActiveDefense().getMaxRestoredTokensPerResponse());
  }

  @Test
  void nullSetterFallsBackToFreshActiveDefense() {
    SpringPrismProperties properties = new SpringPrismProperties();

    properties.setActiveDefense(null);

    assertNotNull(properties.getActiveDefense());
    assertEquals(
        ActiveDefense.DEFAULT_MAX_RESTORED_TOKENS,
        properties.getActiveDefense().getMaxRestoredTokensPerResponse());
  }
}
