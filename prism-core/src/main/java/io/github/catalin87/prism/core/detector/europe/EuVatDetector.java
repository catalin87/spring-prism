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
package io.github.catalin87.prism.core.detector.europe;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/**
 * Identifies EU VAT Registration Numbers. Each EU member state has a distinct country-specific
 * format; all 27 member state patterns are covered by this composite detector.
 */
public class EuVatDetector implements PiiDetector {

  // Composite pattern covering all 27 EU member state VAT formats per EC VIES specification.
  private static final Pattern VAT_PATTERN =
      Pattern.compile(
          "\\b("
              + "ATU[0-9]{8}" // Austria
              + "|BE0[0-9]{9}" // Belgium
              + "|BG[0-9]{9,10}" // Bulgaria
              + "|CY[0-9]{8}[A-Z]" // Cyprus
              + "|CZ[0-9]{8,10}" // Czech Republic
              + "|DE[0-9]{9}" // Germany
              + "|DK[0-9]{8}" // Denmark
              + "|EE[0-9]{9}" // Estonia
              + "|EL[0-9]{9}" // Greece
              + "|ES[A-Z0-9][0-9]{7}[A-Z0-9]" // Spain
              + "|FI[0-9]{8}" // Finland
              + "|FR[A-Z0-9]{2}[0-9]{9}" // France
              + "|HR[0-9]{11}" // Croatia
              + "|HU[0-9]{8}" // Hungary
              + "|IE([0-9]{7}[A-Z]{1,2}|[0-9][A-Z][0-9]{5}[A-Z])" // Ireland
              + "|IT[0-9]{11}" // Italy
              + "|LT([0-9]{9}|[0-9]{12})" // Lithuania
              + "|LU[0-9]{8}" // Luxembourg
              + "|LV[0-9]{11}" // Latvia
              + "|MT[0-9]{8}" // Malta
              + "|NL[0-9]{9}B[0-9]{2}" // Netherlands
              + "|PL[0-9]{10}" // Poland
              + "|PT[0-9]{9}" // Portugal
              + "|RO[0-9]{2,10}" // Romania
              + "|SE[0-9]{12}" // Sweden
              + "|SI[0-9]{8}" // Slovenia
              + "|SK[0-9]{10}" // Slovakia
              + ")\\b",
          Pattern.CASE_INSENSITIVE);

  @Override
  public @NonNull String getEntityType() {
    return "EU_VAT";
  }

  @Override
  public boolean mayMatch(@NonNull String input) {
    return PiiDetector.containsDigit(input);
  }

  @Override
  public @NonNull List<PiiCandidate> detect(@NonNull String input) {
    if (!mayMatch(input)) {
      return List.of();
    }

    List<PiiCandidate> matches = new ArrayList<>();
    Matcher matcher = VAT_PATTERN.matcher(input.toUpperCase());

    while (matcher.find()) {
      // Map back to original input positions (toUpperCase is length-preserving for ASCII)
      String original = input.substring(matcher.start(), matcher.end());
      matches.add(new PiiCandidate(original, matcher.start(), matcher.end(), getEntityType()));
    }

    return matches;
  }
}
