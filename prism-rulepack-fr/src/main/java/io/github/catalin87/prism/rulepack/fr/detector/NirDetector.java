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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/** Identifies French NIR numbers using the official modulo-97 checksum validation. */
public final class NirDetector implements PiiDetector {

  private static final Pattern NIR_PATTERN =
      Pattern.compile(
          "\\b[12]\\d{2}(?:0[1-9]|1[0-2])" + "(?:0[1-9]|[1-9]\\d|2[AB]|9\\d)\\d{6}\\d{2}\\b");

  @Override
  public @NonNull String getEntityType() {
    return "NIR";
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
    Matcher matcher = NIR_PATTERN.matcher(input);
    while (matcher.find()) {
      String candidate = Objects.requireNonNull(matcher.group());
      if (isValidNir(candidate)) {
        matches.add(new PiiCandidate(candidate, matcher.start(), matcher.end(), getEntityType()));
      }
    }
    return matches;
  }

  private boolean isValidNir(String candidate) {
    String numeric = candidate.replace("2A", "19").replace("2B", "18");
    if (numeric.length() != 15) {
      return false;
    }

    String body = numeric.substring(0, 13);
    int key = Integer.parseInt(numeric.substring(13));
    BigInteger value = new BigInteger(body);
    int computed = 97 - value.mod(BigInteger.valueOf(97)).intValue();
    return computed == key;
  }
}
