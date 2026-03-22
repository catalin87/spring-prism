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

class CnpDetectorTest {

  private final PiiDetector detector = new CnpDetector();

  @Test
  void testValidCnpIsDetected() {
    // 1850626370016 is a known-valid Romanian CNP
    List<PiiCandidate> results = detector.detect("CNP: 1850626370016");
    assertEquals(1, results.size());
    assertEquals("1850626370016", results.get(0).text());
    assertEquals("CNP", results.get(0).label());
  }

  @Test
  void testInvalidChecksumIsRejected() {
    // Correct format but wrong check digit
    assertTrue(detector.detect("1850626370019").isEmpty());
  }

  @Test
  void testStartingWithZeroIsRejected() {
    // CNP must start with 1-9
    assertTrue(detector.detect("0850626370016").isEmpty());
  }

  @Test
  void testEmptyStringReturnsEmpty() {
    assertTrue(detector.detect("").isEmpty());
  }

  @Test
  void testGetEntityType() {
    assertEquals("CNP", detector.getEntityType());
  }
}
