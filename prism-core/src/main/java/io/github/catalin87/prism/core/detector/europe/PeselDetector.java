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
 * Identifies Polish National Identification Numbers (PESEL). Validates the 11-digit sequence using
 * the official weighted checksum algorithm to eliminate false positives.
 */
public class PeselDetector implements PiiDetector {

  private static final Pattern PESEL_PATTERN = Pattern.compile("\\b\\d{11}\\b");

  // Official PESEL weights per GUS specification
  private static final int[] WEIGHTS = {1, 3, 7, 9, 1, 3, 7, 9, 1, 3};

  @Override
  public @NonNull String getEntityType() {
    return "PESEL";
  }

  @Override
  public @NonNull List<PiiCandidate> detect(@NonNull String input) {
    if (input.isEmpty()) {
      return List.of();
    }

    List<PiiCandidate> matches = new ArrayList<>();
    Matcher matcher = PESEL_PATTERN.matcher(input);

    while (matcher.find()) {
      String candidate = matcher.group();
      if (isValidPesel(candidate)) {
        matches.add(new PiiCandidate(candidate, matcher.start(), matcher.end(), getEntityType()));
      }
    }

    return matches;
  }

  /** Validates the PESEL checksum using official GUS weighting. */
  private boolean isValidPesel(String pesel) {
    int sum = 0;
    for (int i = 0; i < WEIGHTS.length; i++) {
      sum += WEIGHTS[i] * Character.getNumericValue(pesel.charAt(i));
    }
    int checkDigit = (10 - (sum % 10)) % 10;
    return checkDigit == Character.getNumericValue(pesel.charAt(10));
  }
}
