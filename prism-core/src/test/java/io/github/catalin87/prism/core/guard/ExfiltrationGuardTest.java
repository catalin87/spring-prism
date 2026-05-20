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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ExfiltrationGuardTest {

  @Test
  void disabledGuardNeverTrips() {
    ExfiltrationGuard guard = new ExfiltrationGuard(ExfiltrationGuard.UNLIMITED);

    assertFalse(guard.isEnabled());
    assertFalse(guard.recordAndCheck(1_000_000));
    assertFalse(guard.recordAndCheck(0));
    assertEquals(0, guard.restoredCount(), "disabled guard must not mutate its counter");
    assertEquals(ExfiltrationGuard.UNLIMITED, guard.threshold());
  }

  @Test
  void positiveThresholdTripsStrictlyAboveCeiling() {
    ExfiltrationGuard guard = new ExfiltrationGuard(3);

    assertFalse(guard.recordAndCheck(1));
    assertFalse(guard.recordAndCheck(1));
    assertFalse(guard.recordAndCheck(1), "count==threshold must not trip (inclusive ceiling)");
    assertTrue(guard.recordAndCheck(1), "count>threshold must trip");
    assertEquals(4, guard.restoredCount());
  }

  @Test
  void zeroThresholdTripsOnAnyRestoration() {
    ExfiltrationGuard guard = new ExfiltrationGuard(0);

    assertTrue(guard.isEnabled());
    assertFalse(guard.recordAndCheck(0), "no-op recording must not trip a zero-threshold guard");
    assertTrue(guard.recordAndCheck(1));
  }

  @Test
  void guardStaysTrippedAfterCrossingThreshold() {
    ExfiltrationGuard guard = new ExfiltrationGuard(2);

    assertFalse(guard.recordAndCheck(2));
    assertTrue(guard.recordAndCheck(1));
    assertTrue(guard.recordAndCheck(0), "post-trip queries must continue to report tripped");
  }

  @Test
  void thresholdMessageEmbedsCurrentCountAndCeiling() {
    ExfiltrationGuard guard = new ExfiltrationGuard(100);
    guard.recordAndCheck(105);

    String message = guard.thresholdMessage();

    assertTrue(message.contains("105"), () -> "missing count in: " + message);
    assertTrue(message.contains("100"), () -> "missing threshold in: " + message);
  }

  @Test
  void invalidThresholdRejected() {
    assertThrows(IllegalArgumentException.class, () -> new ExfiltrationGuard(-2));
    assertThrows(IllegalArgumentException.class, () -> new ExfiltrationGuard(Integer.MIN_VALUE));
  }

  @Test
  void negativeIncrementRejected() {
    ExfiltrationGuard guard = new ExfiltrationGuard(10);

    assertThrows(IllegalArgumentException.class, () -> guard.recordAndCheck(-1));
  }

  @Test
  void counterIsAtomicUnderConcurrentRecording() throws Exception {
    int threads = 32;
    int incrementsPerThread = 1_000;
    // Enabled guard with headroom that exceeds the expected total — keeps the AtomicInteger path
    // active without forcing every increment past the threshold (which is also valid behavior,
    // but obscures the atomicity assertion we want to make here).
    ExfiltrationGuard guard = new ExfiltrationGuard(threads * incrementsPerThread * 2);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int t = 0; t < threads; t++) {
        executor.submit(
            () -> {
              try {
                start.await();
                for (int i = 0; i < incrementsPerThread; i++) {
                  guard.recordAndCheck(1);
                }
              } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
              } finally {
                done.countDown();
              }
            });
      }
      start.countDown();
      assertTrue(done.await(10, TimeUnit.SECONDS), "concurrent test did not complete in time");
    }

    assertEquals(threads * incrementsPerThread, guard.restoredCount());
  }
}
