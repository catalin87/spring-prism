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
package io.github.catalin87.prism.core.detector.europe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import java.util.List;
import org.junit.jupiter.api.Test;

class EuVatDetectorTest {

  private final PiiDetector detector = new EuVatDetector();

  @Test
  void testGermanVatIsDetected() {
    List<PiiCandidate> results = detector.detect("VAT: DE123456789");
    assertEquals(1, results.size());
    assertEquals("EU_VAT", results.get(0).label());
  }

  @Test
  void testRomanianVatIsDetected() {
    List<PiiCandidate> results = detector.detect("CIF: RO12345678");
    assertFalse(results.isEmpty());
  }

  @Test
  void testFrenchVatIsDetected() {
    List<PiiCandidate> results = detector.detect("TVA: FR12345678901");
    assertFalse(results.isEmpty());
  }

  @Test
  void testNonVatStringIsIgnored() {
    assertTrue(detector.detect("Order ID: 12345").isEmpty());
  }

  @Test
  void testEmptyStringReturnsEmpty() {
    assertTrue(detector.detect("").isEmpty());
  }

  @Test
  void testGetEntityType() {
    assertEquals("EU_VAT", detector.getEntityType());
  }
}
