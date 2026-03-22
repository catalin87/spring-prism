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
package io.github.catalin87.prism.spring.ai.advisor;

import org.jspecify.annotations.NonNull;

/** Extension hook for collecting Prism runtime metrics without coupling core logic to a backend. */
public interface PrismMetricsSink {

  PrismMetricsSink NOOP = new NoOpPrismMetricsSink();

  void onDetected(@NonNull String rulePackName, @NonNull String entityType, int count);

  void onDetectionError(@NonNull String rulePackName, @NonNull String entityType);

  void onTokenized(int count);

  void onDetokenized(int count);

  final class NoOpPrismMetricsSink implements PrismMetricsSink {
    @Override
    public void onDetected(@NonNull String rulePackName, @NonNull String entityType, int count) {}

    @Override
    public void onDetectionError(@NonNull String rulePackName, @NonNull String entityType) {}

    @Override
    public void onTokenized(int count) {}

    @Override
    public void onDetokenized(int count) {}
  }
}
