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
 * Identifies US Social Security Numbers in AAA-BB-CCCC format. Invalid SSN ranges (e.g., 000, 666,
 * 900-999 area codes) are explicitly excluded per SSA issuance rules.
 */
public class SsnDetector implements PiiDetector {

  // Matches XXX-XX-XXXX or XXXXXXXXX. Excludes known invalid area codes: 000, 666, 9xx.
  private static final Pattern SSN_PATTERN =
      Pattern.compile("\\b(?!000|666|9\\d{2})\\d{3}[- ]?(?!00)\\d{2}[- ]?(?!0000)\\d{4}\\b");

  @Override
  public @NonNull String getEntityType() {
    return "SSN";
  }

  @Override
  public @NonNull List<PiiCandidate> detect(@NonNull String input) {
    if (input.isEmpty()) {
      return List.of();
    }

    List<PiiCandidate> matches = new ArrayList<>();
    Matcher matcher = SSN_PATTERN.matcher(input);

    while (matcher.find()) {
      matches.add(
          new PiiCandidate(matcher.group(), matcher.start(), matcher.end(), getEntityType()));
    }

    return matches;
  }
}
