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

class EmailDetectorTest {

  private final PiiDetector detector = new EmailDetector();

  @Test
  void testSimpleEmailIsDetected() {
    List<PiiCandidate> results = detector.detect("Contact us at john.doe@example.com today.");
    assertEquals(1, results.size());
    assertEquals("john.doe@example.com", results.get(0).text());
    assertEquals("EMAIL", results.get(0).label());
  }

  @Test
  void testMultipleEmailsAreDetected() {
    List<PiiCandidate> results = detector.detect("From: a@b.com, reply to c@d.org");
    assertEquals(2, results.size());
  }

  @Test
  void testSubdomainEmailIsDetected() {
    List<PiiCandidate> results = detector.detect("Send to user@mail.company.co.uk");
    assertEquals(1, results.size());
  }

  @Test
  void testInvalidEmailIsNotDetected() {
    assertTrue(detector.detect("not-an-email").isEmpty());
    assertTrue(detector.detect("missing@tld").isEmpty());
  }

  @Test
  void testEmptyStringReturnsEmpty() {
    assertTrue(detector.detect("").isEmpty());
  }

  @Test
  void testGetEntityType() {
    assertEquals("EMAIL", detector.getEntityType());
  }
}
