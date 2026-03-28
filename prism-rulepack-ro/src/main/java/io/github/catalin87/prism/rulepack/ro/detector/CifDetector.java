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
package io.github.catalin87.prism.rulepack.ro.detector;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/**
 * Identifies Romanian CUI/CIF values.
 *
 * <p>Validation follows the weighted checksum used for Romanian fiscal identifiers and supports
 * both plain numeric forms and the optional {@code RO} prefix commonly used in VAT contexts.
 */
public final class CifDetector implements PiiDetector {

  private static final Pattern CIF_PATTERN = Pattern.compile("\\b(?:RO)?\\d{2,10}\\b");
  private static final int[] WEIGHTS = {7, 5, 3, 2, 1, 7, 5, 3, 2};

  @Override
  public @NonNull String getEntityType() {
    return "CIF";
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
    Matcher matcher = CIF_PATTERN.matcher(input);
    while (matcher.find()) {
      String candidate = Objects.requireNonNull(matcher.group());
      if (isValidCif(candidate)) {
        matches.add(new PiiCandidate(candidate, matcher.start(), matcher.end(), getEntityType()));
      }
    }
    return matches;
  }

  private boolean isValidCif(String candidate) {
    String digits = normalize(candidate);
    if (digits.length() < 2 || digits.length() > 10) {
      return false;
    }

    String body = digits.substring(0, digits.length() - 1);
    int weightOffset = WEIGHTS.length - body.length();
    if (weightOffset < 0) {
      return false;
    }

    int sum = 0;
    for (int index = 0; index < body.length(); index++) {
      sum += Character.getNumericValue(body.charAt(index)) * WEIGHTS[weightOffset + index];
    }

    int computed = (sum * 10) % 11;
    if (computed == 10) {
      computed = 0;
    }
    int controlDigit = Character.getNumericValue(digits.charAt(digits.length() - 1));
    return computed == controlDigit;
  }

  private String normalize(String candidate) {
    if (candidate.regionMatches(true, 0, "RO", 0, 2)) {
      return candidate.substring(2);
    }
    return candidate;
  }
}
