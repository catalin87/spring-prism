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
 * Identifies Romanian Personal Numeric Codes (CNP - Cod Numeric Personal). Validates the 13-digit
 * sequence using the official ANP weighted checksum algorithm.
 */
public class CnpDetector implements PiiDetector {

  private static final Pattern CNP_PATTERN = Pattern.compile("\\b[1-9]\\d{12}\\b");

  // Official CNP weights per ANP Romania specification
  private static final int[] WEIGHTS = {2, 7, 9, 1, 4, 6, 3, 5, 8, 2, 7, 9};

  @Override
  public @NonNull String getEntityType() {
    return "CNP";
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
    Matcher matcher = CNP_PATTERN.matcher(input);

    while (matcher.find()) {
      String candidate = matcher.group();
      if (isValidCnp(candidate)) {
        matches.add(new PiiCandidate(candidate, matcher.start(), matcher.end(), getEntityType()));
      }
    }

    return matches;
  }

  /** Validates via the official ANP Romania weighted modulo-11 checksum. */
  private boolean isValidCnp(String cnp) {
    int sum = 0;
    for (int i = 0; i < WEIGHTS.length; i++) {
      sum += WEIGHTS[i] * Character.getNumericValue(cnp.charAt(i));
    }
    int remainder = sum % 11;
    int checkDigit = (remainder == 10) ? 1 : remainder;
    return checkDigit == Character.getNumericValue(cnp.charAt(12));
  }
}
