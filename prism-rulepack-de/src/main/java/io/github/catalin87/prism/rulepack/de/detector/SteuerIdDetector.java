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
package io.github.catalin87.prism.rulepack.de.detector;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/** Identifies German Steuer-ID values using the official ISO 7064 Mod 11,10 checksum. */
public final class SteuerIdDetector implements PiiDetector {

  private static final Pattern STEUER_ID_PATTERN = Pattern.compile("\\b\\d{11}\\b");

  @Override
  public @NonNull String getEntityType() {
    return "STEUER_ID";
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
    Matcher matcher = STEUER_ID_PATTERN.matcher(input);
    while (matcher.find()) {
      String candidate = Objects.requireNonNull(matcher.group());
      if (isValidSteuerId(candidate)) {
        matches.add(new PiiCandidate(candidate, matcher.start(), matcher.end(), getEntityType()));
      }
    }
    return matches;
  }

  private boolean isValidSteuerId(String candidate) {
    int product = 10;
    for (int index = 0; index < 10; index++) {
      int sum = (Character.getNumericValue(candidate.charAt(index)) + product) % 10;
      if (sum == 0) {
        sum = 10;
      }
      product = (sum * 2) % 11;
    }
    int checkDigit = 11 - product;
    if (checkDigit == 10) {
      checkDigit = 0;
    }
    return checkDigit == Character.getNumericValue(candidate.charAt(10));
  }
}
