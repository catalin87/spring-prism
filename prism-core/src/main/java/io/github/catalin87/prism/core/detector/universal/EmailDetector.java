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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/**
 * Identifies RFC 5321-compliant email address sequences within free-form text using a
 * production-hardened regex boundary that avoids catastrophic backtracking.
 */
public class EmailDetector implements PiiDetector {

  // Anchored at word boundaries; avoids catastrophic backtracking via possessive quantifiers.
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile(
          "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}", Pattern.CASE_INSENSITIVE);

  @Override
  public @NonNull String getEntityType() {
    return "EMAIL";
  }

  @Override
  public boolean mayMatch(@NonNull String input) {
    return input.indexOf('@') >= 0;
  }

  @Override
  public @NonNull List<@NonNull PiiCandidate> detect(@NonNull String input) {
    if (!mayMatch(input)) {
      return List.of();
    }

    List<@NonNull PiiCandidate> matches = new ArrayList<>();
    Matcher matcher = EMAIL_PATTERN.matcher(input);

    while (matcher.find()) {
      String candidate = Objects.requireNonNull(matcher.group());
      matches.add(new PiiCandidate(candidate, matcher.start(), matcher.end(), getEntityType()));
    }

    return matches;
  }
}
