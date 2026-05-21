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

import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismRulePackRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

/**
 * Actuator endpoint exposing and managing the {@link PrismRulePackRegistry}. Mounted at {@code
 * /actuator/prism-rules} once the {@code prism-rules} endpoint id is included in the application's
 * {@code management.endpoints.web.exposure.include} property.
 *
 * <h2>Read</h2>
 *
 * <p>{@code GET /actuator/prism-rules} returns the currently active ruleset — pack names and
 * per-pack detector counts — captured from a single {@link PrismRulePackRegistry#snapshot()} call.
 *
 * <h2>Write</h2>
 *
 * <p>{@code POST /actuator/prism-rules} accepts a JSON body {@code {"packNames": ["UNIVERSAL",
 * "RO"]}} naming the desired active subset of packs. Names are matched against the catalog of
 * {@link PrismRulePack} beans discovered at startup; matched packs are published atomically via
 * {@link PrismRulePackRegistry#update(List)}. Operators may only select from the pre-audited
 * catalog — they cannot inject arbitrary detector logic over HTTP. Unknown names are reported back
 * and the registry is left unchanged.
 *
 * <h2>Security</h2>
 *
 * <p>This endpoint mutates runtime PII-detection policy and MUST be protected by the application's
 * global security configuration (e.g., Spring Security with role-based access on {@code
 * /actuator/**}). The endpoint deliberately does not enforce its own auth; it assumes the
 * surrounding HTTP pipeline gates access. Deployments exposing it without authentication should
 * treat that as a configuration defect.
 *
 * @since 1.2.0
 */
@Endpoint(id = "prism-rules")
public class PrismRulePackRegistryEndpoint {

  private final PrismRulePackRegistry registry;
  private final List<PrismRulePack> availableRulePackCatalog;

  /**
   * Creates the endpoint.
   *
   * @param registry The live registry whose snapshot is read and whose contents are replaced on
   *     write.
   * @param availableRulePackCatalog The full catalog of {@link PrismRulePack} beans known at
   *     startup; write operations resolve incoming {@code packNames} against this catalog.
   */
  public PrismRulePackRegistryEndpoint(
      @NonNull PrismRulePackRegistry registry,
      @NonNull List<PrismRulePack> availableRulePackCatalog) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.availableRulePackCatalog =
        List.copyOf(Objects.requireNonNull(availableRulePackCatalog, "availableRulePackCatalog"));
  }

  /** Returns the current ruleset snapshot. */
  @ReadOperation
  public @NonNull RulesetView read() {
    List<PrismRulePack> snapshot = registry.snapshot();
    List<RulePackView> packs = new ArrayList<>(snapshot.size());
    for (PrismRulePack pack : snapshot) {
      packs.add(new RulePackView(pack.getName(), pack.getDetectors().size()));
    }
    return new RulesetView(packs);
  }

  /**
   * Atomically replaces the active ruleset with the subset of the startup catalog whose names match
   * {@code packNames}. Returns a status payload reporting which names were applied and which (if
   * any) were unknown. When any name is unknown the registry is left unchanged.
   */
  @WriteOperation
  public @NonNull UpdateResult update(@Nullable List<String> packNames) {
    List<String> requested = packNames == null ? List.of() : List.copyOf(packNames);

    Map<String, PrismRulePack> catalogByName = new LinkedHashMap<>();
    for (PrismRulePack pack : availableRulePackCatalog) {
      catalogByName.put(pack.getName(), pack);
    }

    List<String> unknown = new ArrayList<>();
    List<PrismRulePack> resolved = new ArrayList<>(requested.size());
    for (String name : requested) {
      PrismRulePack pack = catalogByName.get(name);
      if (pack == null) {
        unknown.add(name);
      } else {
        resolved.add(pack);
      }
    }

    if (!unknown.isEmpty()) {
      return new UpdateResult(
          "rejected",
          "Unknown pack names; registry unchanged.",
          namesOf(registry.snapshot()),
          unknown,
          Set.copyOf(catalogByName.keySet()));
    }

    registry.update(resolved);
    return new UpdateResult(
        "applied",
        "Ruleset updated.",
        namesOf(registry.snapshot()),
        List.of(),
        Set.copyOf(catalogByName.keySet()));
  }

  private static @NonNull List<String> namesOf(@NonNull List<PrismRulePack> packs) {
    List<String> names = new ArrayList<>(packs.size());
    for (PrismRulePack pack : packs) {
      names.add(pack.getName());
    }
    return names;
  }

  /** JSON view of the active ruleset returned by {@link #read()}. */
  public record RulesetView(@NonNull List<RulePackView> packs) {}

  /** JSON view of a single rule pack. */
  public record RulePackView(@NonNull String name, int detectorCount) {}

  /** JSON view returned by {@link #update(List)}. */
  public record UpdateResult(
      @NonNull String status,
      @NonNull String message,
      @NonNull List<String> activeRuleset,
      @NonNull List<String> unknownPackNames,
      @NonNull Set<String> availablePackNames) {}
}
