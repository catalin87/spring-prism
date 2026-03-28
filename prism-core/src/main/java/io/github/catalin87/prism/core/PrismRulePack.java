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
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.NonNull;

/**
 * Interface defining a logical grouping of localized or contextual {@link PiiDetector}s. Following
 * the Spring Prism "EU-First" architecture, packs can be toggled by the active locale context.
 */
public interface PrismRulePack {

  /**
   * Identifies all active sequence finding logic components bound to this specific core RulePack.
   *
   * @return A list of actively registered detectors provided by this rule pack.
   */
  @NonNull List<@NonNull PiiDetector> getDetectors();

  /**
   * Evaluates the standard geographical or contextual identifier of this matrix node.
   *
   * @return the locale identifier or logical grouping name (e.g. "EN", "EU", "UNIVERSAL").
   */
  @NonNull String getName();

  /**
   * Declares the locale aliases that should activate this pack through {@code
   * spring.prism.locales}.
   *
   * <p>The default keeps {@code 1.x} custom implementations compatible by exposing only the pack
   * name itself as an activation alias.
   *
   * @return the normalized locale aliases that map to this pack.
   */
  default @NonNull Set<@NonNull String> getActivationAliases() {
    return Set.of(getName().trim().toUpperCase(Locale.ROOT));
  }

  /**
   * Signals whether this rule pack should be auto-discovered by the Spring Boot starter when it is
   * exposed as a bean on the application context.
   *
   * <p>The default is {@code false} to preserve {@code 1.0.0} behavior for applications that
   * already register custom {@link PrismRulePack} beans and do not expect them to become active
   * automatically after upgrading to {@code 1.1.0}.
   *
   * @return {@code true} when the starter may auto-discover this pack, otherwise {@code false}.
   */
  default boolean isAutoDiscoverable() {
    return false;
  }
}
