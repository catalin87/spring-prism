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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.util.Span;
import org.junit.jupiter.api.Test;

class OpenNlpPersonNameBackendTest {

  @Test
  void mapsTokenSpansBackToOriginalTextOffsets() {
    NameFinderME nameFinder = mock(NameFinderME.class);
    Span[] nameSpans = {new Span(1, 3)};
    when(nameFinder.find(any(String[].class))).thenReturn(nameSpans);
    when(nameFinder.probs(nameSpans)).thenReturn(new double[] {0.91d});

    OpenNlpPersonNameBackend backend = new OpenNlpPersonNameBackend(nameFinder);

    List<PersonNameMatch> matches = backend.detect("Customer John Doe called");

    assertThat(matches).containsExactly(new PersonNameMatch(9, 17, "John Doe", "opennlp", 0.91d));
  }

  @Test
  void returnsEmptyWhenTokenizerFindsNoTokens() {
    OpenNlpPersonNameBackend backend = new OpenNlpPersonNameBackend(mock(NameFinderME.class));

    assertThat(backend.detect("   ")).isEmpty();
  }
}
