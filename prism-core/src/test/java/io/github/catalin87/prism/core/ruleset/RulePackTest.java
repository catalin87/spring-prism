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
package io.github.catalin87.prism.core.ruleset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RulePackTest {

  @Test
  void testUniversalRulePackContainsFourDetectors() {
    UniversalRulePack pack = new UniversalRulePack();
    assertEquals("UNIVERSAL", pack.getName());
    assertEquals(4, pack.getDetectors().size());
  }

  @Test
  void testEuropeRulePackContainsNineDetectors() {
    EuropeRulePack pack = new EuropeRulePack();
    assertEquals("EUROPE", pack.getName());
    assertEquals(9, pack.getDetectors().size());
  }

  @Test
  void testUniversalPackDetectsEmailEndToEnd() {
    UniversalRulePack pack = new UniversalRulePack();
    boolean anyMatch =
        pack.getDetectors().stream()
            .flatMap(d -> d.detect("user@test.com").stream())
            .anyMatch(c -> c.label().equals("EMAIL"));
    assertTrue(anyMatch);
  }

  @Test
  void testEuropePackDetectsIbanEndToEnd() {
    EuropeRulePack pack = new EuropeRulePack();
    boolean anyMatch =
        pack.getDetectors().stream()
            .flatMap(d -> d.detect("DE89370400440532013000").stream())
            .anyMatch(c -> c.label().equals("IBAN"));
    assertTrue(anyMatch);
  }
}
