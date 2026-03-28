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
package io.github.catalin87.prism.rulepack.us.detector;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/** Identifies US ABA routing numbers using the official 3-7-1 checksum validation. */
public final class AbaRoutingDetector implements PiiDetector {

  private static final Pattern ABA_PATTERN = Pattern.compile("\\b\\d{9}\\b");

  @Override
  public @NonNull String getEntityType() {
    return "ABA_ROUTING";
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
    Matcher matcher = ABA_PATTERN.matcher(input);
    while (matcher.find()) {
      String candidate = Objects.requireNonNull(matcher.group());
      if (isValidRoutingNumber(candidate)) {
        matches.add(new PiiCandidate(candidate, matcher.start(), matcher.end(), getEntityType()));
      }
    }
    return matches;
  }

  private boolean isValidRoutingNumber(String candidate) {
    int sum =
        (3 * digit(candidate, 0))
            + (7 * digit(candidate, 1))
            + (1 * digit(candidate, 2))
            + (3 * digit(candidate, 3))
            + (7 * digit(candidate, 4))
            + (1 * digit(candidate, 5))
            + (3 * digit(candidate, 6))
            + (7 * digit(candidate, 7))
            + (1 * digit(candidate, 8));
    return sum % 10 == 0;
  }

  private int digit(String candidate, int index) {
    return Character.getNumericValue(candidate.charAt(index));
  }
}
