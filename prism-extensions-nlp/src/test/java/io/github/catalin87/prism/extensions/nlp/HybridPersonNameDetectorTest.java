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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.catalin87.prism.core.PiiCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridPersonNameDetectorTest {

  @Test
  void acceptsPersonNameWhenHeuristicAndOpenNlpAgree() {
    HybridPersonNameDetector detector =
        new HybridPersonNameDetector(
            List.of(
                backend("heuristic", new PersonNameMatch(9, 17, "John Doe", "heuristic", 0.60d)),
                backend("opennlp", new PersonNameMatch(9, 17, "John Doe", "opennlp", 0.91d))),
            properties());

    List<PiiCandidate> detected = detector.detect("Customer John Doe requested support.");

    assertThat(detected)
        .extracting(PiiCandidate::text, PiiCandidate::label)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("John Doe", "PERSON_NAME"));
  }

  @Test
  void rejectsKnownTechnicalPhraseEvenWhenBackendsMatch() {
    HybridPersonNameDetector detector =
        new HybridPersonNameDetector(
            List.of(
                backend("heuristic", new PersonNameMatch(0, 11, "Spring Boot", "heuristic", 0.60d)),
                backend("opennlp", new PersonNameMatch(0, 11, "Spring Boot", "opennlp", 0.92d))),
            properties());

    assertThat(detector.detect("Spring Boot service is deployed.")).isEmpty();
  }

  @Test
  void rejectsSingleTokenNameWithoutSupportingContext() {
    HybridPersonNameDetector detector =
        new HybridPersonNameDetector(
            List.of(backend("heuristic", new PersonNameMatch(0, 4, "John", "heuristic", 0.60d))),
            properties());

    assertThat(detector.detect("John resolved the issue.")).isEmpty();
  }

  private static NlpPersonNameProperties properties() {
    NlpPersonNameProperties properties = new NlpPersonNameProperties();
    properties.setConfidenceThreshold(4);
    return properties;
  }

  private static PersonNameBackend backend(String backendId, PersonNameMatch... matches) {
    return new PersonNameBackend() {
      @Override
      public List<PersonNameMatch> detect(String text) {
        return List.of(matches);
      }

      @Override
      public String backendId() {
        return backendId;
      }
    };
  }
}
