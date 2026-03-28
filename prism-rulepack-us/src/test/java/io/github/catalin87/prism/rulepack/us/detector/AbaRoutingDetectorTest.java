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
package io.github.catalin87.prism.rulepack.us.detector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AbaRoutingDetectorTest {

  private final AbaRoutingDetector detector = new AbaRoutingDetector();

  @Test
  void detectsValidRoutingNumber() {
    assertThat(detector.detect("Use routing 021000021 for the transfer"))
        .extracting(candidate -> candidate.text())
        .containsExactly("021000021");
  }

  @Test
  void rejectsInvalidChecksum() {
    assertThat(detector.detect("Use routing 021000022 for the transfer")).isEmpty();
  }
}
