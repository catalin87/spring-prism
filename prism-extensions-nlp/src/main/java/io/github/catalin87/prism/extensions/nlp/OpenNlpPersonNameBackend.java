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
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;
import org.jspecify.annotations.NonNull;

/** OpenNLP-backed candidate extractor using a caller-provided TokenNameFinder model. */
public final class OpenNlpPersonNameBackend implements PersonNameBackend {

  private final NameFinderME nameFinder;

  public OpenNlpPersonNameBackend(NameFinderME nameFinder) {
    this.nameFinder = Objects.requireNonNull(nameFinder);
  }

  @Override
  public @NonNull List<@NonNull PersonNameMatch> detect(@NonNull String text) {
    Span[] tokenSpans = SimpleTokenizer.INSTANCE.tokenizePos(text);
    if (tokenSpans.length == 0) {
      return List.of();
    }

    String[] tokens = new String[tokenSpans.length];
    for (int index = 0; index < tokenSpans.length; index++) {
      tokens[index] = tokenSpans[index].getCoveredText(text).toString();
    }

    Span[] nameSpans = nameFinder.find(tokens);
    double[] probabilities = nameFinder.probs(nameSpans);
    nameFinder.clearAdaptiveData();
    if (nameSpans.length == 0) {
      return List.of();
    }

    List<PersonNameMatch> matches = new ArrayList<>(nameSpans.length);
    for (int index = 0; index < nameSpans.length; index++) {
      Span nameSpan = nameSpans[index];
      int start = tokenSpans[nameSpan.getStart()].getStart();
      int end = tokenSpans[nameSpan.getEnd() - 1].getEnd();
      matches.add(
          new PersonNameMatch(
              start,
              end,
              text.substring(start, end),
              backendId(),
              index < probabilities.length ? probabilities[index] : 0.80d));
    }
    return matches;
  }

  @Override
  public @NonNull String backendId() {
    return "opennlp";
  }
}
