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
package io.github.catalin87.prism.rulepack.gb.detector;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/** Identifies NHS numbers using the official 10-digit checksum validation. */
public final class NhsDetector implements PiiDetector {

  private static final Pattern NHS_PATTERN = Pattern.compile("\\b\\d{3}[ -]?\\d{3}[ -]?\\d{4}\\b");

  @Override
  public @NonNull String getEntityType() {
    return "NHS";
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
    Matcher matcher = NHS_PATTERN.matcher(input);
    while (matcher.find()) {
      String candidate = Objects.requireNonNull(matcher.group());
      if (isValidNhs(candidate)) {
        matches.add(new PiiCandidate(candidate, matcher.start(), matcher.end(), getEntityType()));
      }
    }
    return matches;
  }

  private boolean isValidNhs(String candidate) {
    String digits = candidate.replaceAll("[ -]", "");
    if (digits.length() != 10) {
      return false;
    }

    int sum = 0;
    for (int index = 0; index < 9; index++) {
      sum += Character.getNumericValue(digits.charAt(index)) * (10 - index);
    }
    int remainder = 11 - (sum % 11);
    if (remainder == 11) {
      remainder = 0;
    }
    if (remainder == 10) {
      return false;
    }
    int controlDigit = Character.getNumericValue(digits.charAt(9));
    return remainder == controlDigit;
  }
}
