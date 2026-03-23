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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/**
 * Identifies UK National Insurance Numbers (NINO) in the format AA 99 99 99 A. Prefix letter
 * combinations reserved by HMRC (D, F, I, Q, U, V) are explicitly excluded.
 */
public class NinoDetector implements PiiDetector {

  // Format: [A-Z]{2} [0-9]{6} [A-D]. Excludes invalid HMRC prefix letters: D,F,I,Q,U,V.
  private static final Pattern NINO_PATTERN =
      Pattern.compile(
          "\\b(?![DFIQUV])[A-Z](?![DFIQUVO])[A-Z][ ]?" + "\\d{2}[ ]?\\d{2}[ ]?\\d{2}[ ]?[A-D]\\b",
          Pattern.CASE_INSENSITIVE);

  @Override
  public @NonNull String getEntityType() {
    return "NINO";
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
    Matcher matcher = NINO_PATTERN.matcher(input);

    while (matcher.find()) {
      String candidate = Objects.requireNonNull(matcher.group());
      matches.add(new PiiCandidate(candidate, matcher.start(), matcher.end(), getEntityType()));
    }

    return matches;
  }
}
