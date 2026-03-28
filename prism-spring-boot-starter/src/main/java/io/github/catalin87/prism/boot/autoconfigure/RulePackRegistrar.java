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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;

/** Resolves active rule packs from starter properties. */
final class RulePackRegistrar {

  private static final Set<String> EUROPE_LOCALES =
      Set.of(
          "EU",
          "EUROPE",
          "DE",
          "DEU",
          "GERMANY",
          "PL",
          "POL",
          "POLAND",
          "RO",
          "ROU",
          "ROMANIA",
          "UK",
          "GB",
          "GBR",
          "UNITED_KINGDOM",
          "FR",
          "FRA",
          "FRANCE",
          "NL",
          "NLD",
          "NETHERLANDS");
  private static final Set<String> UNIVERSAL_LOCALES = Set.of("UNIVERSAL", "GLOBAL", "EN", "US");
  private static final Set<String> BASELINE_PACK_NAMES = Set.of("EUROPE", "UNIVERSAL");

  List<PrismRulePack> resolve(
      SpringPrismProperties properties, List<PrismRulePack> additionalRulePacks) {
    Set<String> locales =
        properties.getLocales().stream()
            .map(locale -> locale == null ? "" : locale.trim().toUpperCase(Locale.ROOT))
            .filter(locale -> !locale.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));

    if (locales.isEmpty()) {
      locales.add("UNIVERSAL");
    }

    List<PrismRulePack> autoDiscoverableRulePacks =
        additionalRulePacks == null
            ? List.of()
            : additionalRulePacks.stream().filter(PrismRulePack::isAutoDiscoverable).toList();

    List<PrismRulePack> packs = new ArrayList<>();
    PrismRulePack primaryRulePack = resolvePrimaryRulePack(locales, autoDiscoverableRulePacks);
    packs.add(filtered(primaryRulePack, properties.getDisabledRules()));

    CustomPropertyRulePack customPropertyRulePack = customPack(properties);
    if (!customPropertyRulePack.getDetectors().isEmpty()) {
      packs.add(filtered(customPropertyRulePack, properties.getDisabledRules()));
    }

    autoDiscoverableRulePacks.stream()
        .filter(rulePack -> !sameRulePack(rulePack, primaryRulePack))
        .filter(rulePack -> shouldAppendAutoDiscoverable(rulePack, locales))
        .map(rulePack -> filtered(rulePack, properties.getDisabledRules()))
        .forEach(packs::add);

    Map<String, PrismRulePack> deduplicated = new LinkedHashMap<>();
    for (PrismRulePack pack : packs) {
      deduplicated.putIfAbsent(normalizedName(pack), pack);
    }
    return List.copyOf(deduplicated.values());
  }

  private PrismRulePack resolvePrimaryRulePack(
      Set<String> locales, List<PrismRulePack> autoDiscoverableRulePacks) {
    Set<String> specificLocales =
        locales.stream()
            .filter(locale -> !BASELINE_PACK_NAMES.contains(locale))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    java.util.Optional<PrismRulePack> exactMatch =
        findMatchingPack(autoDiscoverableRulePacks, specificLocales);
    if (exactMatch.isPresent()) {
      return exactMatch.orElseThrow();
    }

    if (locales.stream().anyMatch(EUROPE_LOCALES::contains)) {
      return findMatchingPack(autoDiscoverableRulePacks, EUROPE_LOCALES)
          .orElseGet(EuropeRulePack::new);
    }
    if (locales.stream().anyMatch(UNIVERSAL_LOCALES::contains)) {
      return findMatchingPack(autoDiscoverableRulePacks, UNIVERSAL_LOCALES)
          .orElseGet(UniversalRulePack::new);
    }
    return findMatchingPack(autoDiscoverableRulePacks, UNIVERSAL_LOCALES)
        .orElseGet(UniversalRulePack::new);
  }

  private java.util.Optional<PrismRulePack> findMatchingPack(
      List<PrismRulePack> candidates, Set<String> requestedAliases) {
    return candidates.stream()
        .filter(rulePack -> matchesAnyAlias(rulePack, requestedAliases))
        .sorted(
            java.util.Comparator.comparing(
                (PrismRulePack rulePack) -> isBaselineFamilyPack(rulePack)))
        .findFirst();
  }

  private boolean shouldAppendAutoDiscoverable(
      PrismRulePack candidate, Set<String> requestedLocales) {
    return !isBaselineFamilyPack(candidate) && matchesAnyAlias(candidate, requestedLocales);
  }

  private boolean isBaselineFamilyPack(PrismRulePack rulePack) {
    return BASELINE_PACK_NAMES.contains(normalizedName(rulePack));
  }

  private boolean matchesAnyAlias(PrismRulePack rulePack, Set<String> requestedAliases) {
    Set<String> normalizedAliases =
        rulePack.getActivationAliases().stream()
            .map(alias -> alias == null ? "" : alias.trim().toUpperCase(Locale.ROOT))
            .filter(alias -> !alias.isEmpty())
            .collect(Collectors.toSet());
    return requestedAliases.stream().anyMatch(normalizedAliases::contains);
  }

  private boolean sameRulePack(PrismRulePack left, PrismRulePack right) {
    return normalizedName(left).equals(normalizedName(right));
  }

  private String normalizedName(PrismRulePack rulePack) {
    return rulePack.getName().trim().toUpperCase(Locale.ROOT);
  }

  private CustomPropertyRulePack customPack(SpringPrismProperties properties) {
    List<PiiDetector> detectors =
        properties.getCustomRules().stream()
            .filter(rule -> rule.getName() != null && !rule.getName().isBlank())
            .filter(rule -> rule.getPattern() != null && !rule.getPattern().isBlank())
            .<PiiDetector>map(rule -> new CustomRegexDetector(rule.getName(), rule.getPattern()))
            .toList();
    return new CustomPropertyRulePack(detectors);
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

    return new FilteredRulePack(
        delegate.getName(),
        detectors,
        Set.copyOf(delegate.getActivationAliases()),
        delegate.isAutoDiscoverable());
  }

  private record FilteredRulePack(
      @NonNull String name,
      @NonNull List<PiiDetector> detectors,
      @NonNull Set<String> activationAliases,
      boolean autoDiscoverable)
      implements PrismRulePack {

    @Override
    public @NonNull String getName() {
      return name;
    }

    @Override
    public @NonNull List<PiiDetector> getDetectors() {
      return detectors;
    }

    @Override
    public @NonNull Set<String> getActivationAliases() {
      return activationAliases;
    }

    @Override
    public boolean isAutoDiscoverable() {
      return autoDiscoverable;
    }
  }
}
