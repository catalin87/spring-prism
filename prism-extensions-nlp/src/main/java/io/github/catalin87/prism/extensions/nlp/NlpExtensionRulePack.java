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

import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.PrismRulePack;
import java.util.List;
import org.jspecify.annotations.NonNull;

/** Rule pack exposing optional NLP-backed entity detectors. */
public record NlpExtensionRulePack(@NonNull List<PiiDetector> detectors) implements PrismRulePack {

  @Override
  public @NonNull String getName() {
    return "NLP_EXTENSIONS";
  }

  @Override
  public @NonNull List<PiiDetector> getDetectors() {
    return detectors;
  }

  @Override
  public boolean isAutoDiscoverable() {
    return true;
  }
}
