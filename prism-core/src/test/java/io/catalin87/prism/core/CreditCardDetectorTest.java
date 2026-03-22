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
package io.catalin87.prism.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CreditCardDetectorTest {

  private final PiiDetector detector = new CreditCardDetector();

  @Test
  void testValidCreditCardIsDetected() {
    // Valid standard Visa Test sequence strictly conforming to Luhn
    String text = "My card is 4111 1111 1111 1111 right here.";
    List<PiiCandidate> matches = detector.detect(text);
    assertEquals(1, matches.size());

    PiiCandidate match = matches.get(0);
    assertEquals("4111 1111 1111 1111", match.text());
    assertEquals("CREDIT_CARD", match.label());
  }

  @Test
  void testInvalidLuhnSequenceIsIgnored() {
    // Invalid sequence (Last digit manipulated causing a checksum violation)
    String text = "My card is 4111 1111 1111 1112 right here.";
    List<PiiCandidate> matches = detector.detect(text);
    assertTrue(matches.isEmpty());
  }

  @Test
  void testEmptyStringReturnsEmptyList() {
    assertTrue(detector.detect("").isEmpty());
  }

  @Test
  void testGetEntityTypeReturnsCreditCard() {
    assertEquals("CREDIT_CARD", detector.getEntityType());
  }
}
