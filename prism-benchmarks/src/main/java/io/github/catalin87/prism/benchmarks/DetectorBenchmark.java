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
package io.github.catalin87.prism.benchmarks;

import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import java.util.List;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/** Benchmarks the detector-heavy universal rule pack paths used during outbound scans. */
@State(Scope.Benchmark)
public class DetectorBenchmark {

  private final List<PiiDetector> detectors = new UniversalRulePack().getDetectors();
  private final String mixedPiiText =
      "Reach user@example.com, card 4111 1111 1111 1111, phone +40 712 345 678, "
          + "SSN 123-45-6789 and IP 2001:db8::1.";
  private final String cleanText =
      "A prompt about weather, architecture, and release notes with no sensitive fields.";

  /** Measures full universal-pack detection cost on text containing several PII categories. */
  @Benchmark
  public int detectMixedUniversalPii() {
    int matches = 0;
    for (PiiDetector detector : detectors) {
      matches += detector.detect(mixedPiiText).size();
    }
    return matches;
  }

  /** Measures the cheap skip path when detector heuristics reject obviously clean text. */
  @Benchmark
  public int skipCleanTextViaFastPaths() {
    int scans = 0;
    for (PiiDetector detector : detectors) {
      if (detector.mayMatch(cleanText)) {
        scans += detector.detect(cleanText).size();
      }
    }
    return scans;
  }
}
