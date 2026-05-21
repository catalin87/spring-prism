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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

/**
 * Actuator endpoint exposing the active {@link PrismRulePackRegistry} for inspection and managed
 * runtime updates. Reachable at {@code /actuator/prism-rules}.
 *
 * <p><strong>Security:</strong> This endpoint exposes both read and write operations against the
 * live PII detection rule set; a misconfigured swap can disable PII protection until the next swap.
 * The Spring Prism starter intentionally does <em>not</em> ship a Spring Security configuration —
 * the host application is responsible for authenticating and authorising calls to {@code
 * /actuator/prism-rules}, typically by restricting actuator endpoints to an internal management
 * port and/or requiring an operator role.
 *
 * <p><strong>Write contract:</strong> The {@link #replace(List)} operation accepts <em>only</em>
 * the names of {@link PrismRulePack} beans already registered in the Spring {@link
 * ListableBeanFactory}. Each requested name is resolved against the application context; an unknown
 * name causes the entire swap to be rejected with no change to the registry. This deliberately
 * narrow contract avoids exposing arbitrary detector definitions (regex, labels, classes) over
 * HTTP.
 *
 * @since 1.3.0
 */
@Endpoint(id = "prism-rules")
public class PrismRulePackRegistryEndpoint {

  private final PrismRulePackRegistry registry;
  private final ListableBeanFactory beanFactory;

  /**
   * Creates the endpoint with the live registry and the bean factory used to resolve write-time
   * pack-name references.
   */
  public PrismRulePackRegistryEndpoint(
      @NonNull PrismRulePackRegistry registry, @NonNull ListableBeanFactory beanFactory) {
    this.registry = registry;
    this.beanFactory = beanFactory;
  }

  /** Returns a snapshot of the currently active rule packs. */
  @ReadOperation
  public @NonNull RulePackRegistrySnapshot rules() {
    List<PrismRulePack> active = registry.snapshot();
    List<RulePackInfo> activePacks = new ArrayList<>(active.size());
    for (PrismRulePack pack : active) {
      activePacks.add(new RulePackInfo(pack.getName(), pack.getDetectors().size()));
    }
    List<String> availablePackBeanNames =
        List.copyOf(beanFactory.getBeansOfType(PrismRulePack.class).keySet());
    return new RulePackRegistrySnapshot(activePacks, availablePackBeanNames);
  }

  /** Returns the active rule pack matching the given selector, or {@code null} if not active. */
  @ReadOperation
  public @Nullable RulePackInfo rule(@Selector @NonNull String name) {
    for (PrismRulePack pack : registry.snapshot()) {
      if (pack.getName().equals(name)) {
        return new RulePackInfo(pack.getName(), pack.getDetectors().size());
      }
    }
    return null;
  }

  /**
   * Replaces the active rule packs with the {@link PrismRulePack} beans whose <em>Spring bean
   * names</em> match the request. The swap is atomic: either every requested bean resolves and the
   * registry is updated in a single {@link PrismRulePackRegistry#replace(List)} call, or no change
   * occurs.
   *
   * <p>An empty list is accepted and will disable all PII detection until the next swap; callers
   * are expected to gate this behaviour at the security layer.
   *
   * @param packBeanNames Spring bean names of {@link PrismRulePack} beans to activate, in priority
   *     order. Must not be {@code null}.
   * @return A snapshot of the post-swap registry state for caller verification.
   * @throws UnknownRulePackException if any name does not resolve to a registered {@link
   *     PrismRulePack} bean.
   */
  @WriteOperation
  public @NonNull RulePackRegistrySnapshot replace(@NonNull List<String> packBeanNames) {
    Map<String, PrismRulePack> available = beanFactory.getBeansOfType(PrismRulePack.class);
    List<PrismRulePack> resolved = new ArrayList<>(packBeanNames.size());
    List<String> unknown = new ArrayList<>();
    for (String name : packBeanNames) {
      PrismRulePack pack = available.get(name);
      if (pack == null) {
        unknown.add(name);
      } else {
        resolved.add(pack);
      }
    }
    if (!unknown.isEmpty()) {
      throw new UnknownRulePackException(unknown, List.copyOf(available.keySet()));
    }
    registry.replace(resolved);
    return rules();
  }

  /**
   * Snapshot DTO returned by {@code /actuator/prism-rules} read and write operations.
   *
   * @param active Rule packs currently active in the registry, in order.
   * @param availablePackBeanNames All {@link PrismRulePack} bean names known to the application
   *     context, for operator discoverability when composing a swap request.
   */
  public record RulePackRegistrySnapshot(
      @NonNull List<RulePackInfo> active, @NonNull List<String> availablePackBeanNames) {

    public RulePackRegistrySnapshot {
      active = List.copyOf(active);
      availablePackBeanNames = List.copyOf(availablePackBeanNames);
    }
  }

  /**
   * Metadata for a single active rule pack.
   *
   * @param name The {@link PrismRulePack#getName()} value.
   * @param detectorCount Number of detectors the pack contributes.
   */
  public record RulePackInfo(@NonNull String name, int detectorCount) {}

  /**
   * Thrown when a write operation references one or more pack-bean names that do not resolve in the
   * current Spring context. Carries the unknown names and the full set of available names to help
   * operators correct their request.
   */
  public static final class UnknownRulePackException extends RuntimeException {

    private final List<String> unknownNames;
    private final List<String> availableNames;

    UnknownRulePackException(List<String> unknownNames, List<String> availableNames) {
      super(buildMessage(unknownNames, availableNames));
      this.unknownNames = List.copyOf(unknownNames);
      this.availableNames = List.copyOf(availableNames);
    }

    public @NonNull List<String> unknownNames() {
      return unknownNames;
    }

    public @NonNull List<String> availableNames() {
      return availableNames;
    }

    /** Renders the exception as a structured payload suitable for JSON serialisation. */
    public @NonNull Map<String, Object> toPayload() {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("error", "unknown_rule_pack");
      payload.put("message", getMessage());
      payload.put("unknown", unknownNames);
      payload.put("available", availableNames);
      return payload;
    }

    private static String buildMessage(List<String> unknown, List<String> available) {
      return "Unknown rule pack bean name(s): " + unknown + "; available pack beans: " + available;
    }
  }
}
