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

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/** Identifies French SIREN business identifiers using Luhn checksum validation. */
public final class SirenDetector implements PiiDetector {

  private static final Pattern SIREN_PATTERN = Pattern.compile("\\b\\d{9}\\b");

  @Override
  public @NonNull String getEntityType() {
    return "SIREN";
  }

  @Override
  public boolean mayMatch(@NonNull String input) {
    return PiiDetector.containsDigit(input);
  }

  @Override
  public @NonNull List<@NonNull PiiCandidate> detect(@NonNull String input) {
    if (!mayMatch(input)) {
      return List.of();
    }

    List<@NonNull PiiCandidate> matches = new ArrayList<>();
    Matcher matcher = SIREN_PATTERN.matcher(input);
    while (matcher.find()) {
      String candidate = Objects.requireNonNull(matcher.group());
      if (isValidLuhn(candidate)) {
        matches.add(new PiiCandidate(candidate, matcher.start(), matcher.end(), getEntityType()));
      }
    }
    return matches;
  }

  static boolean isValidLuhn(String candidate) {
    int sum = 0;
    boolean doubleDigit = false;
    for (int index = candidate.length() - 1; index >= 0; index--) {
      int digit = Character.getNumericValue(candidate.charAt(index));
      if (doubleDigit) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
      doubleDigit = !doubleDigit;
    }
    return sum % 10 == 0;
  }
}
