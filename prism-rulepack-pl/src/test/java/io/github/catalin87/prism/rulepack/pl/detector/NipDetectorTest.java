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
package io.github.catalin87.prism.rulepack.pl.detector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NipDetectorTest {

  private final NipDetector detector = new NipDetector();

  @Test
  void detectsValidNip() {
    assertThat(detector.detect("Supplier NIP 5261040828"))
        .extracting(candidate -> candidate.text())
        .containsExactly("5261040828");
  }

  @Test
  void rejectsInvalidChecksum() {
    assertThat(detector.detect("Supplier NIP 5261040827")).isEmpty();
  }
}
