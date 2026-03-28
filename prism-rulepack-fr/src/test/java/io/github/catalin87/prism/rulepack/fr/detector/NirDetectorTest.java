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
package io.github.catalin87.prism.rulepack.fr.detector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NirDetectorTest {

  private final NirDetector detector = new NirDetector();

  @Test
  void detectsValidNir() {
    assertThat(detector.detect("Patient NIR 184127645108946"))
        .extracting(candidate -> candidate.text())
        .containsExactly("184127645108946");
  }

  @Test
  void rejectsInvalidChecksum() {
    assertThat(detector.detect("Patient NIR 184127645108947")).isEmpty();
  }
}
