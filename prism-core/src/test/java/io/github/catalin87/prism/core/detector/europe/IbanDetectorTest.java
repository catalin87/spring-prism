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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import java.util.List;
import org.junit.jupiter.api.Test;

class IbanDetectorTest {

  private final PiiDetector detector = new IbanDetector();

  @Test
  void testValidGermanIbanIsDetected() {
    List<PiiCandidate> results = detector.detect("Wire to DE89370400440532013000 please.");
    assertEquals(1, results.size());
    assertEquals("DE89370400440532013000", results.get(0).text());
    assertEquals("IBAN", results.get(0).label());
  }

  @Test
  void testSpacedIbanIsDetected() {
    List<PiiCandidate> results = detector.detect("Account: DE89 3704 0044 0532 0130 00");
    assertEquals(1, results.size());
  }

  @Test
  void testInvalidChecksumIsRejected() {
    // Valid format but wrong Mod-97 checksum
    assertTrue(detector.detect("DE00370400440532013000").isEmpty());
  }

  @Test
  void testEmptyStringReturnsEmpty() {
    assertTrue(detector.detect("").isEmpty());
  }

  @Test
  void testGetEntityType() {
    assertEquals("IBAN", detector.getEntityType());
  }
}
