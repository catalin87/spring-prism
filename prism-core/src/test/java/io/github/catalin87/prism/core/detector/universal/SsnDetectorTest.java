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
package io.github.catalin87.prism.core.detector.universal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import java.util.List;
import org.junit.jupiter.api.Test;

class SsnDetectorTest {

  private final PiiDetector detector = new SsnDetector();

  @Test
  void testDashedSsnIsDetected() {
    List<PiiCandidate> results = detector.detect("SSN: 123-45-6789 on file.");
    assertEquals(1, results.size());
    assertEquals("123-45-6789", results.get(0).text());
    assertEquals("SSN", results.get(0).label());
  }

  @Test
  void testCompactSsnIsDetected() {
    List<PiiCandidate> results = detector.detect("ID: 123456789");
    assertEquals(1, results.size());
  }

  @Test
  void testInvalidAreaCode000IsRejected() {
    assertTrue(detector.detect("000-45-6789").isEmpty());
  }

  @Test
  void testInvalidAreaCode666IsRejected() {
    assertTrue(detector.detect("666-45-6789").isEmpty());
  }

  @Test
  void testInvalidAreaCode900IsRejected() {
    assertTrue(detector.detect("900-45-6789").isEmpty());
  }

  @Test
  void testEmptyStringReturnsEmpty() {
    assertTrue(detector.detect("").isEmpty());
  }

  @Test
  void testGetEntityType() {
    assertEquals("SSN", detector.getEntityType());
  }
}
