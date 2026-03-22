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
package io.github.catalin87.prism.core;

import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * EU-First Rule Pack extending Universal coverage with GDPR-critical European PII: IBAN, EU VAT,
 * Polish PESEL, Romanian CNP, and UK National Insurance Numbers (NINO).
 */
public class EuropeRulePack implements PrismRulePack {

  private static final List<PiiDetector> DETECTORS =
      List.of(
          new EmailDetector(),
          new CreditCardDetector(),
          new SsnDetector(),
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
  public @NonNull List<PiiDetector> getDetectors() {
    return DETECTORS;
  }
}
