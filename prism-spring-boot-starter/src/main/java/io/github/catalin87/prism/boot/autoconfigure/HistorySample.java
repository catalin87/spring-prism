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

import org.jspecify.annotations.NonNull;

/**
 * Immutable server-side history point retained for dashboard trend rendering.
 *
 * <p>Instances are produced exclusively by {@link PrismRuntimeMetrics#captureHistorySample(String)}
 * and are safe to publish across threads — every field is either a {@code long}/{@code double}
 * primitive or a non-null immutable {@link String}.
 *
 * @param capturedAt ISO-8601 timestamp of the snapshot; never {@code null}.
 * @param totalDetections Sum of all detection counts across rule packs at the time of capture.
 * @param detectionErrors Cumulative detector errors at the time of capture.
 * @param scanMilliseconds Rolling-average scan latency in milliseconds.
 * @param tokenizedCount Cumulative tokens produced.
 * @param detokenizedCount Cumulative tokens redeemed.
 * @param tokenBacklog {@code max(0, tokenizedCount - detokenizedCount)} captured atomically.
 * @param vaultType Configured vault implementation type (e.g. {@code IN_MEMORY}); never {@code
 *     null}.
 */
public record HistorySample(
    @NonNull String capturedAt,
    long totalDetections,
    long detectionErrors,
    double scanMilliseconds,
    long tokenizedCount,
    long detokenizedCount,
    long tokenBacklog,
    @NonNull String vaultType) {}
