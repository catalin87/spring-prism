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
package io.github.catalin87.prism.core.guard;

import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;

/**
 * Active Defense counter that enforces an upper bound on the number of vault tokens restored within
 * a single LLM response or streaming session. Thread-safe and lock-free; one instance is intended
 * to be scoped to a single synchronous request or to a single reactive subscription.
 *
 * <p>The guard exists to mitigate adversarial exfiltration: a compromised or prompt-injected LLM
 * may attempt to return hundreds of {@code <PRISM_...>} tokens at once in order to coerce Spring
 * Prism into restoring the underlying PII values. Wiring a guard around the detokenization path
 * caps the maximum amount of data that can ever leave the protection boundary in one go.
 *
 * <p>Semantics:
 *
 * <ul>
 *   <li>Threshold {@link #UNLIMITED} (={@code -1}) disables enforcement; {@link
 *       #recordAndCheck(int)} always returns {@code false}.
 *   <li>Threshold {@code 0} means "block any restoration"; the first non-zero record trips.
 *   <li>Positive thresholds are inclusive ceilings: cumulative count strictly greater than the
 *       threshold trips the guard.
 * </ul>
 *
 * @since 1.2.0
 */
public final class ExfiltrationGuard {

  /** Sentinel threshold value indicating the guard is disabled and will never trip. */
  public static final int UNLIMITED = -1;

  private final int threshold;
  private final AtomicInteger restoredCount = new AtomicInteger();

  /**
   * Creates a new guard.
   *
   * @param threshold the maximum number of restored tokens allowed; {@link #UNLIMITED} disables
   *     enforcement, {@code 0} blocks any restoration, positive values are inclusive ceilings.
   *     Values less than {@link #UNLIMITED} are rejected.
   * @throws IllegalArgumentException if {@code threshold < -1}
   */
  public ExfiltrationGuard(int threshold) {
    if (threshold < UNLIMITED) {
      throw new IllegalArgumentException(
          "threshold must be >= -1 (use ExfiltrationGuard.UNLIMITED to disable); got " + threshold);
    }
    this.threshold = threshold;
  }

  /**
   * Records {@code n} additional restored tokens and reports whether the cumulative count has
   * exceeded the threshold.
   *
   * <p>This method is the single atomic decision point: callers should treat a {@code true} return
   * as a hard block signal and abort further restoration immediately. Subsequent calls after a trip
   * will continue to return {@code true} as the count keeps climbing, which is intentional — the
   * guard remains tripped for the rest of its lifetime.
   *
   * @param n number of tokens just restored in the current segment; must be non-negative
   * @return {@code true} if the threshold has now been exceeded, {@code false} otherwise (or always
   *     {@code false} when the guard is disabled)
   * @throws IllegalArgumentException if {@code n < 0}
   */
  public boolean recordAndCheck(int n) {
    if (n < 0) {
      throw new IllegalArgumentException("n must be >= 0; got " + n);
    }
    if (threshold == UNLIMITED) {
      return false;
    }
    int updated = (n == 0) ? restoredCount.get() : restoredCount.addAndGet(n);
    return updated > threshold;
  }

  /** Current cumulative count of restored tokens observed by this guard. */
  public int restoredCount() {
    return restoredCount.get();
  }

  /** Configured threshold, or {@link #UNLIMITED} if disabled. */
  public int threshold() {
    return threshold;
  }

  /** Whether this guard enforces any limit. */
  public boolean isEnabled() {
    return threshold != UNLIMITED;
  }

  /**
   * Builds the canonical, public-safe error message used when {@link #recordAndCheck(int)} trips.
   * Centralized here so synchronous and reactive call sites emit identical messages, simplifying
   * downstream log aggregation and alert routing.
   */
  public @NonNull String thresholdMessage() {
    return "Response blocked: restored-token count "
        + restoredCount.get()
        + " exceeded the Active Defense threshold of "
        + threshold
        + ".";
  }
}
