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

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismRulePackRegistry;
import java.util.Collections;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests for {@link PrismRulePackRegistryEndpoint}. The endpoint is intentionally
 * decoupled from the servlet stack, so invoking {@code read()} / {@code update(...)} directly
 * exercises the full contract — no MockMvc bootstrap required.
 */
class PrismRulePackRegistryEndpointTest {

  @Test
  void readReturnsCurrentRulesetWithDetectorCounts() {
    PrismRulePack universal = new NamedPack("UNIVERSAL", 3);
    PrismRulePack ro = new NamedPack("RO", 1);
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(universal, ro));
    PrismRulePackRegistryEndpoint endpoint =
        new PrismRulePackRegistryEndpoint(registry, List.of(universal, ro));

    PrismRulePackRegistryEndpoint.RulesetView view = endpoint.read();

    assertThat(view.packs())
        .extracting(
            PrismRulePackRegistryEndpoint.RulePackView::name,
            PrismRulePackRegistryEndpoint.RulePackView::detectorCount)
        .containsExactly(Tuple.tuple("UNIVERSAL", 3), Tuple.tuple("RO", 1));
  }

  @Test
  void updateAppliesKnownPackNamesAndReflectsInSnapshot() {
    PrismRulePack universal = new NamedPack("UNIVERSAL", 2);
    PrismRulePack ro = new NamedPack("RO", 1);
    PrismRulePack de = new NamedPack("DE", 4);
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(universal));
    PrismRulePackRegistryEndpoint endpoint =
        new PrismRulePackRegistryEndpoint(registry, List.of(universal, ro, de));

    PrismRulePackRegistryEndpoint.UpdateResult result = endpoint.update(List.of("RO", "DE"));

    assertThat(result.status()).isEqualTo("applied");
    assertThat(result.unknownPackNames()).isEmpty();
    assertThat(result.activeRuleset()).containsExactly("RO", "DE");
    assertThat(result.availablePackNames()).containsExactlyInAnyOrder("UNIVERSAL", "RO", "DE");
    assertThat(registry.snapshot()).extracting(PrismRulePack::getName).containsExactly("RO", "DE");
  }

  @Test
  void updateRejectsUnknownNamesAndLeavesRegistryUnchanged() {
    PrismRulePack universal = new NamedPack("UNIVERSAL", 2);
    PrismRulePack ro = new NamedPack("RO", 1);
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(universal, ro));
    PrismRulePackRegistryEndpoint endpoint =
        new PrismRulePackRegistryEndpoint(registry, List.of(universal, ro));

    PrismRulePackRegistryEndpoint.UpdateResult result =
        endpoint.update(List.of("RO", "ZZ_DOES_NOT_EXIST"));

    assertThat(result.status()).isEqualTo("rejected");
    assertThat(result.unknownPackNames()).containsExactly("ZZ_DOES_NOT_EXIST");
    assertThat(result.activeRuleset()).containsExactly("UNIVERSAL", "RO");
    assertThat(registry.snapshot())
        .extracting(PrismRulePack::getName)
        .containsExactly("UNIVERSAL", "RO");
  }

  @Test
  void updateWithNullPackNamesClearsTheActiveRuleset() {
    PrismRulePack universal = new NamedPack("UNIVERSAL", 2);
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(universal));
    PrismRulePackRegistryEndpoint endpoint =
        new PrismRulePackRegistryEndpoint(registry, List.of(universal));

    PrismRulePackRegistryEndpoint.UpdateResult result = endpoint.update(null);

    assertThat(result.status()).isEqualTo("applied");
    assertThat(result.activeRuleset()).isEmpty();
    assertThat(registry.snapshot()).isEmpty();
  }

  private static final class NamedPack implements PrismRulePack {
    private final String name;
    private final int detectorCount;

    NamedPack(@NonNull String name, int detectorCount) {
      this.name = name;
      this.detectorCount = detectorCount;
    }

    @Override
    public @NonNull List<PiiDetector> getDetectors() {
      return Collections.nCopies(detectorCount, NoOpDetector.INSTANCE);
    }

    @Override
    public @NonNull String getName() {
      return name;
    }
  }

  /** Minimal {@link PiiDetector} stand-in; the endpoint only reads list size, never invokes it. */
  private static final class NoOpDetector implements PiiDetector {
    static final NoOpDetector INSTANCE = new NoOpDetector();

    @Override
    public @NonNull String getEntityType() {
      return "NOOP";
    }

    @Override
    public @NonNull List<@NonNull PiiCandidate> detect(@NonNull String text) {
      return List.of();
    }
  }
}
