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

import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.ruleset.EuropeRulePack;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;

/** Resolves active rule packs from starter properties. */
final class RulePackRegistrar {

  private static final Set<String> EUROPE_LOCALES =
      Set.of("EU", "EUROPE", "DE", "PL", "RO", "UK", "GB");
  private static final Set<String> UNIVERSAL_LOCALES = Set.of("UNIVERSAL", "GLOBAL", "EN", "US");

  List<PrismRulePack> resolve(SpringPrismProperties properties) {
    Set<String> locales =
        properties.getLocales().stream()
            .map(locale -> locale == null ? "" : locale.trim().toUpperCase(Locale.ROOT))
            .filter(locale -> !locale.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));

    if (locales.isEmpty()) {
      locales.add("UNIVERSAL");
    }

    List<PrismRulePack> packs = new ArrayList<>();
    if (locales.stream().anyMatch(EUROPE_LOCALES::contains)) {
      packs.add(filtered(new EuropeRulePack(), properties.getDisabledRules()));
    } else if (locales.stream().anyMatch(UNIVERSAL_LOCALES::contains)) {
      packs.add(filtered(new UniversalRulePack(), properties.getDisabledRules()));
    } else {
      packs.add(filtered(new UniversalRulePack(), properties.getDisabledRules()));
    }
    return List.copyOf(packs);
  }

  private PrismRulePack filtered(PrismRulePack delegate, Set<String> disabledRules) {
    if (disabledRules == null || disabledRules.isEmpty()) {
      return delegate;
    }

    Set<String> normalizedDisabled =
        disabledRules.stream()
            .map(rule -> rule == null ? "" : rule.trim().toUpperCase(Locale.ROOT))
            .filter(rule -> !rule.isEmpty())
            .collect(Collectors.toUnmodifiableSet());

    List<PiiDetector> detectors =
        delegate.getDetectors().stream()
            .filter(
                detector ->
                    !normalizedDisabled.contains(detector.getEntityType().toUpperCase(Locale.ROOT)))
            .toList();

    return new FilteredRulePack(delegate.getName(), detectors);
  }

  private record FilteredRulePack(@NonNull String name, @NonNull List<PiiDetector> detectors)
      implements PrismRulePack {

    @Override
    public @NonNull String getName() {
      return name;
    }

    @Override
    public @NonNull List<PiiDetector> getDetectors() {
      return detectors;
    }
  }
}
