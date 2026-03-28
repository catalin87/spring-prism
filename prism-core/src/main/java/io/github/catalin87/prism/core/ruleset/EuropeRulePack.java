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
package io.github.catalin87.prism.core.ruleset;

import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.detector.europe.CnpDetector;
import io.github.catalin87.prism.core.detector.europe.EuVatDetector;
import io.github.catalin87.prism.core.detector.europe.IbanDetector;
import io.github.catalin87.prism.core.detector.europe.NinoDetector;
import io.github.catalin87.prism.core.detector.europe.PeselDetector;
import io.github.catalin87.prism.core.detector.universal.CreditCardDetector;
import io.github.catalin87.prism.core.detector.universal.EmailDetector;
import io.github.catalin87.prism.core.detector.universal.IpAddressDetector;
import io.github.catalin87.prism.core.detector.universal.PhoneNumberDetector;
import io.github.catalin87.prism.core.detector.universal.SsnDetector;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NonNull;

/**
 * EU-First Rule Pack extending Universal coverage with GDPR-critical European PII: IBAN, EU VAT,
 * Polish PESEL, Romanian CNP, and UK National Insurance Numbers (NINO).
 */
public class EuropeRulePack implements PrismRulePack {

  private static final List<@NonNull PiiDetector> DETECTORS =
      List.of(
          new EmailDetector(),
          new CreditCardDetector(),
          new SsnDetector(),
          new PhoneNumberDetector(),
          new IpAddressDetector(),
          new IbanDetector(),
          new EuVatDetector(),
          new PeselDetector(),
          new CnpDetector(),
          new NinoDetector());

  @Override
  public @NonNull String getName() {
    return "EUROPE";
  }

  @Override
  public @NonNull List<@NonNull PiiDetector> getDetectors() {
    return DETECTORS;
  }

  @Override
  public @NonNull Set<@NonNull String> getActivationAliases() {
    return Set.of("EU", "EUROPE", "DE", "PL", "RO", "UK", "GB");
  }

  @Override
  public boolean isAutoDiscoverable() {
    return true;
  }
}
