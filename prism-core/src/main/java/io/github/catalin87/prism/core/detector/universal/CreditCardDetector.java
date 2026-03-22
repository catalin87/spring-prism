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
package io.github.catalin87.prism.core.detector.universal;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/**
 * Mathematically isolates Credit Card patterns universally and strictly evaluates sequence
 * viability using the topological Luhn Checksum algorithm prior to releasing a valid PiiCandidate.
 */
public class CreditCardDetector implements PiiDetector {

  private static final Pattern CC_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b");

  @Override
  public @NonNull String getEntityType() {
    return "CREDIT_CARD";
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
    Matcher matcher = CC_PATTERN.matcher(input);

    while (matcher.find()) {
      String rawMatch = matcher.group();
      // Sequentially strip visual boundaries (spaces and dashes) to isolate native cardinality
      String cleanedNumbers = rawMatch.replaceAll("[ -]", "");

      if (isValidLuhn(cleanedNumbers)) {
        matches.add(new PiiCandidate(rawMatch, matcher.start(), matcher.end(), "CREDIT_CARD"));
      }
    }

    return matches;
  }

  /** Universal topological Luhn mathematical algorithm. */
  private boolean isValidLuhn(String digitSequence) {
    int sum = 0;
    boolean alternate = false;

    // Execute backward sequence checksum mathematically
    for (int i = digitSequence.length() - 1; i >= 0; i--) {
      int n = Character.getNumericValue(digitSequence.charAt(i));
      if (alternate) {
        n *= 2;
        if (n > 9) {
          n = (n % 10) + 1;
        }
      }
      sum += n;
      alternate = !alternate;
    }
    return (sum % 10 == 0);
  }
}
