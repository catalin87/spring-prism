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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrismMetricsSnapshotCompatibilityTest {

  @SuppressWarnings("removal")
  @Test
  void legacyConstructorKeepsStableFallbackValuesForNewFields() {
    PrismMetricsSnapshot snapshot =
        new PrismMetricsSnapshot(
            1L,
            2L,
            3L,
            new PrivacyScore(
                90,
                "5m",
                "Legacy compatibility constructor",
                new PrivacyScoreComponent("Coverage", 30, "ok"),
                new PrivacyScoreComponent("Reliability", 30, "ok"),
                new PrivacyScoreComponent("Posture", 30, "ok")),
            Map.of("EMAIL", 1L),
            Map.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            120,
            List.of(),
            12,
            List.of("UNIVERSAL"),
            "DefaultPrismVault",
            false,
            0L,
            new DashboardConfiguration(
                12, 120, 30, new DashboardAlertThresholds(25d, 75d, 5L, 20L, 1L, 5L)));

    assertThat(snapshot.totalActiveRules()).isZero();
    assertThat(snapshot.failureMode()).isEqualTo("FAIL_SAFE");
    assertThat(snapshot.blockedRequestCount()).isZero();
    assertThat(snapshot.blockedResponseCount()).isZero();
    assertThat(snapshot.configuredVaultMode()).isEqualTo("AUTO");
    assertThat(snapshot.customAppSecretConfigured()).isFalse();
    assertThat(snapshot.sharedVaultReady()).isFalse();
    assertThat(snapshot.vaultReadinessStatus()).isEqualTo("UNKNOWN");
    assertThat(snapshot.vaultReadinessDetails()).isEmpty();
  }
}
