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

class PhoneNumberDetectorTest {

  private final PiiDetector detector = new PhoneNumberDetector();

  @Test
  void detectsInternationalPhoneNumber() {
    List<PiiCandidate> results = detector.detect("Call +40 712 345 678 after lunch.");

    assertEquals(1, results.size());
    assertEquals("+40 712 345 678", results.get(0).text());
    assertEquals("PHONE_NUMBER", results.get(0).label());
  }

  @Test
  void detectsNorthAmericanPhoneNumber() {
    List<PiiCandidate> results = detector.detect("Office: 202-555-0187");

    assertEquals(1, results.size());
    assertEquals("202-555-0187", results.get(0).text());
  }

  @Test
  void detectsPhoneNumberWithParentheses() {
    List<PiiCandidate> results = detector.detect("Emergency line is (202) 555-0187.");

    assertEquals(1, results.size());
    assertEquals("(202) 555-0187", results.get(0).text());
  }

  @Test
  void rejectsIpv4Address() {
    assertTrue(detector.detect("Server 192.168.100.200 is healthy").isEmpty());
  }

  @Test
  void rejectsTooShortNumber() {
    assertTrue(detector.detect("Call 555-1234 later").isEmpty());
  }

  @Test
  void rejectsRepeatedDigits() {
    assertTrue(detector.detect("Avoid 111-111-1111").isEmpty());
  }

  @Test
  void returnsEmptyOnBlankInput() {
    assertTrue(detector.detect("").isEmpty());
  }
}
