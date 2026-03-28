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
package io.github.catalin87.prism.extensions.nlp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/** Heuristic candidate extractor for capitalized person-name spans. */
public final class HeuristicPersonNameBackend implements PersonNameBackend {

  private static final Pattern NAME_PATTERN =
      Pattern.compile(
          "\\b(?:(?:Mr|Mrs|Ms|Miss|Dr|Prof)\\.?\\s+)?\\p{Lu}[\\p{L}'-]+(?:\\s+\\p{Lu}[\\p{L}'-]+)"
              + "{0,2}\\b");

  @Override
  public @NonNull List<@NonNull PersonNameMatch> detect(@NonNull String text) {
    List<PersonNameMatch> matches = new ArrayList<>();
    Matcher matcher = NAME_PATTERN.matcher(text);
    while (matcher.find()) {
      String candidate = Objects.requireNonNull(matcher.group());
      matches.add(
          new PersonNameMatch(matcher.start(), matcher.end(), candidate, backendId(), 0.60d));
    }
    return matches;
  }

  @Override
  public @NonNull String backendId() {
    return "heuristic";
  }
}
