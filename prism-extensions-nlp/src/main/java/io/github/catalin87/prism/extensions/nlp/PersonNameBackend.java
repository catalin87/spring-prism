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

import java.util.List;
import org.jspecify.annotations.NonNull;

/** Backend contract for optional person-name candidate extraction. */
public interface PersonNameBackend {

  /** Returns candidate spans that may represent person names inside the source text. */
  @NonNull List<@NonNull PersonNameMatch> detect(@NonNull String text);

  /** Stable backend id used by the hybrid scorer. */
  @NonNull String backendId();
}
