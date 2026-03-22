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
package io.github.catalin87.prism.core;

import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * Core interface responsible for scanning text and identifying Personally Identifiable Information
 * (PII) boundaries.
 */
public interface PiiDetector {

  /**
   * Cheap pre-check used to skip expensive regex or checksum work when the input cannot possibly
   * contain this detector's target data.
   *
   * @param text The source text to analyze.
   * @return {@code true} when a full scan is worthwhile, otherwise {@code false}.
   */
  default boolean mayMatch(@NonNull String text) {
    return !text.isEmpty();
  }

  /**
   * Determines the classification descriptor allocated to fragments resolved natively by this
   * framework component.
   *
   * @return The canonical name of the entity being detected (e.g., "EMAIL", "CREDIT_CARD",
   *     "EU_VAT").
   */
  @NonNull String getEntityType();

  /**
   * Scans the provided text and returns bounding offsets for all detected entities.
   *
   * @param text The source text to analyze.
   * @return A list of {@link PiiCandidate} objects indicating exact locations in the text.
   */
  @NonNull List<PiiCandidate> detect(@NonNull String text);

  /** Returns {@code true} when the text contains at least one decimal digit. */
  static boolean containsDigit(@NonNull String text) {
    for (int index = 0; index < text.length(); index++) {
      if (Character.isDigit(text.charAt(index))) {
        return true;
      }
    }
    return false;
  }

  /** Returns {@code true} when either marker character occurs in the source text. */
  static boolean containsEither(@NonNull String text, char first, char second) {
    for (int index = 0; index < text.length(); index++) {
      char current = text.charAt(index);
      if (current == first || current == second) {
        return true;
      }
    }
    return false;
  }
}
