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
package io.github.catalin87.prism.boot.autoconfigure;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/** Property-backed detector that uses a configured regex pattern. */
final class CustomRegexDetector implements PiiDetector {

  private final String entityType;
  private final Pattern pattern;

  CustomRegexDetector(@NonNull String entityType, @NonNull String regex) {
    this.entityType = entityType.trim().toUpperCase(Locale.ROOT);
    this.pattern = Pattern.compile(regex);
  }

  @Override
  public @NonNull String getEntityType() {
    return entityType;
  }

  @Override
  public @NonNull List<PiiCandidate> detect(@NonNull String text) {
    if (text.isEmpty()) {
      return List.of();
    }

    Matcher matcher = pattern.matcher(text);
    List<PiiCandidate> candidates = new ArrayList<>();
    while (matcher.find()) {
      if (matcher.start() == matcher.end()) {
        continue;
      }
      candidates.add(new PiiCandidate(matcher.group(), matcher.start(), matcher.end(), entityType));
    }
    return List.copyOf(candidates);
  }
}
