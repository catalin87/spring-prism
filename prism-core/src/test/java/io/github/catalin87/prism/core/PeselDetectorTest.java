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
package io.github.catalin87.prism.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PeselDetectorTest {

  private final PiiDetector detector = new PeselDetector();

  @Test
  void testValidPeselIsDetected() {
    // 44051401458 is a known-valid PESEL (passes official checksum)
    List<PiiCandidate> results = detector.detect("PESEL: 44051401458");
    assertEquals(1, results.size());
    assertEquals("44051401458", results.get(0).text());
    assertEquals("PESEL", results.get(0).label());
  }

  @Test
  void testInvalidChecksumIsRejected() {
    // Correct length but bad check digit
    assertTrue(detector.detect("44051401459").isEmpty());
  }

  @Test
  void testEmptyStringReturnsEmpty() {
    assertTrue(detector.detect("").isEmpty());
  }

  @Test
  void testGetEntityType() {
    assertEquals("PESEL", detector.getEntityType());
  }
}
