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

class IpAddressDetectorTest {

  private final PiiDetector detector = new IpAddressDetector();

  @Test
  void testValidIpv4IsDetected() {
    List<PiiCandidate> results = detector.detect("Server at 192.168.1.1 is down.");
    assertEquals(1, results.size());
    assertEquals("192.168.1.1", results.get(0).text());
    assertEquals("IP_ADDRESS", results.get(0).label());
  }

  @Test
  void testInvalidIpv4OctetIsRejected() {
    assertTrue(detector.detect("Bad IP: 999.168.1.1").isEmpty());
    assertTrue(detector.detect("Bad IP: 256.0.0.1").isEmpty());
  }

  @Test
  void testValidIpv6IsDetected() {
    List<PiiCandidate> results = detector.detect("IPv6: 2001:0db8:85a3:0000:0000:8a2e:0370:7334");
    assertEquals(1, results.size());
    assertEquals("IP_ADDRESS", results.get(0).label());
  }

  @Test
  void testLocalhostIpv6IsDetected() {
    assertEquals(1, detector.detect("Loopback: ::1").size());
  }

  @Test
  void testEmptyStringReturnsEmpty() {
    assertTrue(detector.detect("").isEmpty());
  }

  @Test
  void testGetEntityType() {
    assertEquals("IP_ADDRESS", detector.getEntityType());
  }
}
