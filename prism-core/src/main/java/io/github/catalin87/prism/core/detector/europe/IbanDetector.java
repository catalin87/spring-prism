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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/**
 * Identifies International Bank Account Numbers (IBAN). Validates sequences mathematically using
 * the ISO 13616 Modulo-97 algorithm to eliminate false positives before emitting a candidate.
 */
public class IbanDetector implements PiiDetector {

  // Two-form matcher: compact (DE89...) or space-grouped (DE89 3704 ...).
  // The compact form matches 11-30 alphanumeric chars directly.
  // The spaced form requires groups of exactly 4 separated by a single space.
  private static final Pattern IBAN_COMPACT =
      Pattern.compile("\\b[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}\\b", Pattern.CASE_INSENSITIVE);

  private static final Pattern IBAN_SPACED =
      Pattern.compile(
          "\\b[A-Z]{2}[0-9]{2}(?: [A-Z0-9]{4}){2,7}(?: [A-Z0-9]{1,4})?\\b",
          Pattern.CASE_INSENSITIVE);

  @Override
  public @NonNull String getEntityType() {
    return "IBAN";
  }

  @Override
  public @NonNull List<PiiCandidate> detect(@NonNull String input) {
    if (input.isEmpty()) {
      return List.of();
    }

    List<PiiCandidate> matches = new ArrayList<>();
    scanWith(IBAN_COMPACT.matcher(input), matches);
    scanWith(IBAN_SPACED.matcher(input), matches);

    return matches;
  }

  private void scanWith(Matcher matcher, List<PiiCandidate> matches) {
    while (matcher.find()) {
      String rawMatch = matcher.group();
      String cleanIban = rawMatch.replaceAll("\\s", "").toUpperCase();
      if (isValidIban(cleanIban)) {
        matches.add(new PiiCandidate(rawMatch, matcher.start(), matcher.end(), getEntityType()));
      }
    }
  }

  /** Validates via ISO 13616 Modulo-97 arithmetic. */
  private boolean isValidIban(String iban) {
    if (iban.length() < 15 || iban.length() > 34) {
      return false;
    }
    // Move first 4 chars to the end, then transliterate letters to digits (A=10..Z=35).
    String rearranged = iban.substring(4) + iban.substring(0, 4);
    StringBuilder numeric = new StringBuilder();
    for (char c : rearranged.toCharArray()) {
      if (Character.isDigit(c)) {
        numeric.append(c);
      } else if (Character.isLetter(c)) {
        numeric.append(Character.getNumericValue(c));
      } else {
        return false;
      }
    }
    try {
      return new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
