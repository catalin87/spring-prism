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
package io.github.catalin87.prism.rulepack.ro;

import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.detector.europe.CnpDetector;
import io.github.catalin87.prism.core.detector.europe.IbanDetector;
import io.github.catalin87.prism.core.detector.universal.CreditCardDetector;
import io.github.catalin87.prism.core.detector.universal.EmailDetector;
import io.github.catalin87.prism.core.detector.universal.IpAddressDetector;
import io.github.catalin87.prism.core.detector.universal.PhoneNumberDetector;
import io.github.catalin87.prism.core.detector.universal.SsnDetector;
import io.github.catalin87.prism.rulepack.ro.detector.CifDetector;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NonNull;

/**
 * Romania-focused rule pack that preserves the universal baseline and adds Romanian identifiers.
 */
public final class RomaniaRulePack implements PrismRulePack {

  private static final List<@NonNull PiiDetector> DETECTORS =
      List.of(
          new EmailDetector(),
          new CreditCardDetector(),
          new SsnDetector(),
          new PhoneNumberDetector(),
          new IpAddressDetector(),
          new IbanDetector(),
          new CnpDetector(),
          new CifDetector());

  @Override
  public @NonNull String getName() {
    return "RO";
  }

  @Override
  public @NonNull List<@NonNull PiiDetector> getDetectors() {
    return DETECTORS;
  }

  @Override
  public @NonNull Set<@NonNull String> getActivationAliases() {
    return Set.of("RO", "ROU", "ROMANIA");
  }

  @Override
  public boolean isAutoDiscoverable() {
    return true;
  }
}
