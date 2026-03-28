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
import io.github.catalin87.prism.rulepack.common.CommonRulePack;
import io.github.catalin87.prism.rulepack.de.GermanyRulePack;
import io.github.catalin87.prism.rulepack.fr.FranceRulePack;
import io.github.catalin87.prism.rulepack.gb.UnitedKingdomRulePack;
import io.github.catalin87.prism.rulepack.nl.NetherlandsRulePack;
import io.github.catalin87.prism.rulepack.pl.PolandRulePack;
import io.github.catalin87.prism.rulepack.ro.RomaniaRulePack;
import io.github.catalin87.prism.rulepack.us.UsRulePack;
import java.util.List;
import java.util.stream.Stream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/** Benchmarks detector-heavy modular rule pack paths used during outbound scans in v1.1.0. */
@State(Scope.Benchmark)
public class DetectorBenchmark {

  private final List<PiiDetector> commonDetectors = new CommonRulePack().getDetectors();
  private final List<PiiDetector> enterpriseDetectors =
      Stream.of(
              new CommonRulePack().getDetectors(),
              new RomaniaRulePack().getDetectors(),
              new UsRulePack().getDetectors(),
              new PolandRulePack().getDetectors(),
              new NetherlandsRulePack().getDetectors(),
              new UnitedKingdomRulePack().getDetectors(),
              new FranceRulePack().getDetectors(),
              new GermanyRulePack().getDetectors())
          .flatMap(List::stream)
          .toList();
  private final String mixedCommonText =
      "Reach user@example.com, card 4111 1111 1111 1111, phone +40 712 345 678, "
          + "SSN 123-45-6789 and IP 2001:db8::1.";
  private final String mixedEnterpriseText =
      "Customer John Doe uses john.doe@example.com, CNP 1751015412728, card 4012000033330026, "
          + "DE75512108001245126199, FR1420041010050500013M02606, "
          + "GB29NWBK60161331926819, NL91ABNA0417164300, PL61109010140000071219812874, "
          + "RO49AAAA1B31007593840000, EIN 12-3456789, ABA 021000021, "
          + "SIREN 732829320 and Steuer-ID 12345678901.";
  private final String cleanText =
      "A prompt about weather, architecture, and release notes with no sensitive fields.";

  /** Measures modular baseline detection cost on the common rule pack used in v1.1.0. */
  @Benchmark
  public int detectMixedCommonPii() {
    int matches = 0;
    for (PiiDetector detector : commonDetectors) {
      matches += detector.detect(mixedCommonText).size();
    }
    return matches;
  }

  /** Measures the Big 7 enterprise-selected rule pack profile used by the Enterprise Lab. */
  @Benchmark
  public int detectMixedEnterpriseBig7Pii() {
    int matches = 0;
    for (PiiDetector detector : enterpriseDetectors) {
      matches += detector.detect(mixedEnterpriseText).size();
    }
    return matches;
  }

  /** Measures the cheap skip path when the selected detector fleet rejects clean text. */
  @Benchmark
  public int skipCleanTextViaEnterpriseFastPaths() {
    int scans = 0;
    for (PiiDetector detector : enterpriseDetectors) {
      if (detector.mayMatch(cleanText)) {
        scans += detector.detect(cleanText).size();
      }
    }
    return scans;
  }
}
