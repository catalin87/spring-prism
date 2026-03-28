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
package io.github.catalin87.prism.rulepack.pl.detector;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/** Identifies Polish NIP numbers using the official weighted checksum validation. */
public final class NipDetector implements PiiDetector {

  private static final Pattern NIP_PATTERN =
      Pattern.compile("\\b\\d{3}[- ]?\\d{3}[- ]?\\d{2}[- ]?\\d{2}\\b");
  private static final int[] WEIGHTS = {6, 5, 7, 2, 3, 4, 5, 6, 7};

  @Override
  public @NonNull String getEntityType() {
    return "NIP";
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
    Matcher matcher = NIP_PATTERN.matcher(input);
    while (matcher.find()) {
      String candidate = Objects.requireNonNull(matcher.group());
      if (isValidNip(candidate)) {
        matches.add(new PiiCandidate(candidate, matcher.start(), matcher.end(), getEntityType()));
      }
    }
    return matches;
  }

  private boolean isValidNip(String candidate) {
    String digits = candidate.replaceAll("[- ]", "");
    if (digits.length() != 10) {
      return false;
    }

    int sum = 0;
    for (int index = 0; index < WEIGHTS.length; index++) {
      sum += Character.getNumericValue(digits.charAt(index)) * WEIGHTS[index];
    }

    int checksum = sum % 11;
    if (checksum == 10) {
      return false;
    }
    int controlDigit = Character.getNumericValue(digits.charAt(9));
    return checksum == controlDigit;
  }
}
