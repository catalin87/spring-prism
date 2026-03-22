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

class NinoDetectorTest {

  private final PiiDetector detector = new NinoDetector();

  @Test
  void testValidNinoIsDetected() {
    List<PiiCandidate> results = detector.detect("NI Number: AB 12 34 56 A");
    assertEquals(1, results.size());
    assertEquals("NINO", results.get(0).label());
  }

  @Test
  void testCompactNinoIsDetected() {
    List<PiiCandidate> results = detector.detect("NINO: AB123456A");
    assertEquals(1, results.size());
  }

  @Test
  void testInvalidPrefixDIsRejected() {
    assertTrue(detector.detect("DB 12 34 56 A").isEmpty());
  }

  @Test
  void testInvalidPrefixFIsRejected() {
    assertTrue(detector.detect("FB 12 34 56 A").isEmpty());
  }

  @Test
  void testEmptyStringReturnsEmpty() {
    assertTrue(detector.detect("").isEmpty());
  }

  @Test
  void testGetEntityType() {
    assertEquals("NINO", detector.getEntityType());
  }
}
